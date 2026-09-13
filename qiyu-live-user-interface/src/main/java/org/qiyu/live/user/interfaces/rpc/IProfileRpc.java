package org.qiyu.live.user.interfaces.rpc;

import org.qiyu.live.user.dto.UserProfileExtDTO;

/**
 * 用户主页扩展 RPC（等级/经验/计数）
 */
public interface IProfileRpc {

    /** 查询主页扩展信息，不存在时自动初始化（level=1 exp=0） */
    UserProfileExtDTO getProfileExt(Long userId);

    /** 批量查询（弹幕/列表渲染等级徽章用），缺失的不在返回中 */
    java.util.List<UserProfileExtDTO> batchQueryProfileExt(java.util.List<Long> userIds);
}
