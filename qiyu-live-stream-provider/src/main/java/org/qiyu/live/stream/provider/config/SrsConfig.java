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

    /** WebRTC UDP 端口（宿主机映射端口，浏览器开播/低延迟播放用） */
    private Integer rtcUdpPort = 38000;

    /** 推流密钥 */
    private String secret = "your_secret_key";

    /** SRS 容器内 DVR 录制目录前缀（on_dvr 回调的 file 字段以此开头） */
    private String dvrContainerPrefix = "/data/dvr";

    /** 宿主机上 DVR 录制目录（docker 卷映射，Java 读取本地文件上传 MinIO 用） */
    private String dvrLocalDir = "./docker-data/srs/dvr";

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

    /**
     * WebRTC 推流信令接口（SDP 交换）
     * 返回同源相对路径：浏览器请求发给自己访问的前端域名，由 vite 代理(开发)或网关(生产)转发到 SRS 的 api-port，
     * 避免浏览器直连 SRS 地址导致非本机访问不可用
     */
    public String getRtcPublishApiUrl() {
        return "/rtc/v1/publish/";
    }

    /** WebRTC 播放信令接口（观众端拉流，同源相对路径，经代理/网关转发到 SRS） */
    public String getRtcPlayApiUrl() {
        return "/rtc/v1/play/";
    }

    /** WebRTC 流地址基础部分 webrtc://host:port/live */
    public String getRtcStreamBaseUrl() {
        return "webrtc://" + host + ":" + rtcUdpPort + "/live";
    }
}
