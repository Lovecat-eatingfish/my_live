package org.qiyu.live.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IRankApiService;
import org.qiyu.live.api.vo.resp.RankItemRespVO;
import org.qiyu.live.common.interfaces.constants.RankConstants;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排行榜实现：ZSET member/score 均为普通字符串，读侧反查昵称/房间名补全展示信息
 */
@Service
public class RankApiServiceImpl implements IRankApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RankApiServiceImpl.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;

    @Override
    public List<RankItemRespVO> anchorGiftRank(String period) {
        String day = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String key;
        if ("week".equalsIgnoreCase(period)) {
            key = unionWeekly(day);
        } else {
            key = RankConstants.ANCHOR_GIFT_DAILY_PREFIX + day;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 9);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<RankItemRespVO> list = new ArrayList<>();
        int rank = 1;
        Set<Long> userIds = new HashSet<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            RankItemRespVO vo = new RankItemRespVO();
            vo.setRank(rank++);
            vo.setUserId(Long.valueOf(tuple.getValue()));
            vo.setScore(tuple.getScore());
            userIds.add(vo.getUserId());
            list.add(vo);
        }
        fillUserInfo(list, userIds);
        return list;
    }

    @Override
    public List<RankItemRespVO> roomGiftRank(Integer roomId) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(RankConstants.ROOM_GIFT_PREFIX + roomId, 0, 9);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<RankItemRespVO> list = new ArrayList<>();
        int rank = 1;
        Set<Long> userIds = new HashSet<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            RankItemRespVO vo = new RankItemRespVO();
            vo.setRank(rank++);
            vo.setUserId(Long.valueOf(tuple.getValue()));
            vo.setScore(tuple.getScore());
            userIds.add(vo.getUserId());
            list.add(vo);
        }
        fillUserInfo(list, userIds);
        return list;
    }

    @Override
    public List<RankItemRespVO> heatRank() {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(RankConstants.ROOM_HEAT_KEY, 0, 9);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        List<RankItemRespVO> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            RankItemRespVO vo = new RankItemRespVO();
            vo.setRank(rank++);
            vo.setRoomId(Integer.valueOf(tuple.getValue()));
            vo.setScore(tuple.getScore());
            try {
                LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(vo.getRoomId());
                vo.setRoomName(room == null ? ("房间" + vo.getRoomId()) : room.getRoomName());
            } catch (Exception e) {
                vo.setRoomName("房间" + vo.getRoomId());
            }
            list.add(vo);
        }
        return list;
    }

    /** 7 个日榜 ZUNIONSTORE 成周榜临时 key（TTL 1 天），返回周榜 key */
    private String unionWeekly(String today) {
        String weekKey = RankConstants.ANCHOR_GIFT_WEEK_PREFIX + today;
        List<String> dailyKeys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dailyKeys.add(RankConstants.ANCHOR_GIFT_DAILY_PREFIX
                    + LocalDate.now().minusDays(i).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        }
        try {
            stringRedisTemplate.opsForZSet().unionAndStore(dailyKeys.get(0), dailyKeys.subList(1, dailyKeys.size()), weekKey);
            stringRedisTemplate.expire(weekKey, java.time.Duration.ofDays(1));
        } catch (Exception e) {
            LOGGER.error("[unionWeekly] error, fallback to today only", e);
            return dailyKeys.get(0);
        }
        return weekKey;
    }

    private void fillUserInfo(List<RankItemRespVO> list, Set<Long> userIds) {
        try {
            Map<Long, UserDTO> userMap = userRpc.batchQueryUserInfo(new ArrayList<>(userIds));
            for (RankItemRespVO vo : list) {
                UserDTO user = userMap.get(vo.getUserId());
                if (user != null) {
                    vo.setNickName(user.getNickName());
                    vo.setAvatar(user.getAvatar());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[fillUserInfo] error", e);
        }
    }
}
