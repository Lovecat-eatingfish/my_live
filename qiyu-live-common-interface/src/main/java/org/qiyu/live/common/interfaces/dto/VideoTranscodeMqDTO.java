package org.qiyu.live.common.interfaces.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视频转码任务 MQ 消息体
 */
public class VideoTranscodeMqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long videoId;
    private Long userId;

    public static VideoTranscodeMqDTO of(Long videoId, Long userId) {
        VideoTranscodeMqDTO dto = new VideoTranscodeMqDTO();
        dto.setVideoId(videoId);
        dto.setUserId(userId);
        return dto;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
