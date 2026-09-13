package org.qiyu.live.common.interfaces.constants;

/**
 * 排行榜 Redis key（纯 ZSET 实现）。
 * key 前缀写死固定值：gift/living provider 写、api 读，而 RedisKeyBuilder 的 prefix
 * 取自各应用自己的 spring.application.name，跨模块复用会拼出错误前缀。
 * ZSET 的 member/score 一律经 StringRedisTemplate 读写（普通字符串），避免各应用
 * RedisTemplate<String,Object> 的 JSON 序列化器差异。
 */
public class RankConstants {

    /** 主播收礼日榜：ZSET member=anchorId score=金币，key 后缀 yyyyMMdd */
    public static final String ANCHOR_GIFT_DAILY_PREFIX = "qiyu-live-rank:gift:anchor:";
    /** 本场(单房间)贡献榜：ZSET member=送礼人 userId score=金币，key 后缀 roomId，TTL 8 天 */
    public static final String ROOM_GIFT_PREFIX = "qiyu-live-rank:gift:room:";
    /** 人气榜：ZSET member=roomId score=进房人数（Set 去重后自增） */
    public static final String ROOM_HEAT_KEY = "qiyu-live-rank:heat:room";
    /** 周榜临时聚合 key 前缀：+ yyyyMMdd（7 个日榜 ZUNIONSTORE，TTL 1 天） */
    public static final String ANCHOR_GIFT_WEEK_PREFIX = "qiyu-live-rank:gift:anchor:week:";

    public static final long RANK_TTL_DAYS = 8;
}
