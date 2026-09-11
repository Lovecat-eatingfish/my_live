package org.qiyu.live.stream.provider.service;

import org.qiyu.live.stream.provider.config.SrsConfig;

/**
 * SRS HTTP API 调用服务
 */
public interface ISrsApiService {

    /**
     * 查询所有流信息
     * @return SRS 返回的 JSON 字符串
     */
    String queryStreams();

    /**
     * 查询指定 streamKey 的流是否存在
     * @param streamKey 流标识
     * @return 是否在线
     */
    boolean isStreamOnline(String streamKey);

    /**
     * 获取当前在线人数
     * @param streamKey 流标识
     * @return 在线观众数
     */
    int getViewerCount(String streamKey);

    /** 获取 SRS 配置 */
    SrsConfig getSrsConfig();
}
