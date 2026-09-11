package org.qiyu.live.stream.provider.config;

import org.springframework.context.annotation.Configuration;

/**
 * Stream Provider Redis Key 构建器
 */
@Configuration
public class StreamProviderCacheKeyBuilder extends org.idea.qiyu.live.framework.redis.starter.key.RedisKeyBuilder {

    private static final String STREAM_STATUS = "stream_status";
    private static final String STREAM_KEY = "stream_key";

    /** 推流状态 key */
    public String buildStreamStatus(Integer roomId) {
        return super.getPrefix() + STREAM_STATUS + super.getSplitItem() + roomId;
    }

    /** streamKey 对应关系 key */
    public String buildStreamKey(Integer roomId) {
        return super.getPrefix() + STREAM_KEY + super.getSplitItem() + roomId;
    }
}
