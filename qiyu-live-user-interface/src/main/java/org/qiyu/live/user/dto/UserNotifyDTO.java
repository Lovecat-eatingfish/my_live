package org.qiyu.live.user.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 站内通知 DTO
 */
public class UserNotifyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    /** 接收人 */
    private Long userId;
    /** 1系统 2互动 3私信 4开播通知 */
    private Integer type;
    private String title;
    private String content;
    private String jumpUrl;
    private Integer isRead;
    private Date createTime;

    public static UserNotifyDTO of(Long userId, int type, String title, String content, String jumpUrl) {
        UserNotifyDTO dto = new UserNotifyDTO();
        dto.setUserId(userId);
        dto.setType(type);
        dto.setTitle(title);
        dto.setContent(content);
        dto.setJumpUrl(jumpUrl);
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getJumpUrl() {
        return jumpUrl;
    }

    public void setJumpUrl(String jumpUrl) {
        this.jumpUrl = jumpUrl;
    }

    public Integer getIsRead() {
        return isRead;
    }

    public void setIsRead(Integer isRead) {
        this.isRead = isRead;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
