package org.qiyu.live.msg.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 敏感词（qiyu-live-msg 库，msg-provider 独占读写，admin-api 经 IRiskRpc 管理）
 */
@TableName("risk_sensitive_word")
public class RiskSensitiveWordPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    /** 1拦截 2替换* 3仅记录 */
    private Integer level;
    /** 0全部 1弹幕 2昵称 3视频标题 4评论 5房间名 */
    private Integer scene;
    /** 1有效 0停用 */
    private Integer status;
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getScene() {
        return scene;
    }

    public void setScene(Integer scene) {
        this.scene = scene;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
