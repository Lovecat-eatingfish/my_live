package org.qiyu.live.living.provider.service.impl;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.idea.qiyu.live.framework.redis.starter.key.LivingProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.enums.CommonStatusEum;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.living.provider.dao.po.LivingRoomPO;
import org.qiyu.live.living.provider.service.ILivingRoomTxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author idea
 * @Date: Created in 19:21 2023/8/29
 * @Description
 */
@Service
public class LivingRoomTxServiceImpl implements ILivingRoomTxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingRoomTxServiceImpl.class);

    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private LivingProviderCacheKeyBuilder cacheKeyBuilder;
    @DubboReference(check = false)
    private ImRouterRpc routerRpc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeLiving(LivingRoomReqDTO livingRoomReqDTO) {
        Integer roomId = livingRoomReqDTO.getRoomId();
        LivingRoomPO roomPO = livingRoomMapper.selectById(roomId);
        if (roomPO == null) {
            return false;
        }
        //只允许主播本人关播：观众或其他用户断开IM连接时不触发关播
        if (livingRoomReqDTO.getAnchorId() != null && !livingRoomReqDTO.getAnchorId().equals(roomPO.getAnchorId())) {
            return false;
        }
        //置为关闭状态，直播间列表只展示 status=有效 的房间
        roomPO.setStatus(CommonStatusEum.INVALID_STATUS.getCode());
        livingRoomMapper.updateById(roomPO);
        //移除直播间相关缓存
        redisTemplate.delete(cacheKeyBuilder.buildLivingRoomObj(roomId));
        redisTemplate.delete(cacheKeyBuilder.buildLivingRoomList(roomPO.getType()));
        //人气榜：已关闭的房间及时移出，否则会长期占据 top10（ZSET TTL 8天）
        stringRedisTemplate.opsForZSet().remove(
                org.qiyu.live.common.interfaces.constants.RankConstants.ROOM_HEAT_KEY, String.valueOf(roomId));
        LOGGER.info("closeLiving success,roomId is {},anchorId is {}", roomId, roomPO.getAnchorId());
        //通知房间内所有观众直播间已关闭（主播主动关播、或主播断开IM连接的钩子都会走到这里）
        this.notifyLivingRoomClose(roomPO);
        return true;
    }

    /**
     * 广播直播间关闭消息给房间内所有观众
     */
    private void notifyLivingRoomClose(LivingRoomPO roomPO) {
        try {
            Integer roomId = roomPO.getId();
            String userSetKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, AppIdEnum.QIYU_LIVE_BIZ.getCode());
            Set<Long> userIdSet = redisTemplate.opsForSet().members(userSetKey);
            redisTemplate.delete(userSetKey);
            if (CollectionUtils.isEmpty(userIdSet)) {
                return;
            }
            List<ImMsgBody> imMsgBodies = new ArrayList<>();
            for (Object userIdObj : userIdSet) {
                ImMsgBody imMsgBody = new ImMsgBody();
                imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                //redis set 反序列化出来的数值可能是 Integer 或 Long，统一转 long
                imMsgBody.setUserId(((Number) userIdObj).longValue());
                imMsgBody.setBizCode(ImMsgBizCodeEnum.LIVING_ROOM_CLOSE.getCode());
                imMsgBody.setData(JSON.toJSONString(roomId));
                imMsgBodies.add(imMsgBody);
            }
            routerRpc.batchSendMsg(imMsgBodies);
            LOGGER.info("notifyLivingRoomClose done,roomId is {},notifyUserNum is {}", roomId, imMsgBodies.size());
        } catch (Exception e) {
            //通知失败不影响关播结果
            LOGGER.error("notifyLivingRoomClose error,roomId is {}", roomPO.getId(), e);
        }
    }
}
