package org.qiyu.live.living.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.living.interfaces.dto.LivingPkRespDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.living.provider.service.ILivingRoomService;
import org.qiyu.live.living.provider.service.ILivingRoomTxService;

import java.util.List;

/**
 * @Author idea
 * @Date: Created in 21:24 2023/7/19
 * @Description
 */
@DubboService
public class LivingRoomRpcImpl implements ILivingRoomRpc {

    @Resource
    private ILivingRoomService livingRoomService;
    @Resource
    private org.qiyu.live.living.provider.service.ILinkMicService linkMicService;
    @Resource
    private ILivingRoomTxService livingRoomTxService;

    @Override
    public List<Long> queryUserIdByRoomId(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomService.queryUserIdByRoomId(livingRoomReqDTO);
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> list(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomService.list(livingRoomReqDTO);
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> searchRooms(String keyword, int page, int pageSize) {
        return livingRoomService.searchRooms(keyword, page, pageSize);
    }

    @Override
    public LivingRoomRespDTO queryByRoomId(Integer roomId) {
        return livingRoomService.queryByRoomId(roomId);
    }


    @Override
    public Integer startLivingRoom(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomService.startLivingRoom(livingRoomReqDTO);
    }

    @Override
    public boolean closeLiving(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomTxService.closeLiving(livingRoomReqDTO);
    }

    @Override
    public LivingPkRespDTO onlinePk(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomService.onlinePk(livingRoomReqDTO);
    }

    @Override
    public Long queryOnlinePkUserId(Integer roomId) {
        return livingRoomService.queryOnlinePkUserId(roomId);
    }

    @Override
    public LivingRoomRespDTO queryByAnchorId(Long anchorId) {
        return livingRoomService.queryByAnchorId(anchorId);
    }

    @Override
    public Long inviteLinkMic(Integer roomId, Long guestUserId) {
        return linkMicService.invite(roomId, guestUserId);
    }

    @Override
    public Boolean acceptLinkMic(Long linkMicId) {
        return linkMicService.accept(linkMicId);
    }

    @Override
    public Boolean hangUpLinkMic(Integer roomId) {
        return linkMicService.hangUp(roomId);
    }

    @Override
    public Boolean pkLike(Integer roomId, Long userId) {
        return livingRoomService.pkLike(roomId, userId);
    }

    @Override
    public boolean offlinePk(LivingRoomReqDTO livingRoomReqDTO) {
        return livingRoomService.offlinePk(livingRoomReqDTO);
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> listByAnchorIds(java.util.List<Long> anchorIds, int page, int pageSize) {
        return livingRoomService.listByAnchorIds(anchorIds, page, pageSize);
    }

    @Override
    public String createLottery(Integer roomId, Long userId, String keyword, int durationSec, int winnerCount, int rewardCoins) {
        return livingRoomService.createLottery(roomId, userId, keyword, durationSec, winnerCount, rewardCoins);
    }
}
