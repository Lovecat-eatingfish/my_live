package org.qiyu.live.api.service.impl;

import org.apache.dubbo.config.annotation.DubboReference;import org.qiyu.live.api.error.ApiErrorEnum;
import org.qiyu.live.api.service.IStreamService;
import org.qiyu.live.api.vo.StreamPlayUrlVO;
import org.qiyu.live.api.vo.StreamPushUrlVO;
import org.qiyu.live.api.vo.StreamRecordVO;
import org.qiyu.live.api.vo.StreamStatusVO;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.stream.interfaces.dto.LivingRoomRecordDTO;
import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.PlayBackDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;
import org.qiyu.live.stream.interfaces.rpc.ILivingPlayBackRpc;
import org.qiyu.live.stream.interfaces.rpc.ILivingStreamRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.qiyu.live.web.starter.error.QiyuErrorException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 直播视频流服务实现
 */
@Service
public class StreamServiceImpl implements IStreamService {

    // stream-provider 未启动时降级，不阻塞 api 启动（调用时报 No provider）
    @DubboReference(check = false)
    private ILivingStreamRpc livingStreamRpc;
    @DubboReference(check = false)
    private ILivingPlayBackRpc livingPlayBackRpc;

    @jakarta.annotation.Resource
    private org.springframework.data.redis.core.StringRedisTemplate ticketRedis;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;

    @Override
    public StreamPushUrlVO createPushUrl(Integer roomId) {
        Long userId = QiyuRequestContext.getUserId();
        // 校验房间存在且当前用户是主播本人
        LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(roomId);
        ErrorAssert.isNotNull(room, ApiErrorEnum.LIVING_ROOM_END);
        if (!userId.equals(room.getAnchorId())) {
            throw new QiyuErrorException(-1, "只有主播本人才能获取推流地址");
        }
        LivingStreamPushUrlDTO dto = livingStreamRpc.createPushUrl(roomId, userId);
        ErrorAssert.isNotNull(dto, BizBaseErrorEnum.PARAM_ERROR);
        StreamPushUrlVO vo = ConvertBeanUtils.convert(dto, StreamPushUrlVO.class);
        vo.setRoomId(roomId);
        return vo;
    }

    @Override
    public StreamStatusVO getStreamStatus(Integer roomId) {
        StreamStatusDTO dto = livingStreamRpc.getStreamStatus(roomId);
        return ConvertBeanUtils.convert(dto, StreamStatusVO.class);
    }

    @Override
    public StreamPlayUrlVO getPlayUrl(Integer roomId) {
        // 付费直播间：未购票不可取播放地址（防蹭播）
        org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO roomInfo = livingRoomRpc.queryByRoomId(roomId);
        if (roomInfo != null && roomInfo.getPayType() != null && roomInfo.getPayType() == 1) {
            Long uid = org.qiyu.live.web.starter.context.QiyuRequestContext.getUserId();
            if (uid != null && !uid.equals(roomInfo.getAnchorId()) && !Boolean.TRUE.equals(ticketRedis.hasKey(
                    org.qiyu.live.common.interfaces.constants.TicketConstants.ROOM_TICKET_KEY_PREFIX + roomId + ":" + uid))) {
                throw new org.qiyu.live.web.starter.error.QiyuErrorException(
                        org.qiyu.live.api.error.ApiErrorEnum.TICKET_REQUIRED);
            }
        }
        PlayBackDTO dto = livingPlayBackRpc.getPlayUrl(roomId, "other");
        return ConvertBeanUtils.convert(dto, StreamPlayUrlVO.class);
    }

    @Override
    public List<StreamRecordVO> getRecordList(Integer roomId) {
        List<LivingRoomRecordDTO> dtoList = livingPlayBackRpc.getRecordList(roomId);
        return ConvertBeanUtils.convertList(dtoList, StreamRecordVO.class);
    }

    @Override
    public List<StreamRecordVO> getRecordListByAnchor(Long anchorId) {
        return ConvertBeanUtils.convertList(livingPlayBackRpc.getRecordListByAnchor(anchorId), StreamRecordVO.class);
    }
}
