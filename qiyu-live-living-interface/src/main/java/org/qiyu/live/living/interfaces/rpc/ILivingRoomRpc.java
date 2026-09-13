package org.qiyu.live.living.interfaces.rpc;


import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.living.interfaces.dto.LivingPkRespDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;

import java.util.List;

/**
 * @Author idea
 * @Date: Created in 21:20 2023/7/19
 * @Description
 */
public interface ILivingRoomRpc {


    /**
     * 支持根据roomId查询出批量的userId（set）存储，3000个人，元素非常多，O(n)
     *
     * @param livingRoomReqDTO
     * @return
     */
    List<Long> queryUserIdByRoomId(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 直播间列表的分页查询
     *
     * @param livingRoomReqDTO
     * @return
     */
    PageWrapper<LivingRoomRespDTO> list(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 按直播间名称模糊搜索（只搜开播中的房间，搜索中心用）
     */
    PageWrapper<LivingRoomRespDTO> searchRooms(String keyword, int page, int pageSize);

    /**
     * 根据用户id查询是否正在开播
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
     * 关闭直播间
     *
     * @param livingRoomReqDTO
     * @return
     */
    boolean closeLiving(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 用户在pk直播间中，连上线请求
     *
     * @param livingRoomReqDTO
     * @return
     */
    LivingPkRespDTO onlinePk(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 根据roomId查询当前pk人是谁
     *
     * @param roomId
     * @return
     */
    Long queryOnlinePkUserId(Integer roomId);

    /**
     * 用户在pk直播间下线
     *
     * @param livingRoomReqDTO
     * @return
     */
    boolean offlinePk(LivingRoomReqDTO livingRoomReqDTO);

    /**
     * 查询主播当前进行中的直播间（无则返回null）
     */
    LivingRoomRespDTO queryByAnchorId(Long anchorId);

    /**
     * 连麦：主播邀请观众（返回 linkMicId，重复邀请返回 null），5572 信令单发观众
     */
    Long inviteLinkMic(Integer roomId, Long guestUserId);

    /**
     * 连麦：观众接受（生成第二路推流地址并广播开始）
     */
    Boolean acceptLinkMic(Long linkMicId);

    /**
     * 连麦：挂断（主播/观众均可），广播结束
     */
    Boolean hangUpLinkMic(Integer roomId);

    /**
     * PK 竞技化：观众点赞为主播方加分（+1，每观众每日上限 50），广播 5558 进度
     */
    Boolean pkLike(Integer roomId, Long userId);
}
