package org.qiyu.live.user.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.user.dto.UserProfileExtDTO;
import org.qiyu.live.user.interfaces.rpc.IProfileRpc;
import org.qiyu.live.user.provider.service.IProfileService;

import java.util.List;

/**
 * 用户主页扩展 RPC
 */
@DubboService
public class ProfileRpcImpl implements IProfileRpc {

    @Resource
    private IProfileService profileService;

    @Override
    public UserProfileExtDTO getProfileExt(Long userId) {
        return profileService.getProfileExt(userId);
    }

    @Override
    public List<UserProfileExtDTO> batchQueryProfileExt(List<Long> userIds) {
        return profileService.batchQueryProfileExt(userIds);
    }
}
