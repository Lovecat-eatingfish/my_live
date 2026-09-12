package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.ShopSkuVO;
import org.qiyu.live.api.vo.req.GiftReqVO;
import org.qiyu.live.api.vo.req.RedPacketReqVO;
import org.qiyu.live.api.vo.req.SkuOrderReqVO;
import org.qiyu.live.api.vo.resp.GiftConfigVO;
import org.qiyu.live.api.vo.resp.RedPacketRespVO;
import org.qiyu.live.api.vo.resp.SkuOrderRespVO;

import java.util.List;

/**
 * 礼物、红包雨、商品订单服务接口
 */
public interface IGiftService {

    /**
     * 展示礼物列表
     */
    List<GiftConfigVO> listGift();

    /**
     * 送礼
     */
    boolean send(GiftReqVO giftReqVO);

    // ==================== 红包雨接口 ====================

    /**
     * 主播创建红包雨
     */
    RedPacketRespVO createRedPacket(RedPacketReqVO reqVO);

    /**
     * 主播预热红包雨
     */
    void prepareRedPacket(RedPacketReqVO reqVO);

    /**
     * 主播发送红包雨
     */
    void sendRedPacket(RedPacketReqVO reqVO);

    /**
     * 用户领取红包雨
     * @return 领取金额，0表示领取失败
     */
    Integer receiveRedPacket(RedPacketReqVO reqVO);

    /**
     * 查询红包雨状态
     */
    RedPacketRespVO queryRedPacket(RedPacketReqVO reqVO);

    // ==================== 商品订单接口 ====================

    /**
     * 创建商品订单
     */
    SkuOrderRespVO createSkuOrder(SkuOrderReqVO reqVO);

    /**
     * 支付成功回调
     */
    boolean paySuccessSkuOrder(SkuOrderReqVO reqVO);

    /**
     * 查询订单详情
     */
    SkuOrderRespVO getSkuOrderDetail(SkuOrderReqVO reqVO);

    /**
     * 查询用户订单列表
     */
    List<SkuOrderRespVO> listUserSkuOrders();

    /**
     * 查询直播间的带货商品列表（小黄车）
     */
    List<ShopSkuVO> listShopByRoom(Integer roomId);

    /**
     * 全部在架商品（主播商品管理列表）
     */
    List<ShopSkuVO> listAllSkus();

    /**
     * 我（主播）已上架的商品
     */
    List<ShopSkuVO> listMyShop();

    /**
     * 主播上架/下架商品（status: 1上架 0下架）
     */
    boolean updateShopStatus(Integer skuId, Integer status);
}
