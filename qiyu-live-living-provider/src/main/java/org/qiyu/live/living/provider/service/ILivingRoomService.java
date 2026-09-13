package org.qiyu.live.living.provider.service;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.im.core.server.interfaces.dto.ImOfflineDTO;
import org.qiyu.live.im.core.server.interfaces.dto.ImOnlineDTO;
import org.qiyu.live.living.interfaces.dto.LivingPkRespDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;

import java.util.List;

/**
 * @Author idea
 * @Date: Created in 21:24 2023/7/19
 * @Description
 */
public interface ILivingRoomService {

    /**
     * 支持根据roomId查询出批量的userId（set）存储，3000个人，元素非常多，O(n)
     *
     * @param livingRoomReqDTO
     * @return
     */
    List<Long> queryUserIdByRoomId(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 用户下线处理
     *
     * @param imOfflineDTO
     */
    void userOfflineHandler(ImOfflineDTO imOfflineDTO);

    /**
     * 主播断线后的延迟关播检查：主播已回房或推流存活则放行，否则关播
     */
    void closeLivingCheck(ImOfflineDTO imOfflineDTO);

    /**
     * 查询主播当前进行中的直播间（无则返回null）
     */
    LivingRoomRespDTO queryByAnchorId(Long anchorId);

    /**
     * 用户上线处理
     *
     * @param imOnlineDTO
     */
    void userOnlineHandler(ImOnlineDTO imOnlineDTO);

     /**
     * 查询所有的直播间类型
     *
     * @param type
     * @return
     */
    List<LivingRoomRespDTO> listAllLivingRoomFromDB(Integer type);

    /**
     * 直播间列表的分页查询
     *
     * @param livingRoomReqDTO
     * @return
     */
    PageWrapper<LivingRoomRespDTO> list(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 按直播间名称模糊搜索（只搜开播中的房间）
     */
    PageWrapper<LivingRoomRespDTO> searchRooms(String keyword, int page, int pageSize);

    /**
     * 根据roomId查询直播间
     *
     * @param roomId
     * @return
     */
    LivingRoomRespDTO queryByRoomId(Integer roomId);

    /**
     * 开启直播间
     *
     * @param livingRoomReqDTO
     * @return
     */
    Integer startLivingRoom(LivingRoomReqDTO livingRoomReqDTO);


    /**
     * 根据roomId查询当前pk人是谁
     *
     * @param roomId
     * @return
     */
    Long queryOnlinePkUserId(Integer roomId);

    /**
     * 用户在pk直播间中，连上线请求
     *
     * @param livingRoomReqDTO
     * @return
     */
    LivingPkRespDTO onlinePk(LivingRoomReqDTO livingRoomReqDTO);


    /**
     * 用户在pk直播间中，下线请求
     *
     * @param livingRoomReqDTO
     * @return
     */
    boolean offlinePk(LivingRoomReqDTO livingRoomReqDTO);

    /** PK 观众点赞加分（+1，广播进度） */
    Boolean pkLike(Integer roomId, Long userId);

    /**
     * 关注 tab：按主播 id 集合查开播中的房间（startTime 倒序）
     */
    PageWrapper<LivingRoomRespDTO> listByAnchorIds(java.util.List<Long> anchorIds, int page, int pageSize);

    /**
     * 口令抽奖：主播发起（扣奖励金币 + 广播 5574 + 延迟 MQ 结算）
     * 返回错误信息，null=成功
     */
    String createLottery(Integer roomId, Long userId, String keyword, int durationSec, int winnerCount, int rewardCoins);

    /** 主播设置直播间公告（空串=清除），返回错误信息，null=成功 */
    String setAnnouncement(Integer roomId, Long userId, String announcement);

    /** 主播任命房间管理员，返回错误信息，null=成功 */
    String appointRoomAdmin(Integer roomId, Long anchorId, Long adminUserId);

    /** 主播移除房间管理员 */
    boolean removeRoomAdmin(Integer roomId, Long anchorId, Long adminUserId);

    /** 是否房间管理员 */
    boolean isRoomAdmin(Integer roomId, Long userId);

    /** 房间管理员列表 */
    java.util.List<Long> listRoomAdmins(Integer roomId);

    /** 主播或管理员禁言观众（房间维度，默认 30 分钟），返回错误信息，null=成功 */
    String muteRoomUser(Integer roomId, Long operatorId, Long muteUserId, int minutes);

    /** 解除房间禁言 */
    boolean unmuteRoomUser(Integer roomId, Long operatorId, Long muteUserId);

    /** 主播发起投票（options 为选项数组 JSON），返回错误信息，null=成功 */
    String createVote(Integer roomId, Long userId, String title, java.util.List<String> options, int durationSec);

    /** 观众投票（一人一票），返回错误信息，null=成功 */
    String castVote(Integer roomId, Long userId, int optionIndex);

    /** 当前进行中的投票（无则 null） */
    String currentVote(Integer roomId);
}
