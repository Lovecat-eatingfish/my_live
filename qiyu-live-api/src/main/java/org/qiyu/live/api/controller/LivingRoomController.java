package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.ILivingRoomService;
import org.qiyu.live.api.vo.LivingRoomInitVO;
import org.qiyu.live.api.vo.req.LivingRoomReqVO;
import org.qiyu.live.api.vo.req.OnlinePkReqVO;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.config.RequestLimit;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.qiyu.live.web.starter.error.QiyuBaseError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author idea
 * @Date: Created in 21:14 2023/7/19
 * @Description
 */
@RestController
@RequestMapping("/living")
public class LivingRoomController {

    @Resource
    private ILivingRoomService livingRoomService;

    @PostMapping("/list")
    public WebResponseVO list(LivingRoomReqVO livingRoomReqVO) {
        ErrorAssert.isTure(livingRoomReqVO != null && livingRoomReqVO.getType() != null, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(livingRoomReqVO.getPage() > 0 && livingRoomReqVO.getPageSize() <= 100, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(livingRoomService.list(livingRoomReqVO));
    }

    @RequestLimit(limit = 1, second = 10, msg = "开播请求过于频繁，请稍后再试")
    @PostMapping("/startingLiving")
    public WebResponseVO startingLiving(Integer type, String roomName, String covertImg,
                                            Integer payType, Integer ticketPrice, Integer recordEnabled) {
        ErrorAssert.isNotNull(type, BizBaseErrorEnum.PARAM_ERROR);
        Integer roomId = livingRoomService.startingLiving(type, roomName, covertImg, payType, ticketPrice, recordEnabled);
        LivingRoomInitVO initVO = new LivingRoomInitVO();
        initVO.setRoomId(roomId);
        return WebResponseVO.success(initVO);
    }

    /** 直播分区列表（首页动态 tab） */
    @PostMapping("/categories")
    public WebResponseVO categories() {
        return WebResponseVO.success(livingRoomService.categories());
    }

    /** 关注 tab：关注的主播中正在开播的房间 */
    @PostMapping("/followRooms")
    public WebResponseVO followRooms() {
        return WebResponseVO.success(livingRoomService.followRooms(1, 20));
    }

    /** 口令抽奖：主播发起 */
    @PostMapping("/lottery/create")
    public WebResponseVO lotteryCreate(Integer roomId, String keyword, Integer durationSec,
                                       Integer winnerCount, Integer rewardCoins) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(keyword != null && !keyword.trim().isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(livingRoomService.createLottery(roomId, keyword, durationSec == null ? 60 : durationSec,
                winnerCount == null ? 1 : winnerCount, rewardCoins == null ? 0 : rewardCoins));
    }

    @PostMapping("/onlinePk")
    @RequestLimit(limit = 1,second = 3)
    public WebResponseVO onlinePk(OnlinePkReqVO onlinePkReqVO) {
        ErrorAssert.isNotNull(onlinePkReqVO.getRoomId(), BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(livingRoomService.onlinePk(onlinePkReqVO));
    }

    @RequestLimit(limit = 1, second = 10, msg = "关播请求过于频繁，请稍后再试")
    @PostMapping("/closeLiving")
    public WebResponseVO closeLiving(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        boolean closeStatus = livingRoomService.closeLiving(roomId);
        if (closeStatus) {
            return WebResponseVO.success();
        }
        return WebResponseVO.bizError("关播异常");
    }

    /**
     * 查询我进行中的直播间（主播刷新浏览器后回到直播间用）
     */
    @PostMapping("/myLivingRoom")
    public WebResponseVO myLivingRoom() {
        return WebResponseVO.success(livingRoomService.myLivingRoom());
    }

    /**
     * 查询直播间在线观众数
     */
    @PostMapping("/onlineCount")
    public WebResponseVO onlineCount(Integer roomId) {
        return WebResponseVO.success(livingRoomService.onlineCount(roomId));
    }

    /**
     * 获取主播相关配置信息（只有主播才会有权限）
     *
     * @return
     */
    @PostMapping("/anchorConfig")
    public WebResponseVO anchorConfig(Integer roomId) {
        return WebResponseVO.success(livingRoomService.anchorConfig(QiyuRequestContext.getUserId(), roomId));
    }




    // ==================== PK 竞技化 ====================

    /** 观众点赞为主播方加分（PK 中有效，每观众每日 50） */
    @PostMapping("/pk/like")
    public WebResponseVO pkLike(Integer roomId) {
        return WebResponseVO.success(livingRoomService.pkLike(roomId));
    }

    // ==================== 付费直播间门票 ====================

    /** 购买门票（金币直扣，幂等） */
    @PostMapping("/ticket/buy")
    public WebResponseVO buyTicket(Integer roomId) {
        return WebResponseVO.success(livingRoomService.buyTicket(roomId));
    }

    // ==================== 连麦（5572 信令） ====================

    /** 主播邀请观众连麦 */
    @PostMapping("/linkMic/invite")
    public WebResponseVO inviteLinkMic(Integer roomId, Long guestUserId) {
        return WebResponseVO.success(livingRoomService.inviteLinkMic(roomId, guestUserId));
    }

    /** 观众接受连麦 */
    @PostMapping("/linkMic/accept")
    public WebResponseVO acceptLinkMic(Long linkMicId) {
        return WebResponseVO.success(livingRoomService.acceptLinkMic(linkMicId));
    }

    /** 挂断连麦 */
    @PostMapping("/linkMic/hangUp")
    public WebResponseVO hangUpLinkMic(Integer roomId) {
        return WebResponseVO.success(livingRoomService.hangUpLinkMic(roomId));
    }
}
