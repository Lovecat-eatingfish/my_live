package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.IGiftService;
import org.qiyu.live.api.vo.req.GiftReqVO;
import org.qiyu.live.api.vo.ShopSkuVO;
import org.qiyu.live.api.vo.req.RedPacketReqVO;
import org.qiyu.live.api.vo.req.SkuOrderReqVO;
import org.qiyu.live.api.vo.resp.GiftConfigVO;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 礼物、红包雨、商品订单控制器
 */
@RestController
@RequestMapping("/gift")
public class GiftController {

    @Resource
    private IGiftService giftService;

    /**
     * 获取礼物列表
     */
    @PostMapping("/listGift")
    public WebResponseVO listGift() {
        List<GiftConfigVO> giftConfigVOS = giftService.listGift();
        return WebResponseVO.success(giftConfigVOS);
    }

    /**
     * 发送礼物
     */
    @PostMapping("/send")
    public WebResponseVO send(@RequestBody GiftReqVO giftReqVO) {
        return WebResponseVO.success(giftService.send(giftReqVO));
    }

    // ==================== 红包雨接口 ====================

    /**
     * 主播创建红包雨
     */
    @PostMapping("/redpacket/create")
    public WebResponseVO createRedPacket(@RequestBody RedPacketReqVO reqVO) {
        return WebResponseVO.success(giftService.createRedPacket(reqVO));
    }

    /**
     * 主播预热红包雨（前端倒计时开始）
     */
    @PostMapping("/redpacket/prepare")
    public WebResponseVO prepareRedPacket(@RequestBody RedPacketReqVO reqVO) {
        giftService.prepareRedPacket(reqVO);
        return WebResponseVO.success();
    }

    /**
     * 主播发送红包雨
     */
    @PostMapping("/redpacket/send")
    public WebResponseVO sendRedPacket(@RequestBody RedPacketReqVO reqVO) {
        giftService.sendRedPacket(reqVO);
        return WebResponseVO.success();
    }

    /**
     * 用户领取红包雨
     */
    @PostMapping("/redpacket/receive")
    public WebResponseVO receiveRedPacket(@RequestBody RedPacketReqVO reqVO) {
        return WebResponseVO.success(giftService.receiveRedPacket(reqVO));
    }

    /**
     * 查询红包雨状态
     */
    @PostMapping("/redpacket/query")
    public WebResponseVO queryRedPacket(@RequestBody RedPacketReqVO reqVO) {
        return WebResponseVO.success(giftService.queryRedPacket(reqVO));
    }

    // ==================== 商品订单接口 ====================

    /**
     * 创建商品订单
     */
    @PostMapping("/order/create")
    public WebResponseVO createOrder(@RequestBody SkuOrderReqVO reqVO) {
        return WebResponseVO.success(giftService.createSkuOrder(reqVO));
    }

    /**
     * 支付成功回调
     */
    @PostMapping("/order/pay")
    public WebResponseVO payOrder(@RequestBody SkuOrderReqVO reqVO) {
        return WebResponseVO.success(giftService.paySuccessSkuOrder(reqVO));
    }

    /**
     * 查询订单详情
     */
    @PostMapping("/order/detail")
    public WebResponseVO getOrderDetail(@RequestBody SkuOrderReqVO reqVO) {
        return WebResponseVO.success(giftService.getSkuOrderDetail(reqVO));
    }

    /**
     * 查询用户订单列表
     */
    @PostMapping("/order/list")
    public WebResponseVO listUserOrders() {
        return WebResponseVO.success(giftService.listUserSkuOrders());
    }

    /**
     * 查询直播间带货商品列表（小黄车）
     */
    @PostMapping("/shop/list")
    public WebResponseVO shopList(Integer roomId) {
        return WebResponseVO.success(giftService.listShopByRoom(roomId));
    }

    /**
     * 全部在架商品（主播商品管理列表）
     */
    @PostMapping("/sku/list")
    public WebResponseVO skuList() {
        return WebResponseVO.success(giftService.listAllSkus());
    }

    /**
     * 我（主播）已上架的商品
     */
    @PostMapping("/shop/myList")
    public WebResponseVO myShopList() {
        return WebResponseVO.success(giftService.listMyShop());
    }

    /**
     * 主播上架/下架商品（status: 1上架 0下架）
     */
    @PostMapping("/shop/updateStatus")
    public WebResponseVO updateShopStatus(Integer skuId, Integer status) {
        return WebResponseVO.success(giftService.updateShopStatus(skuId, status));
    }
}
