package org.qiyu.live.common.interfaces.topic;

/**
 * @Author idea
 * @Date: Created in 16:14 2023/5/28
 * @Description
 */
public class ImCoreServerProviderTopicNames {

    /**
     * 接收im系统发送的业务消息
     */
    public static final String QIYU_LIVE_IM_BIZ_MSG_TOPIC = "qiyu_live_im_biz_msg_topic";

    /**
     * 发送im的ack消息
     */
    public static final String QIYU_LIVE_IM_ACK_MSG_TOPIC = "qiyu_live_im_ack_msg_topic";

    /**
     * 用户初次登录im服务发送mq
     */
    public static final String IM_ONLINE_TOPIC = "im_online_topic";

    /**
     * 用户断开im服务发送mq
     */
    public static final String IM_OFFLINE_TOPIC = "im_offline_topic";

    /**
     * 主播IM断线后的关播检查（延迟消息：主播刷新浏览器不应立即关播）
     */
    public static final String LIVING_ROOM_CLOSE_CHECK = "living_room_close_check";
}
