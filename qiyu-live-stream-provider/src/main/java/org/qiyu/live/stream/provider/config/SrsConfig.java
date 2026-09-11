package org.qiyu.live.stream.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SRS 流媒体服务器配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiyu.srs")
public class SrsConfig {

    /** SRS 服务器地址 */
    private String host = "127.0.0.1";

    /** RTMP 端口 */
    private Integer rtmpPort = 1935;

    /** HTTP API 端口 */
    private Integer apiPort = 1985;

    /** HLS 分发端口 */
    private Integer hlsPort = 8080;

    /** 推流密钥 */
    private String secret = "your_secret_key";

    /** 回调接口密钥（SRS 回调时通过 X-Srs-Secret header 传递） */
    private String callbackSecret = "";

    /** RTMP 基础地址 */
    public String getRtmpBaseUrl() {
        return "rtmp://" + host + ":" + rtmpPort + "/live";
    }

    /** HLS 基础地址 */
    public String getHlsBaseUrl() {
        return "http://" + host + ":" + hlsPort + "/live";
    }

    /** SRS HTTP API 地址 */
    public String getApiUrl() {
        return "http://" + host + ":" + apiPort;
    }
}
