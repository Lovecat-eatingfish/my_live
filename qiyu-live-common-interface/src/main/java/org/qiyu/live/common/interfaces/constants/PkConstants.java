package org.qiyu.live.common.interfaces.constants;

/**
 * PK 进度 Redis key（gift-provider 写、living-provider 读/写）。
 * 必须用固定前缀：RedisKeyBuilder 的 prefix 取自各应用自己的 spring.application.name，
 * 同一个 builder 类跨服务会产出不同前缀的 key（历史坑，见 troubleshooting）。
 * is_over 曾写作 "is over"（带空格），2026-09-19 统一改为下划线；
 * 切换时需保证两端服务同时升级，且清理 Redis 中旧形状的残留 key。
 */
public class PkConstants {

    public static final String PK_NUM_KEY_PREFIX = "qiyu-live-gift-provider:living_pk_key:";
    public static final String PK_IS_OVER_KEY_PREFIX = "qiyu-live-gift-provider:living_pk_is_over:";
    public static final String ONLINE_PK_KEY_PREFIX = "qiyu-live-living-provider:living_online_pk:";
    public static final String ROOM_USER_SET_PREFIX = "qiyu-live-living-provider:living_room_user_set:10001:";
}
