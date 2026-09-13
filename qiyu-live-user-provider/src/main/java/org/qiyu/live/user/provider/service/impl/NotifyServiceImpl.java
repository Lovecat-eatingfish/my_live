package org.qiyu.live.user.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.user.dto.UserNotifyDTO;
import org.qiyu.live.user.provider.dao.mapper.IUserNotifyMapper;
import org.qiyu.live.user.provider.dao.po.UserNotifyPO;
import org.qiyu.live.user.provider.service.INotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 站内通知实现（t_user_notify）
 */
@Service
public class NotifyServiceImpl implements INotifyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyServiceImpl.class);

    @Resource
    private IUserNotifyMapper userNotifyMapper;

    @Override
    public PageWrapper<UserNotifyDTO> listNotify(Long userId, int page, int pageSize) {
        PageWrapper<UserNotifyDTO> wrapper = new PageWrapper<>();
        Page<UserNotifyPO> poPage = userNotifyMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UserNotifyPO>()
                        .eq(UserNotifyPO::getUserId, userId)
                        .orderByDesc(UserNotifyPO::getId));
        List<UserNotifyDTO> list = ConvertBeanUtils.convertList(poPage.getRecords(), UserNotifyDTO.class);
        wrapper.setList(list == null ? Collections.emptyList() : list);
        wrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return wrapper;
    }

    @Override
    public Integer unreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        return Math.toIntExact(userNotifyMapper.selectCount(new LambdaQueryWrapper<UserNotifyPO>()
                .eq(UserNotifyPO::getUserId, userId)
                .eq(UserNotifyPO::getIsRead, 0)));
    }

    @Override
    public Boolean markRead(Long userId, Long notifyId) {
        if (userId == null) {
            return false;
        }
        return userNotifyMapper.update(null, new LambdaUpdateWrapper<UserNotifyPO>()
                .eq(UserNotifyPO::getUserId, userId)
                .eq(UserNotifyPO::getIsRead, 0)
                .eq(notifyId != null, UserNotifyPO::getId, notifyId)
                .set(UserNotifyPO::getIsRead, 1)) > 0;
    }

    @Override
    public Boolean sendNotify(UserNotifyDTO notifyDTO) {
        if (notifyDTO == null || notifyDTO.getUserId() == null) {
            return false;
        }
        try {
            UserNotifyPO po = ConvertBeanUtils.convert(notifyDTO, UserNotifyPO.class);
            po.setId(null);
            po.setIsRead(0);
            po.setCreateTime(new Date());
            userNotifyMapper.insert(po);
            return true;
        } catch (Exception e) {
            LOGGER.error("[sendNotify] error, userId={}", notifyDTO.getUserId(), e);
            return false;
        }
    }
}
