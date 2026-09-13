package org.qiyu.live.msg.provider.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.qiyu.live.msg.provider.dao.po.UserDmConversationPO;

@Mapper
public interface UserDmConversationMapper extends BaseMapper<UserDmConversationPO> {

    /**
     * 原子 upsert：不存在则建行，存在则刷 last_msg、累加 unread、刷时间。
     * 发送方行 unread 增量传 0，接收方行传 1。
     */
    @Update("INSERT INTO t_user_dm_conversation(owner_uid, peer_uid, last_msg, unread_cnt, update_time) " +
            "VALUES(#{ownerUid}, #{peerUid}, #{lastMsg}, #{unreadDelta}, NOW()) " +
            "ON DUPLICATE KEY UPDATE last_msg = VALUES(last_msg), " +
            "unread_cnt = unread_cnt + VALUES(unread_cnt), update_time = NOW()")
    int upsertOnNewMsg(@Param("ownerUid") Long ownerUid, @Param("peerUid") Long peerUid,
                       @Param("lastMsg") String lastMsg, @Param("unreadDelta") int unreadDelta);
}
