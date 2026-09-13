package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.LivingRoomInitVO;
import org.qiyu.live.api.vo.req.LivingRoomReqVO;
import org.qiyu.live.api.vo.req.OnlinePkReqVO;
import org.qiyu.live.api.vo.resp.LivingRoomPageRespVO;

/**
 * @Author idea
 * @Date: Created in 21:15 2023/7/19
 * @Description
 */
public interface ILivingRoomService {

    /**
     * 直播间列表展示
     *
     * @param livingRoomReqVO
     * @return
     */
    LivingRoomPageRespVO list(LivingRoomReqVO livingRoomReqVO);

    /** 全部启用中的直播分区（动态 tab） */
    java.util.List<org.qiyu.live.living.interfaces.dto.LivingCategoryDTO> categories();

    /** 关注 tab：关注的主播中正在开播的房间 */
    LivingRoomPageRespVO followRooms(int page, int pageSize);

    /** 口令抽奖：主播发起，返回错误信息，null=成功 */
    String createLottery(Integer roomId, String keyword, int durationSec, int winnerCount, int rewardCoins);

    /** 主播设置直播间公告，返回错误信息，null=成功 */
    String setAnnouncement(Integer roomId, String announcement);

    /** 主播任命房间管理员，返回错误信息，null=成功 */
    String appointRoomAdmin(Integer roomId, Long adminUserId);

    /** 主播移除房间管理员 */
    boolean removeRoomAdmin(Integer roomId, Long adminUserId);

    /** 主播或管理员禁言观众（房间维度），返回错误信息，null=成功 */
    String muteRoomUser(Integer roomId, Long muteUserId, Integer minutes);

    /** 解除房间禁言 */
    boolean unmuteRoomUser(Integer roomId, Long muteUserId);

    /** 主播发起投票，返回错误信息，null=成功 */
    String createVote(Integer roomId, String title, java.util.List<String> options, int durationSec);

    /** 观众投票（一人一票），返回错误信息，null=成功 */
    String castVote(Integer roomId, int optionIndex);

    /** 当前进行中的投票 JSON（无则 null） */
    String currentVote(Integer roomId);

    /**
     * 开启直播间
     *
     * @param type
     */
    /**
     * 开播（支持主播自定义直播间名称与封面）
     *
     * @param type      直播间类型
     * @param roomName  直播间名称，空则用默认名
     * @param covertImg 封面图 URL，空则用用户头像
     */
    Integer startingLiving(Integer type, String roomName, String covertImg, Integer payType, Integer ticketPrice, Integer recordEnabled);


    /**
     * 用户在pk直播间中，连上线请求
     *
     * @param onlinePkReqVO
     * @return
     */
    boolean onlinePk(OnlinePkReqVO onlinePkReqVO);

    /**
     * 关闭直播间
     *
     * @param roomId
     * @return
     */
    boolean closeLiving(Integer roomId);

    /**
     * 查询直播间在线观众数（Redis 房间用户 set 计数）
     *
     * @param roomId
     * @return
     */
    Integer onlineCount(Integer roomId);

    /**
     * 查询我（当前用户）进行中的直播间，返回 roomId，无则返回 null
     */
    Integer myLivingRoom();

    /**
     * 根据用户id返回当前直播间相关信息
     *
     * @param userId
     * @param roomId
     * @return
     */
    LivingRoomInitVO anchorConfig(Long userId,Integer roomId);


    // ==================== 连麦（5572 信令） ====================

    /** PK 观众点赞加分（PK 中有效，每观众每日 50） */
    Boolean pkLike(Integer roomId);

    /** 购买直播间门票（金币直扣） */
    Boolean buyTicket(Integer roomId);

    /** 主播邀请观众连麦 */
    Long inviteLinkMic(Integer roomId, Long guestUserId);

    /** 观众接受连麦 */
    Boolean acceptLinkMic(Long linkMicId);

    /** 挂断连麦 */
    Boolean hangUpLinkMic(Integer roomId);
}
