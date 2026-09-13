package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * 私信消息 DTO（5569 下行 data 与 RPC 查询共用）
 */
public class DmMessageDTO implements Serializable {

    private Long msgId;
    private Long fromUid;
    private Long toUid;
    private String content;
    private Date createTime;

    public static DmMessageDTO of(Long fromUid, Long toUid, String content) {
        DmMessageDTO dto = new DmMessageDTO();
        dto.setFromUid(fromUid);
        dto.setToUid(toUid);
        dto.setContent(content);
        return dto;
    }

    public Long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgId) {
        this.msgId = msgId;
    }

    public Long getFromUid() {
        return fromUid;
    }

    public void setFromUid(Long fromUid) {
        this.fromUid = fromUid;
    }

    public Long getToUid() {
        return toUid;
    }

    public void setToUid(Long toUid) {
        this.toUid = toUid;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
