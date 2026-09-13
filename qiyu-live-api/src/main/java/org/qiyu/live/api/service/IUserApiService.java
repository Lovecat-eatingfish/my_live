package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.resp.UserProfileRespVO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;

/**
 * 用户/关系链服务（api 层）
 */
public interface IUserApiService {

    Boolean follow(Long followUserId);

    Boolean unfollow(Long followUserId);

    Boolean isFollow(Long targetUserId);

    PageWrapper<UserDTO> followList(int page, int pageSize);

    PageWrapper<UserDTO> fansList(int page, int pageSize);

    UserProfileRespVO profile(Long targetUserId);
}
