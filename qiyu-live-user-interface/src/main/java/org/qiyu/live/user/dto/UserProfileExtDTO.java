package org.qiyu.live.user.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户主页扩展信息（等级/经验/计数）
 */
public class UserProfileExtDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Integer followCnt;
    private Integer fansCnt;
    private Integer likeReceivedCnt;
    private Integer level;
    private Long exp;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getFollowCnt() {
        return followCnt;
    }

    public void setFollowCnt(Integer followCnt) {
        this.followCnt = followCnt;
    }

    public Integer getFansCnt() {
        return fansCnt;
    }

    public void setFansCnt(Integer fansCnt) {
        this.fansCnt = fansCnt;
    }

    public Integer getLikeReceivedCnt() {
        return likeReceivedCnt;
    }

    public void setLikeReceivedCnt(Integer likeReceivedCnt) {
        this.likeReceivedCnt = likeReceivedCnt;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Long getExp() {
        return exp;
    }

    public void setExp(Long exp) {
        this.exp = exp;
    }
}
