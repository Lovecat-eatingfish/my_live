package org.qiyu.live.common.interfaces.constants;

/**
 * 风控域常量（场景编码 + 封禁/禁言 Redis key 约定）
 *
 * key 约定跨服务共享：写方 user-provider（banUser/unban），读方 gateway（封号）、msg-provider（禁言），
 * 所以放 common-interface 统一定义，避免各服务 key 不一致。
 */
public class RiskConstants {

    /** 场景：全部 */
    public static final int SCENE_ALL = 0;
    /** 场景：弹幕 */
    public static final int SCENE_DANMU = 1;
    /** 场景：昵称 */
    public static final int SCENE_NICKNAME = 2;
    /** 场景：视频标题 */
    public static final int SCENE_VIDEO_TITLE = 3;
    /** 场景：评论 */
    public static final int SCENE_COMMENT = 4;
    /** 场景：房间名 */
    public static final int SCENE_ROOM_NAME = 5;

    /** 词级别：拦截 */
    public static final int LEVEL_BLOCK = 1;
    /** 词级别：替换为 * */
    public static final int LEVEL_REPLACE = 2;
    /** 词级别：仅记录 */
    public static final int LEVEL_LOG = 3;

    /** 封号 Redis key 前缀（完整 key = 前缀 + userId） */
    public static final String BAN_ACCOUNT_KEY_PREFIX = "qiyu:live:ban:account:";
    /** 禁言 Redis key 前缀（完整 key = 前缀 + userId） */
    public static final String BAN_MUTE_KEY_PREFIX = "qiyu:live:ban:mute:";

    /** 弹幕频率风控：时间窗口（秒） */
    public static final long DANMU_FREQ_WINDOW_SECONDS = 5L;
    /** 弹幕频率风控：窗口内最大条数 */
    public static final int DANMU_FREQ_LIMIT = 10;
}
