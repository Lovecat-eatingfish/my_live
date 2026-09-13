package org.qiyu.live.user.provider.service;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserNotifyDTO;

/**
 * 站内通知服务
 */
public interface INotifyService {

    PageWrapper<UserNotifyDTO> listNotify(Long userId, int page, int pageSize);

    Integer unreadCount(Long userId);

    Boolean markRead(Long userId, Long notifyId);

    Boolean sendNotify(UserNotifyDTO notifyDTO);
}
