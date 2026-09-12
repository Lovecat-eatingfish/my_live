package org.qiyu.live.bank.provider.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.qiyu.live.bank.constants.OrderStatusEnum;
import org.qiyu.live.bank.constants.ReconciliationDiffTypeEnum;
import org.qiyu.live.bank.constants.TradeTypeEnum;
import org.qiyu.live.bank.dto.PayProductDTO;
import org.qiyu.live.bank.provider.dao.maper.IPayOrderMapper;
import org.qiyu.live.bank.provider.dao.maper.IQiyuCurrencyTradeMapper;
import org.qiyu.live.bank.provider.dao.maper.IReconciliationDetailMapper;
import org.qiyu.live.bank.provider.dao.po.PayOrderPO;
import org.qiyu.live.bank.provider.dao.po.QiyuCurrencyTradePO;
import org.qiyu.live.bank.provider.dao.po.ReconciliationDetailPO;
import org.qiyu.live.bank.provider.service.IPayProductService;
import org.qiyu.live.bank.provider.service.IReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 对账核心逻辑：
 * 订单侧：t_pay_order 中当日已支付（status=2）订单，按商品 extra.coin 折算应收金币
 * 流水侧：t_qiyu_currency_trade 中当日充值流水（type=LIVING_RECHARGE, status=1）
 * 流水表没有orderId关联字段，按 (userId, 金额) 多重集匹配：
 *  1. 同用户同金额的订单/流水两两抵消
 *  2. 同用户剩余的订单与流水按条配对，金额不等记为金额不平
 *  3. 仍未配对的订单/流水分别记为单边账
 */
@Service
public class ReconciliationServiceImpl implements IReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    @Resource
    private IPayOrderMapper payOrderMapper;
    @Resource
    private IQiyuCurrencyTradeMapper tradeMapper;
    @Resource
    private IReconciliationDetailMapper reconMapper;
    @Resource
    private IPayProductService payProductService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reconcileDay(String bizDate) {
        Date dayStart = parseDate(bizDate);
        Date dayEnd = Date.from(LocalDate.parse(bizDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<PayOrderPO> orders = listPayedOrders(dayStart, dayEnd);
        List<QiyuCurrencyTradePO> trades = listRechargeTrades(dayStart, dayEnd);

        // 每笔订单折算应收金币（金额以商品配置为准）
        Map<Integer, Integer> productCoinCache = new HashMap<>();
        List<OrderSide> orderSides = new ArrayList<>();
        for (PayOrderPO order : orders) {
            Integer coin = productCoinCache.computeIfAbsent(order.getProductId(),
                    this::queryProductCoin);
            OrderSide side = new OrderSide();
            side.orderId = order.getOrderId();
            side.userId = order.getUserId();
            side.productId = order.getProductId();
            side.num = coin == null ? 0 : coin;
            orderSides.add(side);
        }
        List<TradeSide> tradeSides = new ArrayList<>();
        for (QiyuCurrencyTradePO trade : trades) {
            TradeSide side = new TradeSide();
            side.userId = trade.getUserId();
            side.num = trade.getNum() == null ? 0 : trade.getNum();
            tradeSides.add(side);
        }

        List<ReconciliationDetailPO> diffs = match(orderSides, tradeSides);
        for (ReconciliationDetailPO diff : diffs) {
            diff.setBizDate(dayStart);
        }

        // 可重复执行：清空当日旧差错后重写
        LambdaQueryWrapper<ReconciliationDetailPO> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(ReconciliationDetailPO::getBizDate, dayStart);
        reconMapper.delete(removeWrapper);
        diffs.forEach(reconMapper::insert);

        LOGGER.info("[reconcileDay] bizDate={} orders={} trades={} diffs={}",
                bizDate, orderSides.size(), tradeSides.size(), diffs.size());
        return diffs.size();
    }

    private List<ReconciliationDetailPO> match(List<OrderSide> orders, List<TradeSide> trades) {
        List<ReconciliationDetailPO> diffs = new ArrayList<>();
        Map<Long, List<OrderSide>> orderByUser = groupByUser(orders);
        Map<Long, List<TradeSide>> tradeByUser = groupByUserTrades(trades);

        for (Map.Entry<Long, List<OrderSide>> entry : orderByUser.entrySet()) {
            Long userId = entry.getKey();
            List<OrderSide> remainOrders = new LinkedList<>(entry.getValue());
            List<TradeSide> remainTrades = new LinkedList<>(tradeByUser.getOrDefault(userId, new ArrayList<>()));

            // 1. 同金额订单/流水两两抵消
            for (OrderSide order : remainOrders) {
                TradeSide matched = null;
                for (TradeSide trade : remainTrades) {
                    if (trade.num.equals(order.num)) {
                        matched = trade;
                        break;
                    }
                }
                if (matched != null) {
                    remainTrades.remove(matched);
                    order.matched = true;
                }
            }
            remainOrders.removeIf(o -> o.matched);

            // 2. 剩余订单与流水按条配对 → 金额不平
            while (!remainOrders.isEmpty() && !remainTrades.isEmpty()) {
                OrderSide order = remainOrders.remove(0);
                TradeSide trade = remainTrades.remove(0);
                diffs.add(buildDiff(ReconciliationDiffTypeEnum.AMOUNT_MISMATCH,
                        order, trade.userId, trade.num));
            }
            // 3. 单边账
            for (OrderSide order : remainOrders) {
                diffs.add(buildDiff(ReconciliationDiffTypeEnum.ORDER_NO_TRADE, order, order.userId, 0));
            }
            for (TradeSide trade : remainTrades) {
                diffs.add(buildDiff(ReconciliationDiffTypeEnum.TRADE_NO_ORDER, null, trade.userId, trade.num));
            }
        }
        // 流水里存在、但当日订单侧完全没有该用户的场景
        for (Map.Entry<Long, List<TradeSide>> entry : tradeByUser.entrySet()) {
            if (!orderByUser.containsKey(entry.getKey())) {
                for (TradeSide trade : entry.getValue()) {
                    diffs.add(buildDiff(ReconciliationDiffTypeEnum.TRADE_NO_ORDER, null, trade.userId, trade.num));
                }
            }
        }
        return diffs;
    }

    private ReconciliationDetailPO buildDiff(ReconciliationDiffTypeEnum type, OrderSide order,
                                             Long userId, int actualNum) {
        ReconciliationDetailPO po = new ReconciliationDetailPO();
        po.setDiffType(type.getCode());
        po.setUserId(userId);
        po.setStatus(0);
        po.setRemark(type.getDesc());
        if (order != null) {
            po.setOrderId(order.orderId);
            po.setProductId(order.productId);
            po.setExpectNum(order.num);
        } else {
            po.setExpectNum(0);
        }
        po.setActualNum(actualNum);
        return po;
    }

    private List<PayOrderPO> listPayedOrders(Date dayStart, Date dayEnd) {
        LambdaQueryWrapper<PayOrderPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrderPO::getStatus, OrderStatusEnum.PAYED.getCode());
        // 历史订单 pay_time 可能未回填，兼容按 update_time 归日
        wrapper.and(w -> w
                .and(sub -> sub.ge(PayOrderPO::getPayTime, dayStart).lt(PayOrderPO::getPayTime, dayEnd))
                .or(sub -> sub.isNull(PayOrderPO::getPayTime)
                        .ge(PayOrderPO::getUpdateTime, dayStart).lt(PayOrderPO::getUpdateTime, dayEnd)));
        return payOrderMapper.selectList(wrapper);
    }

    private List<QiyuCurrencyTradePO> listRechargeTrades(Date dayStart, Date dayEnd) {
        LambdaQueryWrapper<QiyuCurrencyTradePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QiyuCurrencyTradePO::getType, TradeTypeEnum.LIVING_RECHARGE.getCode());
        wrapper.eq(QiyuCurrencyTradePO::getStatus, 1);
        wrapper.ge(QiyuCurrencyTradePO::getCreateTime, dayStart);
        wrapper.lt(QiyuCurrencyTradePO::getCreateTime, dayEnd);
        return tradeMapper.selectList(wrapper);
    }

    private Integer queryProductCoin(Integer productId) {
        PayProductDTO product = payProductService.getByProductId(productId);
        if (product == null || product.getExtra() == null) {
            return null;
        }
        JSONObject extra = JSON.parseObject(product.getExtra());
        return extra.getInteger("coin");
    }

    private Map<Long, List<OrderSide>> groupByUser(List<OrderSide> orders) {
        Map<Long, List<OrderSide>> map = new HashMap<>();
        for (OrderSide order : orders) {
            map.computeIfAbsent(order.userId, k -> new ArrayList<>()).add(order);
        }
        return map;
    }

    private Map<Long, List<TradeSide>> groupByUserTrades(List<TradeSide> trades) {
        Map<Long, List<TradeSide>> map = new HashMap<>();
        for (TradeSide trade : trades) {
            map.computeIfAbsent(trade.userId, k -> new ArrayList<>()).add(trade);
        }
        return map;
    }

    private Date parseDate(String bizDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(bizDate);
        } catch (ParseException e) {
            throw new IllegalArgumentException("bizDate must be yyyy-MM-dd: " + bizDate, e);
        }
    }

    @Override
    public List<ReconciliationDetailPO> listDetails(String bizDate, int page, int pageSize) {
        LambdaQueryWrapper<ReconciliationDetailPO> wrapper = new LambdaQueryWrapper<>();
        if (bizDate != null && !bizDate.isEmpty()) {
            wrapper.eq(ReconciliationDetailPO::getBizDate, parseDate(bizDate));
        }
        wrapper.orderByDesc(ReconciliationDetailPO::getBizDate);
        wrapper.orderByAsc(ReconciliationDetailPO::getDiffType);
        wrapper.last(String.format("limit %d,%d", Math.max(page - 1, 0) * pageSize, pageSize));
        return reconMapper.selectList(wrapper);
    }

    private static class OrderSide {
        String orderId;
        Long userId;
        Integer productId;
        Integer num;
        boolean matched;
    }

    private static class TradeSide {
        Long userId;
        Integer num;
    }
}
