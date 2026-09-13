package org.qiyu.live.common.interfaces.rpc;

import org.qiyu.live.common.interfaces.dto.DmConversationDTO;
import org.qiyu.live.common.interfaces.dto.DmMessageDTO;

import java.util.List;

/**
 * 私信 RPC（实现挂 msg-provider，与 IRiskRpc 同策略）
 */
public interface IDmRpc {

    /**
     * 会话列表（按最近消息时间倒序）
     */
    List<DmConversationDTO> listConversations(Long userId);

    /**
     * 与某人的历史消息（返回最新 size 条，按时间正序返回）
     */
    List<DmMessageDTO> listHistory(Long userId, Long peerUid, int size);

    /**
     * 打开会话：清零与 peer 的未读数
     */
    Boolean markRead(Long userId, Long peerUid);

    /**
     * 全部会话未读总数
     */
    Integer unreadTotal(Long userId);
}
