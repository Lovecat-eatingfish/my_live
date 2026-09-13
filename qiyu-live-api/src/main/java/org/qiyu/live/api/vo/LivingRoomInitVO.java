package org.qiyu.live.api.vo;

/**
 * @Author idea
 * @Date: Created in 14:33 2023/7/20
 * @Description
 */
public class LivingRoomInitVO {

    private Long anchorId;
    private Long userId;
    private String anchorImg;
    private String roomName;
    private boolean isAnchor;
    private String avatar;
    private Integer roomId;
    private String watcherNickName;
    private String anchorNickName;
    //观众头像
    private String watcherAvatar;
    //默认背景图，为了方便讲解使用
    private String defaultBgImg;
    /** 直播间公告（主播设置） */
    private String announcement;
    /** 当前用户是否本房间管理员 */
    private Boolean isRoomAdmin;
    /** 房间管理员 userId 列表 */
    private java.util.List<Long> roomAdmins;
    private Long pkObjId;

    public Long getPkObjId() {
        return pkObjId;
    }

    public void setPkObjId(Long pkObjId) {
        this.pkObjId = pkObjId;
    }

    public String getWatcherAvatar() {
        return watcherAvatar;
    }

    public void setWatcherAvatar(String watcherAvatar) {
        this.watcherAvatar = watcherAvatar;
    }

    public String getDefaultBgImg() {
        return defaultBgImg;
    }

    public void setDefaultBgImg(String defaultBgImg) {
        this.defaultBgImg = defaultBgImg;
    }

    public String getWatcherNickName() {
        return watcherNickName;
    }

    public void setWatcherNickName(String watcherNickName) {
        this.watcherNickName = watcherNickName;
    }

    public String getAnchorNickName() {
        return anchorNickName;
    }

    public void setAnchorNickName(String anchorNickName) {
        this.anchorNickName = anchorNickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(Long anchorId) {
        this.anchorId = anchorId;
    }

    public String getAnchorImg() {
        return anchorImg;
    }

    public void setAnchorImg(String anchorImg) {
        this.anchorImg = anchorImg;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public boolean isAnchor() {
        return isAnchor;
    }

    public void setAnchor(boolean anchor) {
        isAnchor = anchor;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    @Override
    public String toString() {
        return "LivingRoomInitVO{" +
                "anchorId=" + anchorId +
                ", userId=" + userId +
                ", anchorImg='" + anchorImg + '\'' +
                ", roomName='" + roomName + '\'' +
                ", isAnchor=" + isAnchor +
                ", avatar='" + avatar + '\'' +
                ", roomId=" + roomId +
                ", watcherNickName='" + watcherNickName + '\'' +
                ", anchorNickName='" + anchorNickName + '\'' +
                ", watcherAvatar='" + watcherAvatar + '\'' +
                ", defaultBgImg='" + defaultBgImg + '\'' +
                ", pkObjId=" + pkObjId +
                '}';
    }
    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }

    public Boolean getIsRoomAdmin() {
        return isRoomAdmin;
    }

    public void setIsRoomAdmin(Boolean isRoomAdmin) {
        this.isRoomAdmin = isRoomAdmin;
    }

    public java.util.List<Long> getRoomAdmins() {
        return roomAdmins;
    }

    public void setRoomAdmins(java.util.List<Long> roomAdmins) {
        this.roomAdmins = roomAdmins;
    }

}
