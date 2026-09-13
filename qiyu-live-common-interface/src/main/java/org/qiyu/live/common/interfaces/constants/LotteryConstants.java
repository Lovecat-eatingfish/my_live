package org.qiyu.live.common.interfaces.constants;

/**
 * 直播间口令抽奖 Redis key 常量。
 * 跨服务读写（living-provider 写，msg-provider 读），必须固定前缀，
 * 不能用 RedisKeyBuilder（其前缀取 spring.application.name，跨服务不一致）。
 */
public interface LotteryConstants {

    /** 房间抽奖上下文（JSON 字符串：roomId/keyword/winnerCount/rewardCoins/anchorId/endTime） */
    String ROOM_LOTTERY_KEY_PREFIX = "qiyu-live-living-provider:living_lottery:";

    /** 房间抽奖参与者集合（SADD 去重，成员=userId） */
    String ROOM_LOTTERY_PARTICIPANTS_PREFIX = "qiyu-live-living-provider:living_lottery_participants:";

    /** 允许的抽奖时长（秒）→ RocketMQ 延迟级别：30s/1m/2m/3m/5m */
    java.util.Map<Integer, Integer> DURATION_DELAY_LEVEL = java.util.Map.of(
            30, 4, 60, 5, 120, 6, 180, 7, 300, 9);
}
