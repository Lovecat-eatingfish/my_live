package org.qiyu.live.video.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频评论 DTO
 */
public class VideoCommentDTO implements Serializable {

    private static final long serialVersionUID = 1547230891235L;

    private Long id;
    private Long videoId;
    private Long userId;
    /** 评论人昵称（服务端 enrich） */
    private String nickName;
    /** 评论人头像（服务端 enrich） */
    private String avatar;
    private String content;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
