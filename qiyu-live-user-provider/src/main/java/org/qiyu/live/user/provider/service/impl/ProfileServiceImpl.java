package org.qiyu.live.user.provider.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.constants.UserLevelConstants;
import org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.dto.UserProfileExtDTO;
import org.qiyu.live.user.provider.dao.mapper.IUserProfileExtMapper;
import org.qiyu.live.user.provider.dao.po.UserProfileExtPO;
import org.qiyu.live.user.provider.service.IProfileService;
import org.qiyu.live.user.provider.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户主页扩展实现：t_user_profile_ext 为计数/经验的最终事实，Redis 只做弹幕链路读取的缓存
 */
@Service
public class ProfileServiceImpl implements IProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileServiceImpl.class);

    private static final String COLUMN_FOLLOW_CNT = "follow_cnt";
    private static final String COLUMN_FANS_CNT = "fans_cnt";
    private static final String COLUMN_LIKE_CNT = "like_received_cnt";

    @Resource
    private IUserProfileExtMapper userProfileExtMapper;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @DubboReference(check = false)
    private ImRouterRpc routerRpc;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;

    @Override
    public UserProfileExtDTO getProfileExt(Long userId) {
        return convert(getOrInitExt(userId));
    }

    @Override
    public List<UserProfileExtDTO> batchQueryProfileExt(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userProfileExtMapper.selectBatchIds(userIds).stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileExtPO getOrInitExt(Long userId) {
        UserProfileExtPO ext = userProfileExtMapper.selectById(userId);
        if (ext != null) {
            return ext;
        }
        UserProfileExtPO init = new UserProfileExtPO();
        init.setUserId(userId);
        init.setFollowCnt(0);
        init.setFansCnt(0);
        init.setLikeReceivedCnt(0);
        init.setLevel(1);
        init.setExp(0L);
        try {
            userProfileExtMapper.insert(init);
            return init;
        } catch (DuplicateKeyException e) {
            return userProfileExtMapper.selectById(userId);
        }
    }

    @Override
    public void changeCnt(Long userId, String cntColumn, int delta) {
        if (userId == null || (!COLUMN_FOLLOW_CNT.equals(cntColumn)
                && !COLUMN_FANS_CNT.equals(cntColumn) && !COLUMN_LIKE_CNT.equals(cntColumn))) {
            return;
        }
        getOrInitExt(userId);
        // GREATEST 兜底计数不为负（setSql 才是 SET 子句，apply 会进 WHERE）
        userProfileExtMapper.update(null, new LambdaUpdateWrapper<UserProfileExtPO>()
                .eq(UserProfileExtPO::getUserId, userId)
                .setSql(String.format("%s = GREATEST(%s + %d, 0)", cntColumn, cntColumn, delta)));
    }

    @Override
    public void addExp(UserExpChangeMqDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getExpDelta() == null || dto.getExpDelta() <= 0) {
            return;
        }
        Long userId = dto.getUserId();
        long delta = dto.getExpDelta();
        UserProfileExtPO ext = getOrInitExt(userId);
        int oldLevel = ext.getLevel() == null ? 1 : ext.getLevel();
        // 原子自增：弹幕并发结算时读-算-写会互相覆盖丢经验
        userProfileExtMapper.update(null, new LambdaUpdateWrapper<UserProfileExtPO>()
                .eq(UserProfileExtPO::getUserId, userId)
                .setSql("exp = exp + " + delta));
        UserProfileExtPO fresh = userProfileExtMapper.selectById(userId);
        long newExp = fresh.getExp() == null ? delta : fresh.getExp();
        int newLevel = Math.max(UserLevelConstants.calcLevel(newExp), oldLevel);
        if (newLevel != oldLevel) {
            userProfileExtMapper.update(null, new LambdaUpdateWrapper<UserProfileExtPO>()
                    .eq(UserProfileExtPO::getUserId, userId)
                    .set(UserProfileExtPO::getLevel, newLevel));
        }
        // 弹幕链路渲染等级徽章用，TTL 7 天，每次结算都刷新
        stringRedisTemplate.opsForValue().set(
                UserLevelConstants.EXP_KEY_PREFIX + userId, String.valueOf(newExp), 30, TimeUnit.DAYS);
        stringRedisTemplate.opsForValue().set(
                UserLevelConstants.LEVEL_KEY_PREFIX + userId, String.valueOf(newLevel), 7, TimeUnit.DAYS);
        if (newLevel > oldLevel) {
            sendLevelUpNotice(userId, newLevel, dto.getRoomId());
            LOGGER.info("[addExp] level up userId={} {} -> {} exp={}", userId, oldLevel, newLevel, newExp);
        }
    }

    /**
     * 升级特效：发生在直播间内则广播全房间（B 站风格），否则单发给本人
     */
    private void sendLevelUpNotice(Long userId, int level, Integer roomId) {
        UserDTO userDTO = userService.getByUserId(userId);
        JSONObject data = new JSONObject();
        data.put("userId", userId);
        data.put("nickName", userDTO != null ? userDTO.getNickName() : ("用户" + userId));
        data.put("level", level);
        List<Long> targetIds;
        if (roomId != null) {
            LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
            reqDTO.setRoomId(roomId);
            reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            targetIds = livingRoomRpc.queryUserIdByRoomId(reqDTO);
        } else {
            targetIds = Collections.singletonList(userId);
        }
        if (targetIds == null || targetIds.isEmpty()) {
            targetIds = Collections.singletonList(userId);
        }
        List<ImMsgBody> msgBodies = new ArrayList<>();
        for (Long targetId : targetIds) {
            ImMsgBody msgBody = new ImMsgBody();
            msgBody.setUserId(targetId);
            msgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            msgBody.setBizCode(ImMsgBizCodeEnum.LEVEL_UP_EFFECT.getCode());
            msgBody.setData(data.toJSONString());
            msgBodies.add(msgBody);
        }
        routerRpc.batchSendMsg(msgBodies);
    }

    private UserProfileExtDTO convert(UserProfileExtPO po) {
        UserProfileExtDTO dto = new UserProfileExtDTO();
        dto.setUserId(po.getUserId());
        dto.setFollowCnt(po.getFollowCnt());
        dto.setFansCnt(po.getFansCnt());
        dto.setLikeReceivedCnt(po.getLikeReceivedCnt());
        dto.setLevel(po.getLevel());
        dto.setExp(po.getExp());
        return dto;
    }
}
