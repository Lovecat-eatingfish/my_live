package org.qiyu.live.common.interfaces.constants;

/**
 * 粉丝团灯牌常量。跨服务读写（gift 写、living/msg 读），固定前缀不走 RedisKeyBuilder。
 * 亲密度 = 累计送礼金币（每金币+1） + 每日观看（每天首次进房+10）。
 */
public interface FanConstants {

    /** 粉丝亲密度 hash：key={anchorId}，field={userId}，value=points */
    String FAN_POINTS_KEY_PREFIX = "qiyu:live:fan:points:";

    /** 每日观看加分去重 key：{anchorId}:{userId}:{yyyyMMdd}，TTL 25h */
    String FAN_DAILY_KEY_PREFIX = "qiyu:live:fan:daily:";

    /** 每日观看加的亲密度 */
    int DAILY_WATCH_POINTS = 10;

    /** 进场特效门槛（灯牌等级） */
    int ENTRANCE_EFFECT_LEVEL = 3;

    /** 亲密度 → 灯牌等级（0=未加入粉丝团） */
    static int levelOf(long points) {
        if (points >= 20000) return 5;
        if (points >= 5000) return 4;
        if (points >= 1000) return 3;
        if (points >= 100) return 2;
        if (points >= 1) return 1;
        return 0;
    }
}
