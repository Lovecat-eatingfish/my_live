package org.qiyu.live.common.interfaces.constants;

/**
 * 直播间投票常量（跨服务固定前缀，不走 RedisKeyBuilder）
 */
public interface VoteConstants {

    /** 投票上下文 JSON：{title, options[], endTime, anchorId} */
    String VOTE_CTX_KEY_PREFIX = "qiyu:live:vote:";

    /** 已投票观众集合（一人一票） */
    String VOTE_VOTED_KEY_PREFIX = "qiyu:live:vote_voted:";

    /** 各选项票数 hash：field=选项下标 */
    String VOTE_COUNTS_KEY_PREFIX = "qiyu:live:vote_counts:";

    /** 允许的投票时长（秒）→ RocketMQ 延迟级别 */
    java.util.Map<Integer, Integer> DURATION_DELAY_LEVEL = java.util.Map.of(
            30, 4, 60, 5, 120, 6, 180, 7, 300, 9);

    /** 选项数上限 */
    int MAX_OPTIONS = 6;
}
