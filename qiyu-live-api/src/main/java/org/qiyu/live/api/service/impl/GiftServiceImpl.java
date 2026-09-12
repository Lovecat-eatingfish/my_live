package org.qiyu.live.api.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.qiyu.live.api.error.ApiErrorEnum;
import org.qiyu.live.api.service.IGiftService;
import org.qiyu.live.api.vo.req.GiftReqVO;
import org.qiyu.live.api.vo.req.RedPacketReqVO;
import org.qiyu.live.api.vo.ShopSkuVO;
import org.qiyu.live.api.vo.req.SkuOrderReqVO;
import org.qiyu.live.api.vo.resp.GiftConfigVO;
import org.qiyu.live.api.vo.resp.RedPacketRespVO;
import org.qiyu.live.api.vo.resp.SkuOrderRespVO;
import org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc;
import org.qiyu.live.common.interfaces.dto.SendGiftMq;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.AnchorShopInfoDTO;
import org.qiyu.live.gift.dto.GiftConfigDTO;
import org.qiyu.live.gift.dto.RedPacketConfigDTO;
import org.qiyu.live.gift.dto.SkuInfoDTO;
import org.qiyu.live.gift.dto.SkuOrderInfoDTO;
import org.qiyu.live.gift.interfaces.IAnchorShopRpc;
import org.qiyu.live.gift.interfaces.IGiftConfigRpc;
import org.qiyu.live.gift.interfaces.IRedPacketRpc;
import org.qiyu.live.gift.interfaces.ISkuOrderRpc;
import org.qiyu.live.gift.interfaces.ISkuRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 礼物、红包雨、商品订单服务实现
 */
@Service
public class GiftServiceImpl implements IGiftService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GiftServiceImpl.class);

    @DubboReference(check = false)
    private IGiftConfigRpc giftConfigRpc;
    @DubboReference(check = false)
    private IQiyuCurrencyAccountRpc qiyuCurrencyAccountRpc;
    @DubboReference(check = false)
    private IRedPacketRpc redPacketRpc;
    @DubboReference(check = false)
    private ISkuOrderRpc skuOrderRpc;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    @DubboReference(check = false)
    private IAnchorShopRpc anchorShopRpc;
    @DubboReference(check = false)
    private ISkuRpc skuRpc;
    @Resource
    private MQProducer mqProducer;

    private Cache<Integer, GiftConfigDTO> giftConfigDTOCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .build();

    @Override
    public List<GiftConfigVO> listGift() {
        List<GiftConfigDTO> giftConfigDTOS = giftConfigRpc.queryGiftList();
        return ConvertBeanUtils.convertList(giftConfigDTOS, GiftConfigVO.class);
    }

    @Override
    public boolean send(GiftReqVO giftReqVO) {
        int giftId = giftReqVO.getGiftId();
        GiftConfigDTO giftConfigDTO = giftConfigDTOCache.get(giftId, id -> giftConfigRpc.getByGiftId(giftId));
        ErrorAssert.isNotNull(giftConfigDTO, ApiErrorEnum.GIFT_CONFIG_ERROR);
        ErrorAssert.isTure(!giftReqVO.getReceiverId().equals(giftReqVO.getSenderUserId()), ApiErrorEnum.NOT_SEND_TO_YOURSELF);

        SendGiftMq sendGiftMq = new SendGiftMq();
        sendGiftMq.setUserId(QiyuRequestContext.getUserId());
        sendGiftMq.setGiftId(giftId);
        sendGiftMq.setRoomId(giftReqVO.getRoomId());
        sendGiftMq.setReceiverId(giftReqVO.getReceiverId());
        sendGiftMq.setUrl(giftConfigDTO.getSvgaUrl());
        sendGiftMq.setType(giftReqVO.getType());
        sendGiftMq.setPrice(giftConfigDTO.getPrice());
        sendGiftMq.setUuid(UUID.randomUUID().toString());
        Message message = new Message();
        message.setTopic(GiftProviderTopicNames.SEND_GIFT);
        message.setBody(JSON.toJSONBytes(sendGiftMq));
        try {
            SendResult sendResult = mqProducer.send(message);
            LOGGER.info("[gift-send] send result is {}", sendResult);
        } catch (Exception e) {
            LOGGER.info("[gift-send] send result is error:", e);
        }
        return true;
    }

    // ==================== 红包雨接口实现 ====================

    @Override
    public RedPacketRespVO createRedPacket(RedPacketReqVO reqVO) {
        RedPacketConfigDTO dto = new RedPacketConfigDTO();
        dto.setAnchorId(QiyuRequestContext.getUserId());
        dto.setRoomId(reqVO.getRoomId());
        dto.setTotalPrice(reqVO.getTotalPrice());
        dto.setTotalCount(reqVO.getTotalCount());
        dto.setMaxGetPrice(reqVO.getMaxGetPrice());
        redPacketRpc.create(dto);
        // 创建后回查，拿到 redPacketId 和 configCode
        return convertRedPacketResp(redPacketRpc.getByAnchorId(QiyuRequestContext.getUserId()));
    }

    @Override
    public List<ShopSkuVO> listShopByRoom(Integer roomId) {
        LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(roomId);
        if (room == null || room.getAnchorId() == null) {
            return new ArrayList<>();
        }
        List<AnchorShopInfoDTO> shopList = anchorShopRpc.listByAnchorId(room.getAnchorId());
        if (shopList == null || shopList.isEmpty()) {
            return new ArrayList<>();
        }
        List<ShopSkuVO> voList = new ArrayList<>();
        for (AnchorShopInfoDTO shop : shopList) {
            if (shop.getSkuId() == null) {
                continue;
            }
            SkuInfoDTO sku = skuRpc.getBySkuId(shop.getSkuId());
            if (sku == null) {
                continue;
            }
            ShopSkuVO vo = new ShopSkuVO();
            vo.setSkuId(sku.getSkuId());
            vo.setName(sku.getName());
            vo.setIconUrl(sku.getIconUrl());
            vo.setSkuPrice(sku.getSkuPrice());
            vo.setRemark(sku.getRemark());
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public void prepareRedPacket(RedPacketReqVO reqVO) {
        ErrorAssert.isNotNull(reqVO.getRedPacketId(), ApiErrorEnum.PARAM_ERROR);
        redPacketRpc.prepare(reqVO.getRedPacketId());
    }

    @Override
    public void sendRedPacket(RedPacketReqVO reqVO) {
        ErrorAssert.isNotNull(reqVO.getRedPacketId(), ApiErrorEnum.PARAM_ERROR);
        redPacketRpc.send(reqVO.getRedPacketId());
    }

    @Override
    public Integer receiveRedPacket(RedPacketReqVO reqVO) {
        ErrorAssert.isNotNull(reqVO.getRedPacketId(), ApiErrorEnum.PARAM_ERROR);
        Long userId = QiyuRequestContext.getUserId();
        return redPacketRpc.receive(reqVO.getRedPacketId(), userId, reqVO.getRoomId());
    }

    @Override
    public RedPacketRespVO queryRedPacket(RedPacketReqVO reqVO) {
        RedPacketConfigDTO dto;
        if (reqVO.getRedPacketId() != null) {
            // 按ID查询（主播端）
            dto = redPacketRpc.getByAnchorId(QiyuRequestContext.getUserId());
        } else if (reqVO.getConfigCode() != null) {
            // 按配置码查询（用户端）
            dto = redPacketRpc.getByConfigCode(reqVO.getConfigCode());
        } else {
            return null;
        }
        return convertRedPacketResp(dto);
    }

    // ==================== 商品订单接口实现 ====================

    @Override
    public SkuOrderRespVO createSkuOrder(SkuOrderReqVO reqVO) {
        SkuOrderInfoDTO orderDTO = new SkuOrderInfoDTO();
        orderDTO.setUserId(QiyuRequestContext.getUserId());
        orderDTO.setRoomId(reqVO.getRoomId());
        orderDTO.setSkuIdList(reqVO.getSkuIdList());
        SkuOrderInfoDTO result = skuOrderRpc.createOrder(orderDTO);
        return convertSkuOrderResp(result);
    }

    @Override
    public boolean paySuccessSkuOrder(SkuOrderReqVO reqVO) {
        ErrorAssert.isNotNull(reqVO.getOrderId(), ApiErrorEnum.PARAM_ERROR);
        return skuOrderRpc.paySuccess(reqVO.getOrderId());
    }

    @Override
    public SkuOrderRespVO getSkuOrderDetail(SkuOrderReqVO reqVO) {
        ErrorAssert.isNotNull(reqVO.getOrderId(), ApiErrorEnum.PARAM_ERROR);
        SkuOrderInfoDTO dto = skuOrderRpc.getById(reqVO.getOrderId());
        return convertSkuOrderResp(dto);
    }

    @Override
    public List<SkuOrderRespVO> listUserSkuOrders() {
        List<SkuOrderInfoDTO> dtoList = skuOrderRpc.listByUserId(QiyuRequestContext.getUserId());
        return ConvertBeanUtils.convertList(dtoList, SkuOrderRespVO.class);
    }

    // ==================== 转换方法 ====================

    private RedPacketRespVO convertRedPacketResp(RedPacketConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        RedPacketRespVO vo = new RedPacketRespVO();
        vo.setRedPacketId(dto.getId());
        vo.setAnchorId(dto.getAnchorId());
        vo.setRoomId(dto.getRoomId());
        vo.setTotalPrice(dto.getTotalPrice());
        vo.setTotalCount(dto.getTotalCount());
        vo.setMaxGetPrice(dto.getMaxGetPrice());
        vo.setStatus(dto.getStatus());
        vo.setConfigCode(dto.getConfigCode());
        return vo;
    }

    private SkuOrderRespVO convertSkuOrderResp(SkuOrderInfoDTO dto) {
        if (dto == null) {
            return null;
        }
        SkuOrderRespVO vo = new SkuOrderRespVO();
        vo.setOrderId(dto.getId());
        vo.setSkuIdList(dto.getSkuIdList());
        vo.setUserId(dto.getUserId());
        vo.setRoomId(dto.getRoomId());
        vo.setTotalPrice(dto.getTotalPrice());
        vo.setStatus(dto.getStatus());
        vo.setStatusDesc(getStatusDesc(dto.getStatus()));
        vo.setCreateTime(dto.getCreateTime());
        return vo;
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待支付";
            case 1: return "已支付";
            case 2: return "已取消";
            default: return "未知";
        }
    }
}
