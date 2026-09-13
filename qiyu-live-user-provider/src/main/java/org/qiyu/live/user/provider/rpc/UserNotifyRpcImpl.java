package org.qiyu.live.user.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserNotifyDTO;
import org.qiyu.live.user.interfaces.rpc.INotifyRpc;
import org.qiyu.live.user.provider.service.INotifyService;

/**
 * 站内通知 RPC
 */
@DubboService
public class UserNotifyRpcImpl implements INotifyRpc {

    @Resource
    private INotifyService notifyService;

    @Override
    public PageWrapper<UserNotifyDTO> listNotify(Long userId, int page, int pageSize) {
        return notifyService.listNotify(userId, page, pageSize);
    }

    @Override
    public Integer unreadCount(Long userId) {
        return notifyService.unreadCount(userId);
    }

    @Override
    public Boolean markRead(Long userId, Long notifyId) {
        return notifyService.markRead(userId, notifyId);
    }

    @Override
    public Boolean sendNotify(UserNotifyDTO notifyDTO) {
        return notifyService.sendNotify(notifyDTO);
    }
}
