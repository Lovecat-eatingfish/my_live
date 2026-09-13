package org.qiyu.live.user.provider.service;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;

/**
 * 用户关系链服务（关注/粉丝）
 */
public interface IUserRelationService {

    Boolean follow(Long userId, Long followUserId);

    Boolean unfollow(Long userId, Long followUserId);

    Boolean isFollow(Long userId, Long targetUserId);

    PageWrapper<UserDTO> pageFollow(Long userId, int page, int pageSize);

    PageWrapper<UserDTO> pageFans(Long userId, int page, int pageSize);

    Integer countFollow(Long userId);

    Integer countFans(Long userId);
}
