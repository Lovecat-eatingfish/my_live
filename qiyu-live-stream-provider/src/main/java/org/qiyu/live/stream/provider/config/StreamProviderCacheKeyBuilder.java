package org.qiyu.live.stream.provider.config;

import org.springframework.context.annotation.Configuration;

/**
 * Stream Provider Redis Key 构建器
 */
@Configuration
public class StreamProviderCacheKeyBuilder extends org.idea.qiyu.live.framework.redis.starter.key.RedisKeyBuilder {

    private static final String STREAM_STATUS = "stream_status";
    private static final String STREAM_KEY = "stream_key";
    private static final String RECORD_CONTEXT = "record_context";

    /** 推流状态 key */
    public String buildStreamStatus(Integer roomId) {
        return super.getPrefix() + STREAM_STATUS + super.getSplitItem() + roomId;
    }

    /** streamKey 对应关系 key (roomId -> streamKey) */
    public String buildStreamKey(Integer roomId) {
        return super.getPrefix() + STREAM_KEY + super.getSplitItem() + roomId;
    }

    /** streamKey 反向映射 key (streamKey -> roomId)，供 SRS 回调反查 */
    public String buildStreamKeyReverse(String streamKey) {
        return super.getPrefix() + STREAM_KEY + "_reverse" + super.getSplitItem() + streamKey;
    }

    /** 录制上下文 key (streamKey -> 录制信息 JSON)，关播/停流时暂存，on_dvr 回调消费 */
    public String buildRecordContextKey(String streamKey) {
        return super.getPrefix() + RECORD_CONTEXT + super.getSplitItem() + streamKey;
    }
}
