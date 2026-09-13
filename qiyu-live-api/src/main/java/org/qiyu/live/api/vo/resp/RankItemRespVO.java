package org.qiyu.live.api.vo.resp;

import java.io.Serializable;

/**
 * 排行榜条目 VO（三种榜通用）
 */
public class RankItemRespVO implements Serializable {

    /** 名次 1 起 */
    private Integer rank;
    /** 主播榜/贡献榜的用户 id；人气榜为空 */
    private Long userId;
    /** 人气榜的房间 id；其余为空 */
    private Integer roomId;
    private String nickName;
    private String avatar;
    private String roomName;
    /** 金币（礼物榜）或人气值（人气榜） */
    private Double score;

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
