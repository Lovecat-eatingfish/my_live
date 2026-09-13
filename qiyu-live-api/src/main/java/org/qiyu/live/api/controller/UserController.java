package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IUserApiService;
import org.qiyu.live.api.error.ApiErrorEnum;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.dto.RiskCheckReqDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.common.interfaces.rpc.IRiskRpc;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.dto.UserNotifyDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.user.interfaces.rpc.INotifyRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个性化设置
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private IRiskRpc riskRpc;
    @Resource
    private IUserApiService userApiService;
    @DubboReference(check = false)
    private INotifyRpc notifyRpc;

    /**
     * 修改昵称/头像（不传的字段保持不变）
     */
    @PostMapping("/updateProfile")
    public WebResponseVO updateProfile(String nickName, String avatar) {
        ErrorAssert.isTure(StringUtils.hasText(nickName) || StringUtils.hasText(avatar),
                BizBaseErrorEnum.PARAM_ERROR);
        if (StringUtils.hasText(nickName)) {
            ErrorAssert.isTure(nickName.length() <= 20, BizBaseErrorEnum.PARAM_ERROR);
            RiskCheckRespDTO riskResp = riskRpc.checkText(
                    RiskCheckReqDTO.of(nickName, RiskConstants.SCENE_NICKNAME, QiyuRequestContext.getUserId()));
            ErrorAssert.isTure(!riskResp.isBlocked(), ApiErrorEnum.CONTENT_BLOCKED);
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(QiyuRequestContext.getUserId());
        if (StringUtils.hasText(nickName)) {
            userDTO.setNickName(nickName);
        }
        if (StringUtils.hasText(avatar)) {
            userDTO.setAvatar(avatar);
        }
        return WebResponseVO.success(userRpc.updateUserInfo(userDTO));
    }

    // ==================== 关系链（关注/粉丝）与个人主页 ====================

    /** 关注 */
    @PostMapping("/follow")
    public WebResponseVO follow(Long followUserId) {
        return WebResponseVO.success(userApiService.follow(followUserId));
    }

    /** 取关 */
    @PostMapping("/unfollow")
    public WebResponseVO unfollow(Long followUserId) {
        return WebResponseVO.success(userApiService.unfollow(followUserId));
    }

    /** 是否已关注 */
    @PostMapping("/isFollow")
    public WebResponseVO isFollow(Long targetUserId) {
        return WebResponseVO.success(userApiService.isFollow(targetUserId));
    }

    /** 我关注的人 */
    @PostMapping("/followList")
    public WebResponseVO followList(Integer page, Integer pageSize) {
        return WebResponseVO.success(userApiService.followList(pg(page), ps(pageSize)));
    }

    /** 我的粉丝 */
    @PostMapping("/fansList")
    public WebResponseVO fansList(Integer page, Integer pageSize) {
        return WebResponseVO.success(userApiService.fansList(pg(page), ps(pageSize)));
    }

    /** 个人主页聚合（targetUserId 不传=自己） */
    @PostMapping("/profile")
    public WebResponseVO profile(Long targetUserId) {
        return WebResponseVO.success(userApiService.profile(targetUserId));
    }

    // ==================== 通知中心 ====================

    /** 通知分页 */
    @PostMapping("/notify/list")
    public WebResponseVO notifyList(Integer page, Integer pageSize) {
        return WebResponseVO.success(
                notifyRpc.listNotify(QiyuRequestContext.getUserId(), pg(page), ps(pageSize)));
    }

    /** 标记已读（notifyId 不传=全部已读） */
    @PostMapping("/notify/read")
    public WebResponseVO notifyRead(Long notifyId) {
        return WebResponseVO.success(notifyRpc.markRead(QiyuRequestContext.getUserId(), notifyId));
    }

    /** 未读数 */
    @PostMapping("/notify/unreadCount")
    public WebResponseVO notifyUnreadCount() {
        return WebResponseVO.success(notifyRpc.unreadCount(QiyuRequestContext.getUserId()));
    }

    private int pg(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int ps(Integer pageSize) {
        return pageSize == null || pageSize < 1 || pageSize > 50 ? 20 : pageSize;
    }
}
