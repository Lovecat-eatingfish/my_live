package org.qiyu.live.api.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IBankService;
import org.qiyu.live.api.vo.req.PayProductReqVO;
import org.qiyu.live.api.vo.resp.PayProductItemVO;
import org.qiyu.live.api.vo.resp.PayProductRespVO;
import org.qiyu.live.api.vo.resp.PayProductVO;
import org.qiyu.live.bank.constants.OrderStatusEnum;
import org.qiyu.live.bank.dto.PayOrderDTO;
import org.qiyu.live.bank.dto.PayProductDTO;
import org.qiyu.live.bank.interfaces.IPayOrderRpc;
import org.qiyu.live.bank.interfaces.IPayProductRpc;
import org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc;
import org.qiyu.live.bank.constants.PaySourceEnum;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @Author idea
 * @Date: Created in 08:27 2023/8/17
 * @Description
 */
@Service
public class BankServiceImpl implements IBankService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BankServiceImpl.class);

    /**
     * 模拟第三方支付成功后的回调地址（bank-api 的 wxNotify 入口）。
     * 真实支付场景由支付宝/微信服务器回调该地址；本项目不对接真实渠道，发起支付后由服务端直接模拟回调。
     */
    @org.springframework.beans.factory.annotation.Value("${qiyu.pay.mock-notify-url:http://localhost:38201/live/bank/payNotify/wxNotify}")
    private String mockNotifyUrl;

    @DubboReference(check = false)
    private IPayProductRpc payProductRpc;
    @DubboReference(check = false)
    private IQiyuCurrencyAccountRpc qiyuCurrencyAccountRpc;
    @DubboReference(check = false)
    private IPayOrderRpc payOrderRpc;
    @Resource
    private RestTemplate restTemplate;

    @Override
    public PayProductVO products(Integer type) {
        List<PayProductDTO> payProductDTOS = payProductRpc.products(type);
        PayProductVO payProductVO = new PayProductVO();
        List<PayProductItemVO> itemList = new ArrayList<>();
        for (PayProductDTO payProductDTO : payProductDTOS) {
            PayProductItemVO itemVO = new PayProductItemVO();
            itemVO.setName(payProductDTO.getName());
            itemVO.setId(payProductDTO.getId());
            itemVO.setPrice(payProductDTO.getPrice());
            itemVO.setCoinNum(JSON.parseObject(payProductDTO.getExtra()).getInteger("coin"));
            itemList.add(itemVO);
        }
        payProductVO.setPayProductItemVOList(itemList);
        payProductVO.setCurrentBalance(Optional.ofNullable(qiyuCurrencyAccountRpc.getBalance(QiyuRequestContext.getUserId())).orElse(0));
        return payProductVO;
    }

    @Override
    public Integer getBalance() {
        Long userId = QiyuRequestContext.getUserId();
        return Optional.ofNullable(qiyuCurrencyAccountRpc.getBalance(userId)).orElse(0);
    }

    @Override
    public PayProductRespVO payProduct(PayProductReqVO payProductReqVO) {
        //参数校验
        ErrorAssert.isTure(payProductReqVO != null && payProductReqVO.getProductId() != null && payProductReqVO.getPaySource() != null, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isNotNull(PaySourceEnum.find(payProductReqVO.getPaySource()), BizBaseErrorEnum.PARAM_ERROR);
        PayProductDTO payProductDTO = payProductRpc.getByProductId(payProductReqVO.getProductId());
        ErrorAssert.isNotNull(payProductDTO, BizBaseErrorEnum.PARAM_ERROR);

        //插入一条订单，待支付状态
        PayOrderDTO payOrderDTO = new PayOrderDTO();
        payOrderDTO.setProductId(payProductReqVO.getProductId());
        payOrderDTO.setUserId(QiyuRequestContext.getUserId());
        payOrderDTO.setSource(payProductReqVO.getPaySource());
        payOrderDTO.setPayChannel(payProductReqVO.getPayChannel());
        String orderId = payOrderRpc.insertOne(payOrderDTO);

        //更新订单为支付中状态
        payOrderRpc.updateOrderStatus(orderId, OrderStatusEnum.PAYING.getCode());
        PayProductRespVO payProductRespVO = new PayProductRespVO();
        payProductRespVO.setOrderId(orderId);

        //模拟第三方支付成功回调（本项目不对接真实支付渠道，服务端直接通知 bank-api 入账）
        //注意：不能用 {param} URI 模板占位——JSON 值中的花括号会干扰 RestTemplate 模板解析导致参数丢失，必须手动编码
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderId", orderId);
        jsonObject.put("userId", QiyuRequestContext.getUserId());
        jsonObject.put("bizCode", 10001);
        try {
            //用 URI 对象传参，避免 RestTemplate 对已编码的 % 二次编码导致 bank-api 收到原始转义串
            String notifyUrl = mockNotifyUrl + "?param=" + URLEncoder.encode(jsonObject.toJSONString(), StandardCharsets.UTF_8);
            ResponseEntity<String> resultEntity = restTemplate.postForEntity(java.net.URI.create(notifyUrl), null, String.class);
            LOGGER.info("[payProduct] mock pay notify result, orderId={}, body={}", orderId, resultEntity.getBody());
        } catch (Exception e) {
            LOGGER.error("[payProduct] mock pay notify failed, orderId={}, url={}", orderId, mockNotifyUrl, e);
        }
        return payProductRespVO;
    }
}
