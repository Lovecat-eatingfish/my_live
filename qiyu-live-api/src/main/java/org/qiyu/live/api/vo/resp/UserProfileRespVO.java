package org.qiyu.live.api.vo.resp;

import java.io.Serializable;
import java.util.Date;

/**
 * 个人主页聚合 VO
 */
public class UserProfileRespVO implements Serializable {

    private Long userId;
    private String nickName;
    private String avatar;
    private Integer sex;
    private Date bornDate;
    private Date createTime;
    /** 等级/经验 */
    private Integer level;
    private Long exp;
    /** 升到下一级还差多少经验，封顶为 -1 */
    private Long nextLevelExp;
    /** 计数 */
    private Integer followCnt;
    private Integer fansCnt;
    private Integer likeReceivedCnt;
    /** 观看者视角 */
    private Boolean isFollow;
    /** 相互关注（对方也关注了我） */
    private Boolean isMutual;
    private Boolean isSelf;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public Date getBornDate() {
        return bornDate;
    }

    public void setBornDate(Date bornDate) {
        this.bornDate = bornDate;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
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

    public Long getNextLevelExp() {
        return nextLevelExp;
    }

    public void setNextLevelExp(Long nextLevelExp) {
        this.nextLevelExp = nextLevelExp;
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

    public Boolean getIsFollow() {
        return isFollow;
    }

    public void setIsFollow(Boolean isFollow) {
        this.isFollow = isFollow;
    }

    public Boolean getIsSelf() {
        return isSelf;
    }

    public void setIsSelf(Boolean isSelf) {
        this.isSelf = isSelf;
    }
    public Boolean getIsMutual() {
        return isMutual;
    }

    public void setIsMutual(Boolean isMutual) {
        this.isMutual = isMutual;
    }

}
