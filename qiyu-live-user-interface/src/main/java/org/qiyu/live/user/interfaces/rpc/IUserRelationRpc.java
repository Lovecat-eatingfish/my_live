package org.qiyu.live.user.interfaces.rpc;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;

/**
 * 用户关系链 RPC（关注/粉丝）
 */
public interface IUserRelationRpc {

    /** 关注，重复关注幂等返回 true */
    Boolean follow(Long userId, Long followUserId);

    /** 取关，未关注时返回 false */
    Boolean unfollow(Long userId, Long followUserId);

    /** userId 是否关注了 targetUserId */
    Boolean isFollow(Long userId, Long targetUserId);

    /** userId 关注的人（分页） */
    PageWrapper<UserDTO> pageFollow(Long userId, int page, int pageSize);

    /** userId 的粉丝（分页） */
    PageWrapper<UserDTO> pageFans(Long userId, int page, int pageSize);

    /** 关注数 */
    Integer countFollow(Long userId);

    /** 粉丝数 */
    Integer countFans(Long userId);
}
