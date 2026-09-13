package org.qiyu.live.common.interfaces.constants;

/**
 * 用户等级体系常量（经验阈值 + 跨模块共享的 Redis key 前缀）
 * <p>
 * Redis key 前缀写死 user-provider 的应用名：等级/经验值由 user-provider 写、
 * msg-provider 读，而 RedisKeyBuilder 的 prefix 取自各应用自己的 spring.application.name，
 * 跨模块复用会拼出错误前缀，所以这里用固定常量。
 */
public class UserLevelConstants {

    /** 各等级累计经验门槛（下标=等级-1），L12 为封顶 */
    public static final long[] LEVEL_EXP = {0, 100, 300, 600, 1000, 2000, 3500, 6000, 10000, 20000, 35000, 60000};

    public static final int MAX_LEVEL = LEVEL_EXP.length;

    /** 用户等级 Redis key（value=等级数字，升级时写，TTL 7 天，弹幕链路读取渲染徽章） */
    public static final String LEVEL_KEY_PREFIX = "qiyu-live-user-provider:level:";
    /** 用户累计经验 Redis key（value=累计经验，INCRBY 结算） */
    public static final String EXP_KEY_PREFIX = "qiyu-live-user-provider:exp:";

    /** 经验来源场景：看播 */
    public static final int EXP_SCENE_WATCH = 1;
    /** 经验来源场景：弹幕 */
    public static final int EXP_SCENE_DANMU = 2;
    /** 经验来源场景：送礼 */
    public static final int EXP_SCENE_GIFT = 3;
    /** 经验来源场景：发视频 */
    public static final int EXP_SCENE_VIDEO = 4;

    public static int calcLevel(long exp) {
        int level = 1;
        for (int i = LEVEL_EXP.length - 1; i >= 0; i--) {
            if (exp >= LEVEL_EXP[i]) {
                level = i + 1;
                break;
            }
        }
        return level;
    }

    /** 升到 level 还差多少经验（已封顶返回 -1） */
    public static long nextLevelExp(int level) {
        if (level >= MAX_LEVEL) {
            return -1;
        }
        return LEVEL_EXP[level];
    }
}
