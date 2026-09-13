package org.qiyu.live.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IUserApiService;
import org.qiyu.live.api.vo.resp.UserProfileRespVO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.constants.UserLevelConstants;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.dto.UserProfileExtDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.user.interfaces.rpc.IProfileRpc;
import org.qiyu.live.user.interfaces.rpc.IUserRelationRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.stereotype.Service;

/**
 * 用户/关系链服务实现（api 层聚合）
 */
@Service
public class UserApiServiceImpl implements IUserApiService {

    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private IUserRelationRpc userRelationRpc;
    @DubboReference(check = false)
    private IProfileRpc profileRpc;

    @Override
    public Boolean follow(Long followUserId) {
        ErrorAssert.isNotNull(followUserId, BizBaseErrorEnum.PARAM_ERROR);
        return userRelationRpc.follow(QiyuRequestContext.getUserId(), followUserId);
    }

    @Override
    public Boolean unfollow(Long followUserId) {
        ErrorAssert.isNotNull(followUserId, BizBaseErrorEnum.PARAM_ERROR);
        return userRelationRpc.unfollow(QiyuRequestContext.getUserId(), followUserId);
    }

    @Override
    public Boolean isFollow(Long targetUserId) {
        ErrorAssert.isNotNull(targetUserId, BizBaseErrorEnum.PARAM_ERROR);
        return userRelationRpc.isFollow(QiyuRequestContext.getUserId(), targetUserId);
    }

    @Override
    public PageWrapper<UserDTO> followList(int page, int pageSize) {
        return userRelationRpc.pageFollow(QiyuRequestContext.getUserId(), page, pageSize);
    }

    @Override
    public PageWrapper<UserDTO> fansList(int page, int pageSize) {
        return userRelationRpc.pageFans(QiyuRequestContext.getUserId(), page, pageSize);
    }

    @Override
    public UserProfileRespVO profile(Long targetUserId) {
        Long viewerId = QiyuRequestContext.getUserId();
        Long profileUserId = targetUserId == null ? viewerId : targetUserId;
        ErrorAssert.isNotNull(profileUserId, BizBaseErrorEnum.PARAM_ERROR);
        UserDTO userDTO = userRpc.getByUserId(profileUserId);
        ErrorAssert.isNotNull(userDTO, BizBaseErrorEnum.PARAM_ERROR);
        UserProfileExtDTO ext = profileRpc.getProfileExt(profileUserId);

        UserProfileRespVO vo = new UserProfileRespVO();
        vo.setUserId(userDTO.getUserId());
        vo.setNickName(userDTO.getNickName());
        vo.setAvatar(userDTO.getAvatar());
        vo.setSex(userDTO.getSex());
        vo.setBornDate(userDTO.getBornDate());
        vo.setCreateTime(userDTO.getCreateTime());
        vo.setLevel(ext == null || ext.getLevel() == null ? 1 : ext.getLevel());
        vo.setExp(ext == null || ext.getExp() == null ? 0 : ext.getExp());
        vo.setNextLevelExp(UserLevelConstants.nextLevelExp(vo.getLevel()));
        vo.setFollowCnt(ext == null || ext.getFollowCnt() == null ? 0 : ext.getFollowCnt());
        vo.setFansCnt(ext == null || ext.getFansCnt() == null ? 0 : ext.getFansCnt());
        vo.setLikeReceivedCnt(ext == null || ext.getLikeReceivedCnt() == null ? 0 : ext.getLikeReceivedCnt());
        boolean isSelf = viewerId != null && viewerId.equals(profileUserId);
        vo.setIsSelf(isSelf);
        boolean iFollowHim = !isSelf && userRelationRpc.isFollow(viewerId, profileUserId);
        vo.setIsFollow(iFollowHim);
        // 相互关注：我关注了 TA 且 TA 也关注了我
        vo.setIsMutual(iFollowHim && userRelationRpc.isFollow(profileUserId, viewerId));
        return vo;
    }
}
