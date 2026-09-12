package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
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

    /**
     * 修改昵称/头像（不传的字段保持不变）
     */
    @PostMapping("/updateProfile")
    public WebResponseVO updateProfile(String nickName, String avatar) {
        ErrorAssert.isTure(StringUtils.hasText(nickName) || StringUtils.hasText(avatar),
                BizBaseErrorEnum.PARAM_ERROR);
        UserDTO userDTO = new UserDTO();
        userDTO.setUserId(QiyuRequestContext.getUserId());
        if (StringUtils.hasText(nickName)) {
            ErrorAssert.isTure(nickName.length() <= 20, BizBaseErrorEnum.PARAM_ERROR);
            userDTO.setNickName(nickName);
        }
        if (StringUtils.hasText(avatar)) {
            userDTO.setAvatar(avatar);
        }
        return WebResponseVO.success(userRpc.updateUserInfo(userDTO));
    }
}
