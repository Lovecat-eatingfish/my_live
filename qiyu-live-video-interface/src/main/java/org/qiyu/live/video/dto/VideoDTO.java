package org.qiyu.live.video.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频信息 DTO
 */
public class VideoDTO implements Serializable {

    private static final long serialVersionUID = 1547230891234L;

    private Long id;
    private Long userId;
    /** 作者昵称（服务端 enrich） */
    private String nickName;
    /** 作者头像（服务端 enrich） */
    private String avatar;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private Integer tagId;
    /** 标签名（服务端 enrich） */
    private String tagName;
    /** 时长（秒） */
    private Integer duration;
    /** 文件大小（字节） */
    private Long size;
    private Long playCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long shareCount;
    private Long commentCount;
    /** 当前登录用户是否已赞/已藏（查询时按需 enrich） */
    /** 视频状态（1上架 0下架），管理端展示与操作用 */
    private Integer status;
    /** 转码状态（0处理中 1完成 2失败/未转码） */
    private Integer transcodeStatus;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTranscodeStatus() {
        return transcodeStatus;
    }

    public void setTranscodeStatus(Integer transcodeStatus) {
        this.transcodeStatus = transcodeStatus;
    }

    private Boolean liked;
    private Boolean favorited;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Integer getTagId() { return tagId; }
    public void setTagId(Integer tagId) { this.tagId = tagId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Long getPlayCount() { return playCount; }
    public void setPlayCount(Long playCount) { this.playCount = playCount; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public Long getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Long favoriteCount) { this.favoriteCount = favoriteCount; }
    public Long getShareCount() { return shareCount; }
    public void setShareCount(Long shareCount) { this.shareCount = shareCount; }
    public Long getCommentCount() { return commentCount; }
    public void setCommentCount(Long commentCount) { this.commentCount = commentCount; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public Boolean getFavorited() { return favorited; }
    public void setFavorited(Boolean favorited) { this.favorited = favorited; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
