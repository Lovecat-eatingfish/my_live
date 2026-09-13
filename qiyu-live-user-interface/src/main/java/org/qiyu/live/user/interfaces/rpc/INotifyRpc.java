package org.qiyu.live.user.interfaces.rpc;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserNotifyDTO;

/**
 * 站内通知 RPC（写入分散在各动作方，读取/已读在这里）
 */
public interface INotifyRpc {

    /** 通知分页（时间倒序） */
    PageWrapper<UserNotifyDTO> listNotify(Long userId, int page, int pageSize);

    /** 未读数 */
    Integer unreadCount(Long userId);

    /** 标记已读；notifyId 为 null 时全部已读 */
    Boolean markRead(Long userId, Long notifyId);

    /** 发一条通知（各动作方调用） */
    Boolean sendNotify(UserNotifyDTO notifyDTO);
}
