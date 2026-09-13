package org.qiyu.live.living.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.qiyu.live.common.interfaces.topic.ImCoreServerProviderTopicNames;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.idea.qiyu.live.framework.redis.starter.key.LivingProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.enums.CommonStatusEum;
import org.qiyu.live.common.interfaces.constants.LotteryConstants;
import org.qiyu.live.living.provider.dao.mapper.LivingRoomAdminMapper;
import org.qiyu.live.living.provider.dao.po.LivingRoomAdminPO;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.core.server.interfaces.dto.ImOfflineDTO;
import org.qiyu.live.im.core.server.interfaces.dto.ImOnlineDTO;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.constants.LivingRoomTypeEnum;
import org.qiyu.live.living.interfaces.dto.LivingPkRespDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.living.provider.dao.mapper.LivingRoomRecordMapper;
import org.qiyu.live.living.provider.dao.po.LivingRoomPO;
import org.qiyu.live.living.provider.dao.po.LivingRoomRecordPO;
import org.qiyu.live.living.provider.service.ILivingRoomService;
import org.qiyu.live.living.provider.service.ILivingRoomTxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author idea
 * @Date: Created in 21:24 2023/7/19
 * @Description
 */
@Service
public class LivingRoomServiceImpl implements ILivingRoomService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingRoomServiceImpl.class);

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private LivingRoomRecordMapper livingRoomRecordMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @Resource
    private LivingProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder giftCacheKeyBuilder;
    @Resource
    private ILivingRoomTxService livingRoomTxService;
    @Resource
    private MQProducer mqProducer;
    @Resource
    private org.qiyu.live.living.provider.dao.mapper.LivingRoomAdminMapper livingRoomAdminMapper;
    @DubboReference(check = false)
    private org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc currencyAccountRpc;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;
    @DubboReference(check = false)
    private org.qiyu.live.user.interfaces.IUserRpc userRpc;

    /** PK 进度 Lua（与 gift-provider SendGiftConsumer 同 key 同语义） */
    private static final Long PK_INIT_NUM = 50L;
    private static final Long PK_MAX_NUM = 100L;
    private static final Long PK_MIN_NUM = 0L;
    private String PK_LIKE_LUA =
            " local a = redis.call('exists',KEYS[1]); " +
            " if a == 0 then redis.call('set',KEYS[1],ARGV[1]) end; " +
            " return redis.call('incrby',KEYS[1],tonumber(ARGV[4])) ";

    @Override
    public List<Long> queryUserIdByRoomId(LivingRoomReqDTO livingRoomReqDTO) {
        Integer roomId = livingRoomReqDTO.getRoomId();
        Integer appId = livingRoomReqDTO.getAppId();
        String cacheKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, appId);
        //0-100,101-200,201-300 (0-末尾)
        Cursor<Object> cursor = redisTemplate.opsForSet().scan(cacheKey, ScanOptions.scanOptions().match("*").count(100).build());
        List<Long> userIdList = new ArrayList<>();
        while (cursor.hasNext()) {
            Integer userId = (Integer) cursor.next();
            userIdList.add(Long.valueOf(userId));
        }
        return userIdList;
    }

    @Override
    public void userOfflineHandler(ImOfflineDTO imOfflineDTO) {
        LOGGER.info("offline handler,imOfflineDTO is {}", imOfflineDTO);
        Long userId = imOfflineDTO.getUserId();
        Integer roomId = imOfflineDTO.getRoomId();
        Integer appId = imOfflineDTO.getAppId();
        String cacheKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, appId);
        redisTemplate.opsForSet().remove(cacheKey, userId);
        //监听pk主播下线行为
        LivingRoomReqDTO roomReqDTO = new LivingRoomReqDTO();
        roomReqDTO.setRoomId(imOfflineDTO.getRoomId());
        roomReqDTO.setPkObjId(imOfflineDTO.getUserId());
        roomReqDTO.setAnchorId(imOfflineDTO.getUserId());
        this.offlinePk(roomReqDTO);
        //主播断开IM不再直接关播：刷新浏览器也会触发IM断线，直接关播体验很差。
        //投递30秒延迟消息做关播检查，到期时校验：主播已回房 or 推流仍存活（OBS场景）则放行
        LambdaQueryWrapper<LivingRoomPO> anchorRoomWrapper = new LambdaQueryWrapper<>();
        anchorRoomWrapper.eq(LivingRoomPO::getAnchorId, userId)
                .eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode())
                .last("limit 1");
        LivingRoomPO anchorRoom = livingRoomMapper.selectOne(anchorRoomWrapper);
        if (anchorRoom == null) {
            return;
        }
        Message closeCheckMsg = new Message();
        closeCheckMsg.setTopic(ImCoreServerProviderTopicNames.LIVING_ROOM_CLOSE_CHECK);
        closeCheckMsg.setBody(JSON.toJSONBytes(roomReqDTO));
        closeCheckMsg.setDelayTimeLevel(4); // 30s 宽限期
        try {
            mqProducer.send(closeCheckMsg);
            LOGGER.info("[userOfflineHandler] 主播断线，已投递关播检查, roomId={}, anchorId={}", roomId, userId);
        } catch (Exception e) {
            LOGGER.error("[userOfflineHandler] 关播检查消息发送失败，兜底直接关播, roomId={}", roomId, e);
            livingRoomTxService.closeLiving(roomReqDTO);
        }
    }

    @Override
    public LivingRoomRespDTO queryByAnchorId(Long anchorId) {
        LambdaQueryWrapper<LivingRoomPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivingRoomPO::getAnchorId, anchorId)
                .eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode())
                .orderByDesc(LivingRoomPO::getId)
                .last("limit 1");
        LivingRoomPO roomPO = livingRoomMapper.selectOne(wrapper);
        return roomPO == null ? null : ConvertBeanUtils.convert(roomPO, LivingRoomRespDTO.class);
    }

    @Override
    public void closeLivingCheck(ImOfflineDTO imOfflineDTO) {
        Integer roomId = imOfflineDTO.getRoomId();
        LivingRoomPO roomPO = livingRoomMapper.selectById(roomId);
        if (roomPO == null || roomPO.getStatus() == null
                || roomPO.getStatus() != CommonStatusEum.VALID_STATUS.getCode()) {
            return; // 房间已关闭，无需处理
        }
        Long anchorId = roomPO.getAnchorId();
        //条件1：主播已回到房间（IM重连）→ 放行
        String userSetKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, imOfflineDTO.getAppId() == null
                ? AppIdEnum.QIYU_LIVE_BIZ.getCode() : imOfflineDTO.getAppId());
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userSetKey, anchorId))) {
            LOGGER.info("[closeLivingCheck] 主播已回房，放行, roomId={}", roomId);
            return;
        }
        //条件2：推流仍存活（OBS推流不依赖主播浏览器）→ 放行
        if (roomPO.getStreamStatus() != null && roomPO.getStreamStatus() == 1) {
            LOGGER.info("[closeLivingCheck] 推流存活，放行, roomId={}", roomId);
            return;
        }
        LOGGER.info("[closeLivingCheck] 主播离线且推流已断，执行关播, roomId={}", roomId);
        LivingRoomReqDTO closeReq = new LivingRoomReqDTO();
        closeReq.setRoomId(roomId);
        closeReq.setAppId(imOfflineDTO.getAppId());
        closeReq.setAnchorId(imOfflineDTO.getUserId());
        livingRoomTxService.closeLiving(closeReq);
    }

    @Override
    public void userOnlineHandler(ImOnlineDTO imOnlineDTO) {
        LOGGER.info("online handler,imOnlineDTO is {}", imOnlineDTO);
        Long userId = imOnlineDTO.getUserId();
        Integer roomId = imOnlineDTO.getRoomId();
        Integer appId = imOnlineDTO.getAppId();
        String cacheKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, appId);
        //set集合中
        Long added = redisTemplate.opsForSet().add(cacheKey, userId);
        redisTemplate.expire(cacheKey, 12, TimeUnit.HOURS);
        // 人气榜：只在用户首次进房时 +1（Set 返回 1 表示新成员，天然去重）
        if (added != null && added == 1) {
            try {
                stringRedisTemplate.opsForZSet().incrementScore(
                        org.qiyu.live.common.interfaces.constants.RankConstants.ROOM_HEAT_KEY,
                        String.valueOf(roomId), 1);
                stringRedisTemplate.expire(org.qiyu.live.common.interfaces.constants.RankConstants.ROOM_HEAT_KEY,
                        org.qiyu.live.common.interfaces.constants.RankConstants.RANK_TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                LOGGER.error("[userOnlineHandler] heat zincrby error, roomId={}", roomId, e);
            }
            // 进场欢迎：全房间广播系统弹幕（走 5555，system 标记，前端系统样式渲染）
            try {
                sendWelcomeMsg(userId, roomId, appId);
            } catch (Exception e) {
                LOGGER.error("[userOnlineHandler] welcome msg error, roomId={}", roomId, e);
            }
        }
    }

    private void sendWelcomeMsg(Long userId, Integer roomId, Integer appId) {
        org.qiyu.live.user.dto.UserDTO userDTO = userRpc.getByUserId(userId);
        String nick = userDTO == null || userDTO.getNickName() == null ? ("用户" + userId) : userDTO.getNickName();
        // 粉丝灯牌：房间主播的粉丝亲密度（每日观看 +10，送礼累计）
        Long anchorId = null;
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room != null && room.getId() != null) {
            anchorId = room.getAnchorId();
        }
        int fanLevel = 0;
        if (anchorId != null) {
            try {
                // 每日首次进房 +10（关注与否不强制，进房即积累亲密度）
                String dailyKey = org.qiyu.live.common.interfaces.constants.FanConstants.FAN_DAILY_KEY_PREFIX
                        + anchorId + ":" + userId + ":"
                        + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                        .setIfAbsent(dailyKey, "1", java.time.Duration.ofHours(25)))) {
                    stringRedisTemplate.opsForHash().increment(
                            org.qiyu.live.common.interfaces.constants.FanConstants.FAN_POINTS_KEY_PREFIX + anchorId,
                            String.valueOf(userId),
                            org.qiyu.live.common.interfaces.constants.FanConstants.DAILY_WATCH_POINTS);
                }
                Object pts = stringRedisTemplate.opsForHash().get(
                        org.qiyu.live.common.interfaces.constants.FanConstants.FAN_POINTS_KEY_PREFIX + anchorId,
                        String.valueOf(userId));
                fanLevel = org.qiyu.live.common.interfaces.constants.FanConstants.levelOf(
                        pts == null ? 0 : Long.parseLong(pts.toString()));
            } catch (Exception e) {
                LOGGER.error("[sendWelcomeMsg] fan level error, roomId={}, userId={}", roomId, userId, e);
            }
        }
        com.alibaba.fastjson.JSONObject data = new com.alibaba.fastjson.JSONObject();
        data.put("userId", userId);
        data.put("roomId", roomId);
        data.put("senderName", "系统");
        data.put("content", fanLevel >= org.qiyu.live.common.interfaces.constants.FanConstants.ENTRANCE_EFFECT_LEVEL
                ? "欢迎 " + nick + " 带着粉丝团 " + fanLevel + " 号灯牌进场 🎉"
                : "欢迎 " + nick + " 来到直播间");
        data.put("system", true);
        data.put("fanLevel", fanLevel);
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO reqDTO = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        reqDTO.setRoomId(roomId);
        reqDTO.setAppId(appId);
        java.util.List<Long> userIds = queryUserIdByRoomId(reqDTO);
        for (Long targetId : userIds) {
            org.qiyu.live.im.dto.ImMsgBody body = new org.qiyu.live.im.dto.ImMsgBody();
            body.setUserId(targetId);
            body.setAppId(org.qiyu.live.im.constants.AppIdEnum.QIYU_LIVE_BIZ.getCode());
            body.setBizCode(org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum.LIVING_ROOM_IM_CHAT_MSG_BIZ.getCode());
            body.setData(data.toJSONString());
            imRouterRpc.batchSendMsg(java.util.Collections.singletonList(body));
        }
    }

    @Override
    public List<LivingRoomRespDTO> listAllLivingRoomFromDB(Integer type) {
        LambdaQueryWrapper<LivingRoomPO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode());
        queryWrapper.eq(LivingRoomPO::getType, type);
        //按照时间倒序展示
        queryWrapper.orderByDesc(LivingRoomPO::getId);
        queryWrapper.last("limit 1000");
        return ConvertBeanUtils.convertList(livingRoomMapper.selectList(queryWrapper), LivingRoomRespDTO.class);
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> list(LivingRoomReqDTO livingRoomReqDTO) {
        String cacheKey = cacheKeyBuilder.buildLivingRoomList(livingRoomReqDTO.getType());
        int page = livingRoomReqDTO.getPage();
        int pageSize = livingRoomReqDTO.getPageSize();
        long total = redisTemplate.opsForList().size(cacheKey);
        List<Object> resultList = redisTemplate.opsForList().range(cacheKey, (page - 1) * pageSize, (page * pageSize));
        PageWrapper<LivingRoomRespDTO> pageWrapper = new PageWrapper<>();
        if (CollectionUtils.isEmpty(resultList)) {
            pageWrapper.setList(Collections.emptyList());
            pageWrapper.setHasNext(false);
            return pageWrapper;
        } else {
            List<LivingRoomRespDTO> livingRoomRespDTOS = ConvertBeanUtils.convertList(resultList, LivingRoomRespDTO.class);
            pageWrapper.setList(livingRoomRespDTOS);
            pageWrapper.setHasNext(page * pageSize < total);
            return pageWrapper;
        }
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> searchRooms(String keyword, int page, int pageSize) {
        PageWrapper<LivingRoomRespDTO> pageWrapper = new PageWrapper<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            pageWrapper.setList(Collections.emptyList());
            pageWrapper.setHasNext(false);
            return pageWrapper;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<LivingRoomPO> poPage =
                livingRoomMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomPO>()
                                .eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode())
                                .like(LivingRoomPO::getRoomName, keyword.trim())
                                .orderByDesc(LivingRoomPO::getId));
        pageWrapper.setList(ConvertBeanUtils.convertList(poPage.getRecords(), LivingRoomRespDTO.class));
        pageWrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return pageWrapper;
    }

    @Override
    public PageWrapper<LivingRoomRespDTO> listByAnchorIds(java.util.List<Long> anchorIds, int page, int pageSize) {
        PageWrapper<LivingRoomRespDTO> pageWrapper = new PageWrapper<>();
        if (CollectionUtils.isEmpty(anchorIds)) {
            pageWrapper.setList(Collections.emptyList());
            pageWrapper.setHasNext(false);
            return pageWrapper;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<LivingRoomPO> poPage =
                livingRoomMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomPO>()
                                .eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode())
                                .in(LivingRoomPO::getAnchorId, anchorIds)
                                .orderByDesc(LivingRoomPO::getId));
        pageWrapper.setList(ConvertBeanUtils.convertList(poPage.getRecords(), LivingRoomRespDTO.class));
        pageWrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return pageWrapper;
    }

    @Override
    public String createLottery(Integer roomId, Long userId, String keyword, int durationSec, int winnerCount, int rewardCoins) {
        if (roomId == null || userId == null || keyword == null || keyword.trim().isEmpty()) {
            return "参数不完整";
        }
        if (!LotteryConstants.DURATION_DELAY_LEVEL.containsKey(durationSec)) {
            return "抽奖时长仅支持 30s/1m/2m/3m/5m";
        }
        if (winnerCount <= 0 || winnerCount > 100 || rewardCoins < 0) {
            return "中奖人数或奖励配置不合法";
        }
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null || !userId.equals(room.getAnchorId())) {
            return "只有主播能发起抽奖";
        }
        String ctxKey = LotteryConstants.ROOM_LOTTERY_KEY_PREFIX + roomId;
        if (stringRedisTemplate.hasKey(ctxKey)) {
            return "已有进行中的抽奖";
        }
        // 奖励金币先从主播余额扣（不足则拒绝）
        if (rewardCoins > 0) {
            Integer balance = currencyAccountRpc.getBalance(userId);
            if (balance == null || balance < rewardCoins) {
                return "余额不足，无法发放奖励";
            }
            currencyAccountRpc.decr(userId, rewardCoins);
        }
        JSONObject ctx = new JSONObject();
        ctx.put("roomId", roomId);
        ctx.put("keyword", keyword.trim());
        ctx.put("winnerCount", winnerCount);
        ctx.put("rewardCoins", rewardCoins);
        ctx.put("anchorId", userId);
        long endTime = System.currentTimeMillis() + durationSec * 1000L;
        ctx.put("endTime", endTime);
        String ctxStr = ctx.toJSONString();
        // 跨服务读取（msg-provider），必须用 StringRedisTemplate，避免 JSON 序列化器 @class 类型头不兼容
        stringRedisTemplate.opsForValue().set(ctxKey, ctxStr, java.time.Duration.ofSeconds(durationSec + 120));
        stringRedisTemplate.delete(LotteryConstants.ROOM_LOTTERY_PARTICIPANTS_PREFIX + roomId);

        // 广播 5574 开奖开始（房间观众可见口令，发弹幕即参与）
        JSONObject start = new JSONObject();
        start.put("roomId", roomId);
        start.put("keyword", keyword.trim());
        start.put("endTime", endTime);
        start.put("winnerCount", winnerCount);
        start.put("rewardCoins", rewardCoins);
        start.put("anchorId", userId);
        try {
            LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
            reqDTO.setRoomId(roomId);
            reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            java.util.List<Long> userIdList = queryUserIdByRoomId(reqDTO);
            batchSendImMsg(userIdList, ImMsgBizCodeEnum.LOTTERY_START.getCode(), start);
        } catch (Exception e) {
            LOGGER.error("[createLottery] broadcast start error, roomId={}", roomId, e);
        }

        // 延迟 MQ 到点结算（RocketMQ 延迟级别固定档位，时长映射见 LotteryConstants）
        try {
            org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message(
                    "LivingLotterySettleTopic",
                    String.valueOf(roomId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            msg.setDelayTimeLevel(LotteryConstants.DURATION_DELAY_LEVEL.get(durationSec));
            mqProducer.send(msg);
        } catch (Exception e) {
            LOGGER.error("[createLottery] send settle mq error, roomId={}", roomId, e);
            return "抽奖已发起但结算消息发送失败";
        }
        return null;
    }

    @Override
    public String setAnnouncement(Integer roomId, Long userId, String announcement) {
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null || !userId.equals(room.getAnchorId())) {
            return "只有主播能设置公告";
        }
        if (announcement != null && announcement.length() > 200) {
            return "公告最多 200 字";
        }
        livingRoomMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LivingRoomPO>()
                .eq(LivingRoomPO::getId, roomId)
                .set(LivingRoomPO::getAnnouncement, announcement == null ? "" : announcement));
        // 刷新房间缓存
        redisTemplate.delete(cacheKeyBuilder.buildLivingRoomObj(roomId));
        return null;
    }

    @Override
    public String appointRoomAdmin(Integer roomId, Long anchorId, Long adminUserId) {
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null || !anchorId.equals(room.getAnchorId())) {
            return "只有主播能任命管理员";
        }
        if (adminUserId == null || adminUserId.equals(room.getAnchorId())) {
            return "不能任命自己为管理员";
        }
        Long exists = livingRoomAdminMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomAdminPO>()
                        .eq(LivingRoomAdminPO::getRoomId, roomId)
                        .eq(LivingRoomAdminPO::getAdminUserId, adminUserId));
        if (exists > 0) {
            return null; // 幂等
        }
        LivingRoomAdminPO po = new LivingRoomAdminPO();
        po.setRoomId(roomId);
        po.setAdminUserId(adminUserId);
        po.setCreateTime(new java.util.Date());
        try {
            livingRoomAdminMapper.insert(po);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发任命幂等
        }
        return null;
    }

    @Override
    public boolean removeRoomAdmin(Integer roomId, Long anchorId, Long adminUserId) {
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null || !anchorId.equals(room.getAnchorId())) {
            return false;
        }
        return livingRoomAdminMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomAdminPO>()
                        .eq(LivingRoomAdminPO::getRoomId, roomId)
                        .eq(LivingRoomAdminPO::getAdminUserId, adminUserId)) > 0;
    }

    @Override
    public boolean isRoomAdmin(Integer roomId, Long userId) {
        if (roomId == null || userId == null) {
            return false;
        }
        return livingRoomAdminMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomAdminPO>()
                        .eq(LivingRoomAdminPO::getRoomId, roomId)
                        .eq(LivingRoomAdminPO::getAdminUserId, userId)) > 0;
    }

    @Override
    public java.util.List<Long> listRoomAdmins(Integer roomId) {
        if (roomId == null) {
            return java.util.Collections.emptyList();
        }
        return livingRoomAdminMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomAdminPO>()
                                .eq(LivingRoomAdminPO::getRoomId, roomId))
                .stream().map(LivingRoomAdminPO::getAdminUserId).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String muteRoomUser(Integer roomId, Long operatorId, Long muteUserId, int minutes) {
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null) {
            return "直播间不存在";
        }
        boolean isAnchor = operatorId.equals(room.getAnchorId());
        if (!isAnchor && !isRoomAdmin(roomId, operatorId)) {
            return "只有主播或管理员能禁言";
        }
        if (operatorId.equals(muteUserId)) {
            return "不能禁言自己";
        }
        if (muteUserId.equals(room.getAnchorId())) {
            return "不能禁言主播";
        }
        if (isRoomAdmin(roomId, muteUserId)) {
            return "不能禁言管理员";
        }
        int mins = Math.min(Math.max(minutes, 1), 1440);
        stringRedisTemplate.opsForValue().set(
                org.qiyu.live.common.interfaces.constants.RiskConstants.ROOM_MUTE_KEY_PREFIX + roomId + ":" + muteUserId,
                String.valueOf(operatorId), java.time.Duration.ofMinutes(mins));
        return null;
    }

    @Override
    public boolean unmuteRoomUser(Integer roomId, Long operatorId, Long muteUserId) {
        LivingRoomRespDTO room = queryByRoomId(roomId);
        if (room == null || room.getId() == null) {
            return false;
        }
        if (!operatorId.equals(room.getAnchorId()) && !isRoomAdmin(roomId, operatorId)) {
            return false;
        }
        return stringRedisTemplate.delete(
                org.qiyu.live.common.interfaces.constants.RiskConstants.ROOM_MUTE_KEY_PREFIX + roomId + ":" + muteUserId);
    }

    @Override
    public LivingRoomRespDTO queryByRoomId(Integer roomId) {
        String cacheKey = cacheKeyBuilder.buildLivingRoomObj(roomId);
        LivingRoomRespDTO queryResult = (LivingRoomRespDTO) redisTemplate.opsForValue().get(cacheKey);
        if (queryResult != null) {
            //空值缓存
            if (queryResult.getId() == null) {
                return null;
            }
            return queryResult;
        }
        LambdaQueryWrapper<LivingRoomPO> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(LivingRoomPO::getId, roomId);
        queryWrapper.eq(LivingRoomPO::getStatus, CommonStatusEum.VALID_STATUS.getCode());
        queryWrapper.last("limit 1");
        queryResult = ConvertBeanUtils.convert(livingRoomMapper.selectOne(queryWrapper), LivingRoomRespDTO.class);
        if (queryResult == null) {
            //防止缓存击穿
            redisTemplate.opsForValue().set(cacheKey, new LivingRoomRespDTO(), 1, TimeUnit.MINUTES);
            return null;
        }
        if (LivingRoomTypeEnum.PK_LIVING_ROOM.getCode().equals(queryResult.getType())) {
            queryResult.setPkObjId(this.queryOnlinePkUserId(roomId));
        }
        redisTemplate.opsForValue().set(cacheKey, queryResult, 30, TimeUnit.MINUTES);
        return queryResult;
    }

    @Override
    public Integer startLivingRoom(LivingRoomReqDTO livingRoomReqDTO) {
        LivingRoomPO livingRoomPO = ConvertBeanUtils.convert(livingRoomReqDTO, LivingRoomPO.class);
        livingRoomPO.setStatus(CommonStatusEum.VALID_STATUS.getCode());
        livingRoomPO.setStartTime(new Date());
        livingRoomMapper.insert(livingRoomPO);
        String cacheKey = cacheKeyBuilder.buildLivingRoomObj(livingRoomPO.getId());
        //防止之前有空值缓存，这里做移除操作
        redisTemplate.delete(cacheKey);
        return livingRoomPO.getId();
    }


    @Override
    public Long queryOnlinePkUserId(Integer roomId) {
        String cacheKey = cacheKeyBuilder.buildLivingOnlinePk(roomId);
        Object userId = redisTemplate.opsForValue().get(cacheKey);
        return userId != null ? Long.valueOf((int) userId) : null;
    }

    @Override
    public LivingPkRespDTO onlinePk(LivingRoomReqDTO livingRoomReqDTO) {
        LivingRoomRespDTO currentLivingRoom = this.queryByRoomId(livingRoomReqDTO.getRoomId());
        LivingPkRespDTO respDTO = new LivingPkRespDTO();
        respDTO.setOnlineStatus(false);
        if (currentLivingRoom.getAnchorId().equals(livingRoomReqDTO.getPkObjId())) {
            respDTO.setMsg("主播不可以连线参与pk");
            return respDTO;
        }
        String cacheKey = cacheKeyBuilder.buildLivingOnlinePk(livingRoomReqDTO.getRoomId());
        boolean tryOnline = redisTemplate.opsForValue().setIfAbsent(cacheKey, livingRoomReqDTO.getPkObjId(), 30, TimeUnit.HOURS);
        if (tryOnline) {
            List<Long> userIdList = this.queryUserIdByRoomId(livingRoomReqDTO);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("pkObjId", livingRoomReqDTO.getPkObjId());
            jsonObject.put("pkObjAvatar", "https://picdm.sunbangyan.cn/2023/08/29/w2qq1k.jpeg");
            batchSendImMsg(userIdList, ImMsgBizCodeEnum.LIVING_ROOM_PK_ONLINE.getCode(), jsonObject);
            respDTO.setMsg("连线成功");
            respDTO.setOnlineStatus(true);
            // 倒计时结算：10 分钟后自动结算胜负（RocketMQ 延迟消息 level14=10min，与红包结算同构）
            try {
                org.apache.rocketmq.common.message.Message msg = new org.apache.rocketmq.common.message.Message(
                        "LivingPkSettleTopic",
                        String.valueOf(livingRoomReqDTO.getRoomId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                msg.setDelayTimeLevel(14);
                mqProducer.send(msg);
            } catch (Exception e) {
                LOGGER.error("[onlinePk] send settle mq error, roomId={}", livingRoomReqDTO.getRoomId(), e);
            }
        } else {
            respDTO.setMsg("目前有人在线，请稍后再试");
        }
        return respDTO;
    }

    @Override
    public boolean offlinePk(LivingRoomReqDTO livingRoomReqDTO) {
        String cacheKey = cacheKeyBuilder.buildLivingOnlinePk(livingRoomReqDTO.getRoomId());
        return redisTemplate.delete(cacheKey);
    }

    private void batchSendImMsg(List<Long> userIdList, int bizCode, JSONObject jsonObject) {
        List<ImMsgBody> imMsgBodies = userIdList.stream().map(userId -> {
            ImMsgBody imMsgBody = new ImMsgBody();
            imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            imMsgBody.setBizCode(bizCode);
            imMsgBody.setUserId(userId);
            imMsgBody.setData(jsonObject.toJSONString());
            return imMsgBody;
        }).collect(Collectors.toList());
        imRouterRpc.batchSendMsg(imMsgBodies);
    }


    @Override
    public Boolean pkLike(Integer roomId, Long userId) {
        Long pkObjId = queryOnlinePkUserId(roomId);
        if (pkObjId == null) {
            return false; // 未在 PK 中
        }
        // 已结算（打满/倒计时）后不再加分
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                org.qiyu.live.common.interfaces.constants.PkConstants.PK_IS_OVER_KEY_PREFIX + roomId))) {
            return false;
        }
        // 每观众每日上限 50（防刷）
        String dayKey = cacheKeyBuilder.buildLivingRoomUserSet(roomId, 0) + ":pklike:" + userId + ":" + java.time.LocalDate.now();
        Long cnt = stringRedisTemplate.opsForValue().increment(dayKey);
        stringRedisTemplate.expire(dayKey, java.time.Duration.ofHours(25));
        if (cnt == null || cnt > 50) {
            return false;
        }
        // 点赞 = 主播方 +1（Lua clamp 到 [0,100]，与送礼加分同 key 同语义）
        String pkNumKey = org.qiyu.live.common.interfaces.constants.PkConstants.PK_NUM_KEY_PREFIX + roomId;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript();
        redisScript.setScriptText(PK_LIKE_LUA);
        redisScript.setResultType(Long.class);
        Long pkNum = redisTemplate.execute(redisScript, Collections.singletonList(pkNumKey),
                PK_INIT_NUM, PK_MAX_NUM, PK_MIN_NUM, 1L);
        // 广播进度 5558（复用送礼 PK 通道，前端进度条自动更新）
        LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
        reqDTO.setRoomId(roomId);
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        List<Long> userIds = queryUserIdByRoomId(reqDTO);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("pkNum", pkNum);
        jsonObject.put("likeAdd", true);
        batchSendImMsg(userIds, ImMsgBizCodeEnum.LIVING_ROOM_PK_SEND_GIFT_SUCCESS.getCode(), jsonObject);
        return pkNum != null;
    }
}
