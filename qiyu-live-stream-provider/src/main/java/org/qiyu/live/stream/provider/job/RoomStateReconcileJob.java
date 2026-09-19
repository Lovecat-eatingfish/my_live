package org.qiyu.live.stream.provider.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.stream.provider.config.StreamProviderCacheKeyBuilder;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.qiyu.live.stream.provider.service.IImBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 房间推流状态对账自愈：
 * SRS on_unpublish 回调丢失（SRS 崩溃/重启、进程被杀）时，房间会停留在
 * stream_status=1 的僵尸状态——巡查截帧、人气、关闭检查全部失真。
 * 每 5 分钟比对 DB 的推流中房间与 SRS 实际在线流：
 *  1) 状态失真 → 复用 onUnpublish 语义修复（DB + Redis 缓存 + IM 5563 通知观众）；
 *  2) 主播也已不在房间 IM 集合 → 走正常关播链路（closeLiving，带主播身份通过校验）。
 */
@Component
public class RoomStateReconcileJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomStateReconcileJob.class);

    private static final int STREAM_STATUS_LIVING = 1;
    private static final int STREAM_STATUS_NOT_START = 0;

    /** 宽限期：刚开播/重启场景下 SRS 回调与在线流查询存在时间窗，3 分钟内不判死 */
    private static final long GRACE_MILLIS = 3 * 60_000L;

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private SrsStreamClient srsStreamClient;
    @Resource
    private StreamProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IImBroadcastService imBroadcastService;
    // living-provider 未启动时降级为调用时才报错，不阻塞本服务启动
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void reconcile() {
        Set<String> liveNames = srsStreamClient.queryLiveStreamNames();
        if (liveNames == null) {
            return; // SRS 不可达时不做任何判断，避免误关
        }
        List<LivingRoomPO> rooms = livingRoomMapper.selectList(new LambdaQueryWrapper<LivingRoomPO>()
                .eq(LivingRoomPO::getStreamStatus, STREAM_STATUS_LIVING)
                .isNotNull(LivingRoomPO::getStreamKey)
                .ne(LivingRoomPO::getStreamKey, ""));
        if (rooms == null || rooms.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (LivingRoomPO room : rooms) {
            try {
                if (liveNames.contains(SrsStreamClient.tailStreamName(room.getStreamKey()))) {
                    continue;
                }
                if (room.getStreamStartTime() != null
                        && now - room.getStreamStartTime().getTime() < GRACE_MILLIS) {
                    continue;
                }
                LOGGER.warn("[reconcile] room {} stream_status=1 but stream absent in SRS, repairing",
                        room.getId());
                repairStreamStatus(room);
                closeIfAnchorAbsent(room);
            } catch (Exception e) {
                LOGGER.warn("[reconcile] room {} failed: {}", room.getId(), e.getMessage());
            }
        }
    }

    /** 复用 onUnpublish 语义：DB 复位 + 删状态缓存 + IM 5563 通知观众推流已结束 */
    private void repairStreamStatus(LivingRoomPO room) {
        livingRoomMapper.updateStreamStatus(room.getId(), STREAM_STATUS_NOT_START, null);
        stringRedisTemplate.delete(cacheKeyBuilder.buildStreamStatus(room.getId()));
        JSONObject notify = new JSONObject();
        notify.put("roomId", room.getId());
        notify.put("status", STREAM_STATUS_NOT_START);
        imBroadcastService.broadcastToRoom(room.getId(), ImMsgBizCodeEnum.LIVING_STREAM_STATUS_CHANGE, notify);
    }

    /** 主播不在房间 IM 集合 → 关播（closeLiving 校验主播身份，观众/他人无法误关） */
    private void closeIfAnchorAbsent(LivingRoomPO room) {
        LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
        reqDTO.setRoomId(room.getId());
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        List<Long> userIds = livingRoomRpc.queryUserIdByRoomId(reqDTO);
        boolean anchorInRoom = userIds != null && userIds.contains(room.getAnchorId());
        if (anchorInRoom) {
            LOGGER.info("[reconcile] room {} anchor still in room, keep room open", room.getId());
            return;
        }
        LivingRoomReqDTO closeReq = new LivingRoomReqDTO();
        closeReq.setRoomId(room.getId());
        closeReq.setAnchorId(room.getAnchorId());
        closeReq.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        boolean closed = livingRoomRpc.closeLiving(closeReq);
        LOGGER.info("[reconcile] room {} anchor absent, closeLiving={} ", room.getId(), closed);
    }
}
