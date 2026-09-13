package org.qiyu.live.user.provider.service;

import org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO;
import org.qiyu.live.user.dto.UserProfileExtDTO;
import org.qiyu.live.user.provider.dao.po.UserProfileExtPO;

import java.util.List;

/**
 * 用户主页扩展服务（计数/等级/经验）
 */
public interface IProfileService {

    UserProfileExtDTO getProfileExt(Long userId);

    List<UserProfileExtDTO> batchQueryProfileExt(List<Long> userIds);

    /** 查询扩展记录，不存在时自动初始化（level=1 exp=0 全 0 计数） */
    UserProfileExtPO getOrInitExt(Long userId);

    /** 计数原子加减（cntColumn: follow_cnt / fans_cnt / like_received_cnt） */
    void changeCnt(Long userId, String cntColumn, int delta);

    /** 经验值结算：DB 为准累加，跨级时刷新等级缓存并广播 5570 */
    void addExp(UserExpChangeMqDTO dto);
}
