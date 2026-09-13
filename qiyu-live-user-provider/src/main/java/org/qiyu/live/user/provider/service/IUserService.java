package org.qiyu.live.user.provider.service;

import org.qiyu.live.user.dto.UserDTO;

import java.util.List;
import java.util.Map;

/**
 * @Author idea
 * @Date: Created in 16:40 2023/5/12
 * @Description
 */
public interface IUserService {

    /**
     * 根据用户id进行查询
     *
     * @param userId
     * @return
     */
    UserDTO getByUserId(Long userId);

    /**
     * 用户信息更新
     *
     * @param userDTO
     * @return
     */
    boolean updateUserInfo(UserDTO userDTO);

    /**
     * 插入用户信息
     *
     * @param userDTO
     * @return
     */
    boolean insertOne(UserDTO userDTO);

    /**
     * 批量查询用户信息
     *
     * @param userIdList
     * @return
     */
    Map<Long,UserDTO> batchQueryUserInfo(List<Long> userIdList);

    /**
     * 管理端用户列表（keyword 模糊匹配昵称，分页）
     */
    java.util.List<UserDTO> listUsers(String keyword, int page, int pageSize);

    /**
     * 封禁/禁言：写 t_user_ban 记录 + 写运行时 Redis key（网关封号校验、弹幕禁言校验）
     */
    boolean banUser(org.qiyu.live.user.dto.UserBanDTO banDTO);

    /**
     * 解除封禁/禁言（解除该用户当前生效中的 type 类型记录 + 删 Redis key）
     */
    boolean unbanUser(Long userId, int type);
}