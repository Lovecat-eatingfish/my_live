package org.qiyu.live.msg.provider.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.DmConversationDTO;
import org.qiyu.live.common.interfaces.dto.DmMessageDTO;
import org.qiyu.live.common.interfaces.rpc.IDmRpc;
import org.qiyu.live.msg.provider.dao.mapper.UserDmConversationMapper;
import org.qiyu.live.msg.provider.dao.mapper.UserDmMessageMapper;
import org.qiyu.live.msg.provider.dao.po.UserDmConversationPO;
import org.qiyu.live.msg.provider.dao.po.UserDmMessagePO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 私信查询 RPC 实现
 */
@DubboService
public class DmRpcImpl implements IDmRpc {

    private static final int DEFAULT_HISTORY_SIZE = 50;

    @Resource
    private UserDmMessageMapper dmMessageMapper;
    @Resource
    private UserDmConversationMapper dmConversationMapper;

    @Override
    public List<DmConversationDTO> listConversations(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserDmConversationPO> pos = dmConversationMapper.selectList(
                new LambdaQueryWrapper<UserDmConversationPO>()
                        .eq(UserDmConversationPO::getOwnerUid, userId)
                        .orderByDesc(UserDmConversationPO::getUpdateTime)
                        .last("limit 100"));
        return pos.stream().map(po -> {
            DmConversationDTO dto = new DmConversationDTO();
            dto.setPeerUid(po.getPeerUid());
            dto.setLastMsg(po.getLastMsg());
            dto.setUnreadCnt(po.getUnreadCnt());
            dto.setUpdateTime(po.getUpdateTime());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public List<DmMessageDTO> listHistory(Long userId, Long peerUid, int size) {
        if (userId == null || peerUid == null) {
            return Collections.emptyList();
        }
        int limit = Math.min(Math.max(size, 1), 200);
        //取最新 limit 条后反转为正序
        List<UserDmMessagePO> pos = dmMessageMapper.selectList(
                new LambdaQueryWrapper<UserDmMessagePO>()
                        .and(w -> w.eq(UserDmMessagePO::getFromUid, userId).eq(UserDmMessagePO::getToUid, peerUid))
                        .or(w -> w.eq(UserDmMessagePO::getFromUid, peerUid).eq(UserDmMessagePO::getToUid, userId))
                        .eq(UserDmMessagePO::getStatus, 1)
                        .orderByDesc(UserDmMessagePO::getId)
                        .last("limit " + limit));
        Collections.reverse(pos);
        return pos.stream().map(po -> {
            DmMessageDTO dto = new DmMessageDTO();
            dto.setMsgId(po.getId());
            dto.setFromUid(po.getFromUid());
            dto.setToUid(po.getToUid());
            dto.setContent(po.getContent());
            dto.setCreateTime(po.getCreateTime());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public Boolean markRead(Long userId, Long peerUid) {
        if (userId == null || peerUid == null) {
            return false;
        }
        return dmConversationMapper.update(null,
                new LambdaUpdateWrapper<UserDmConversationPO>()
                        .eq(UserDmConversationPO::getOwnerUid, userId)
                        .eq(UserDmConversationPO::getPeerUid, peerUid)
                        .set(UserDmConversationPO::getUnreadCnt, 0)) >= 0;
    }

    @Override
    public Integer unreadTotal(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<UserDmConversationPO> pos = dmConversationMapper.selectList(
                new LambdaQueryWrapper<UserDmConversationPO>()
                        .eq(UserDmConversationPO::getOwnerUid, userId)
                        .gt(UserDmConversationPO::getUnreadCnt, 0)
                        .select(UserDmConversationPO::getUnreadCnt));
        return pos.stream().mapToInt(po -> po.getUnreadCnt() == null ? 0 : po.getUnreadCnt()).sum();
    }
}
