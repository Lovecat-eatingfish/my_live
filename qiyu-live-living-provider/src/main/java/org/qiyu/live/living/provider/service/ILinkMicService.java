package org.qiyu.live.living.provider.service;

/**
 * 连麦状态机服务（信令 5572）
 */
public interface ILinkMicService {

    /** 主播邀请观众连麦，返回 linkMicId；同一观众有待处理邀请或已在连麦则拒绝 */
    Long invite(Integer roomId, Long guestUserId);

    /** 被邀请者接受：置连麦中 + 生成第二路推流地址 + 全房间广播 start */
    boolean accept(Long linkMicId);

    /** 挂断（主播或观众均可）：置结束 + 全房间广播 stop */
    boolean hangUp(Integer roomId);
}
