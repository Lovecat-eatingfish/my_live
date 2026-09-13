package org.qiyu.live.user.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.provider.dao.mapper.IUserRelationMapper;
import org.qiyu.live.user.provider.dao.po.UserRelationPO;
import org.qiyu.live.user.provider.service.IProfileService;
import org.qiyu.live.user.provider.service.IUserRelationService;
import org.qiyu.live.user.provider.service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户关系链实现：t_user_relation 存关系，t_user_profile_ext 原子加减计数
 */
@Service
public class UserRelationServiceImpl implements IUserRelationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRelationServiceImpl.class);

    @Resource
    private IUserRelationMapper userRelationMapper;
    @Resource
    private IUserService userService;
    @Resource
    private IProfileService profileService;

    @Override
    public Boolean follow(Long userId, Long followUserId) {
        if (userId == null || followUserId == null || userId.equals(followUserId)) {
            return false;
        }
        UserRelationPO po = new UserRelationPO();
        po.setUserId(userId);
        po.setFollowUserId(followUserId);
        po.setStatus(1);
        try {
            userRelationMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：曾关注后取关则恢复，仍在关注中则幂等返回
            int updated = userRelationMapper.update(null, new LambdaUpdateWrapper<UserRelationPO>()
                    .eq(UserRelationPO::getUserId, userId)
                    .eq(UserRelationPO::getFollowUserId, followUserId)
                    .eq(UserRelationPO::getStatus, 0)
                    .set(UserRelationPO::getStatus, 1));
            if (updated <= 0) {
                return true;
            }
        }
        profileService.changeCnt(userId, "follow_cnt", 1);
        profileService.changeCnt(followUserId, "fans_cnt", 1);
        LOGGER.info("[follow] userId={} -> followUserId={}", userId, followUserId);
        return true;
    }

    @Override
    public Boolean unfollow(Long userId, Long followUserId) {
        if (userId == null || followUserId == null) {
            return false;
        }
        int updated = userRelationMapper.update(null, new LambdaUpdateWrapper<UserRelationPO>()
                .eq(UserRelationPO::getUserId, userId)
                .eq(UserRelationPO::getFollowUserId, followUserId)
                .eq(UserRelationPO::getStatus, 1)
                .set(UserRelationPO::getStatus, 0));
        if (updated <= 0) {
            return false;
        }
        profileService.changeCnt(userId, "follow_cnt", -1);
        profileService.changeCnt(followUserId, "fans_cnt", -1);
        return true;
    }

    @Override
    public Boolean isFollow(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        return userRelationMapper.selectCount(new LambdaQueryWrapper<UserRelationPO>()
                .eq(UserRelationPO::getUserId, userId)
                .eq(UserRelationPO::getFollowUserId, targetUserId)
                .eq(UserRelationPO::getStatus, 1)) > 0;
    }

    @Override
    public PageWrapper<UserDTO> pageFollow(Long userId, int page, int pageSize) {
        return pageRelation(userId, page, pageSize, true);
    }

    @Override
    public PageWrapper<UserDTO> pageFans(Long userId, int page, int pageSize) {
        return pageRelation(userId, page, pageSize, false);
    }

    private PageWrapper<UserDTO> pageRelation(Long userId, int page, int pageSize, boolean followDirection) {
        PageWrapper<UserDTO> wrapper = new PageWrapper<>();
        Page<UserRelationPO> poPage = userRelationMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UserRelationPO>()
                        .eq(followDirection ? UserRelationPO::getUserId : UserRelationPO::getFollowUserId, userId)
                        .eq(UserRelationPO::getStatus, 1)
                        .orderByDesc(UserRelationPO::getId));
        List<Long> targetIds = poPage.getRecords().stream()
                .map(po -> followDirection ? po.getFollowUserId() : po.getUserId())
                .collect(Collectors.toList());
        if (targetIds.isEmpty()) {
            wrapper.setList(List.of());
            return wrapper;
        }
        Map<Long, UserDTO> userMap = userService.batchQueryUserInfo(targetIds);
        List<UserDTO> userList = targetIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        wrapper.setList(userList);
        wrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return wrapper;
    }

    @Override
    public Integer countFollow(Long userId) {
        return profileService.getOrInitExt(userId).getFollowCnt();
    }

    @Override
    public Integer countFans(Long userId) {
        return profileService.getOrInitExt(userId).getFansCnt();
    }
}
