package org.qiyu.live.user.provider.dao.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 用户主页扩展（t_user_profile_ext，计数与等级经验）
 */
@TableName("t_user_profile_ext")
public class UserProfileExtPO {

    @TableId
    private Long userId;
    private Integer followCnt;
    private Integer fansCnt;
    private Integer likeReceivedCnt;
    private Integer level;
    private Long exp;
    private Date updateTime;

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

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
