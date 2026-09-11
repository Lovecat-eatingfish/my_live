package org.qiyu.live.stream.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
        String url = srsConfig.getApiUrl() + "/api/v1/streams";
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

    @Override
    public boolean isStreamOnline(String streamKey) {
        // 使用单流查询 API，同时可获取观看人数
        String url = srsConfig.getApiUrl() + "/api/v1/streams/" + streamKey;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!StringUtils.hasText(body)) {
                    return false;
                }
                JSONObject json = JSON.parseObject(body);
                int code = json.getIntValue("code");
                return code == 0;
            }
        } catch (Exception e) {
            LOGGER.error("isStreamOnline error, streamKey={}", streamKey, e);
            return false;
        }
    }

    @Override
    public int getViewerCount(String streamKey) {
        // 复用单流查询 API 获取 clients 数量，避免遍历全量流列表
        String url = srsConfig.getApiUrl() + "/api/v1/streams/" + streamKey;
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!StringUtils.hasText(body)) {
                    return 0;
                }
                JSONObject json = JSON.parseObject(body);
                if (json.getIntValue("code") != 0) {
                    return 0;
                }
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    return data.getIntValue("clients");
                }
            }
        } catch (Exception e) {
            LOGGER.error("getViewerCount error, streamKey={}", streamKey, e);
        }
        return 0;
    }

    @Override
    public SrsConfig getSrsConfig() {
        return this.srsConfig;
    }
}
