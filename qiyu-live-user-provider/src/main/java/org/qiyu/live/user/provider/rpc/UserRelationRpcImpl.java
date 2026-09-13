package org.qiyu.live.user.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.rpc.IUserRelationRpc;
import org.qiyu.live.user.provider.service.IUserRelationService;

/**
 * 用户关系链 RPC
 */
@DubboService
public class UserRelationRpcImpl implements IUserRelationRpc {

    @Resource
    private IUserRelationService userRelationService;

    @Override
    public Boolean follow(Long userId, Long followUserId) {
        return userRelationService.follow(userId, followUserId);
    }

    @Override
    public Boolean unfollow(Long userId, Long followUserId) {
        return userRelationService.unfollow(userId, followUserId);
    }

    @Override
    public Boolean isFollow(Long userId, Long targetUserId) {
        return userRelationService.isFollow(userId, targetUserId);
    }

    @Override
    public PageWrapper<UserDTO> pageFollow(Long userId, int page, int pageSize) {
        return userRelationService.pageFollow(userId, page, pageSize);
    }

    @Override
    public PageWrapper<UserDTO> pageFans(Long userId, int page, int pageSize) {
        return userRelationService.pageFans(userId, page, pageSize);
    }

    @Override
    public Integer countFollow(Long userId) {
        return userRelationService.countFollow(userId);
    }

    @Override
    public Integer countFans(Long userId) {
        return userRelationService.countFans(userId);
    }
}
