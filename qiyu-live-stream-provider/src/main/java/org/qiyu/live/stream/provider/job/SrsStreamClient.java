package org.qiyu.live.stream.provider.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * SRS 在线流查询客户端：供巡查截帧、房间状态对账等任务判断某路流是否真实存在，
 * 避免只依赖 DB 的 stream_status（SRS 崩溃/回调丢失时会产生僵尸状态）。
 */
@Component
public class SrsStreamClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SrsStreamClient.class);

    @Resource
    private SrsConfig srsConfig;

    /**
     * 查询 SRS 当前在线流的流名集合（streamKey 最后一段）。
     * 查询失败返回 null，调用方应跳过本轮而非误判（退回按 stream_status 处理的旧行为）。
     */
    public Set<String> queryLiveStreamNames() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + srsConfig.getHost() + ":" + srsConfig.getApiPort()
                            + "/api/v1/streams/"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSON.parseObject(resp.body());
            // SRS /api/v1/streams/ 的 streams 数组在顶层；兼容 data 包裹的历史格式
            JSONArray streams = json.getJSONArray("streams");
            if (streams == null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    streams = data.getJSONArray("streams");
                }
            }
            Set<String> names = new HashSet<>();
            if (streams != null) {
                for (int i = 0; i < streams.size(); i++) {
                    names.add(streams.getJSONObject(i).getString("name"));
                }
            }
            return names;
        } catch (Exception e) {
            LOGGER.warn("[SrsStreamClient] query streams failed: {}", e.getMessage());
            return null;
        }
    }

    /** streamKey 形如 app/stream，取最后一段作为 SRS 流名 */
    public static String tailStreamName(String streamKey) {
        return streamKey.contains("/")
                ? streamKey.substring(streamKey.lastIndexOf('/') + 1)
                : streamKey;
    }
}
