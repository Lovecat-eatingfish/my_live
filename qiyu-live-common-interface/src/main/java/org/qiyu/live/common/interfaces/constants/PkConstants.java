package org.qiyu.live.common.interfaces.constants;

/**
 * PK 进度 Redis key（gift-provider 写、living-provider 读/写）。
 * 必须用固定前缀：RedisKeyBuilder 的 prefix 取自各应用自己的 spring.application.name，
 * 同一个 builder 类跨服务会产出不同前缀的 key（历史坑，见 troubleshooting）。
 * 注意 is over 中间的空格是历史遗留 key 形状，不能改。
 */
public class PkConstants {

    public static final String PK_NUM_KEY_PREFIX = "qiyu-live-gift-provider:living_pk_key:";
    public static final String PK_IS_OVER_KEY_PREFIX = "qiyu-live-gift-provider:living_pk_is over:";
    public static final String ONLINE_PK_KEY_PREFIX = "qiyu-live-living-provider:living_online_pk:";
    public static final String ROOM_USER_SET_PREFIX = "qiyu-live-living-provider:living_room_user_set:10001:";
}
