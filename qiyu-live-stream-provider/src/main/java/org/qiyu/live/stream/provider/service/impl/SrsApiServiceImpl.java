package org.qiyu.live.stream.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;

import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.service.ISrsApiService;

/**
 * SRS HTTP API 调用实现
 */
@Service
public class SrsApiServiceImpl implements ISrsApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SrsApiServiceImpl.class);

    @Resource
    private SrsConfig srsConfig;

    private final OkHttpClient httpClient;

    public SrsApiServiceImpl(SrsConfig srsConfig) {
        this.srsConfig = srsConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String queryStreams() {
        String url = srsConfig.getApiUrl() + "/api/v1/streams/";
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                return response.body() != null ? response.body().string() : "";
            }
        } catch (Exception e) {
            LOGGER.error("queryStreams error, url={}", url, e);
            return "";
        }
    }

    /**
     * 从 /api/v1/streams/ 列表中查找指定 streamKey 的流
     * 注意：SRS 的 /api/v1/streams/{id} 只支持数字 id，按 streamKey 名字查会返回 code=2048
     */
    private JSONObject findStream(String streamKey) {
        String body = queryStreams();
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(body);
            if (json.getIntValue("code") != 0) {
                return null;
            }
            com.alibaba.fastjson.JSONArray streams = json.getJSONArray("streams");
            if (streams == null) {
                return null;
            }
            for (int i = 0; i < streams.size(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                if (!streamKey.equals(stream.getString("name"))) {
                    continue;
                }
                // 在线标记在嵌套字段 publish.active（推流端存在即在线）
                JSONObject publish = stream.getJSONObject("publish");
                if (publish != null && publish.getBooleanValue("active")) {
                    return stream;
                }
            }
        } catch (Exception e) {
            LOGGER.error("findStream error, streamKey={}", streamKey, e);
        }
        return null;
    }

    @Override
    public boolean isStreamOnline(String streamKey) {
        return findStream(streamKey) != null;
    }

    @Override
    public int getViewerCount(String streamKey) {
        JSONObject stream = findStream(streamKey);
        if (stream == null) {
            return 0;
        }
        // clients 包含推流端自身，减 1 得到观看人数
        return Math.max(0, stream.getIntValue("clients") - 1);
    }

    @Override
    public int kickPublishClients(String streamKey) {
        String listUrl = srsConfig.getApiUrl() + "/api/v1/clients?page=1&per_page=100";
        int kicked = 0;
        try {
            Request request = new Request.Builder().url(listUrl).get().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!StringUtils.hasText(body)) {
                    LOGGER.warn("[kickPublishClients] empty response body, url={}", listUrl);
                    return -1;
                }
                JSONObject json = JSON.parseObject(body);
                if (json.getIntValue("code") != 0) {
                    LOGGER.warn("[kickPublishClients] srs api code={}, body={}", json.getIntValue("code"), body);
                    return -1;
                }
                com.alibaba.fastjson.JSONArray clients = json.getJSONArray("clients");
                if (clients == null) {
                    LOGGER.warn("[kickPublishClients] no clients array in response: {}", body);
                    return 0;
                }
                for (int i = 0; i < clients.size(); i++) {
                    JSONObject client = clients.getJSONObject(i);
                    // 注意：clients API 的 stream 字段是 SRS 内部流ID(vid-xxx)，流名在 name 字段
                    String clientName = client.getString("name");
                    String type = client.getString("type");
                    if (streamKey.equals(clientName) && type != null && type.contains("publish")) {
                        if (deleteClient(client.getString("id"))) {
                            kicked++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("kickPublishClients error, streamKey={}", streamKey, e);
            return -1;
        }
        LOGGER.info("[kickPublishClients] streamKey={}, kicked={}", streamKey, kicked);
        return kicked;
    }

    private boolean deleteClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return false;
        }
        String url = srsConfig.getApiUrl() + "/api/v1/clients/" + clientId;
        try {
            Request request = new Request.Builder().url(url).delete().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                return StringUtils.hasText(body) && JSON.parseObject(body).getIntValue("code") == 0;
            }
        } catch (Exception e) {
            LOGGER.error("deleteClient error, clientId={}", clientId, e);
            return false;
        }
    }

    @Override
    public SrsConfig getSrsConfig() {
        return this.srsConfig;
    }
}
