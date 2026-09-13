package org.qiyu.live.im.router.interfaces.constants;

/**
 * @Author idea
 * @Date: Created in 22:47 2023/7/14
 * @Description
 */
public enum ImMsgBizCodeEnum {

    LIVING_ROOM_IM_CHAT_MSG_BIZ(5555,"直播间im聊天消息"),
    LIVING_ROOM_SEND_GIFT_SUCCESS(5556,"送礼成功"),
    LIVING_ROOM_SEND_GIFT_FAIL(5557,"送礼失败"),
    LIVING_ROOM_PK_SEND_GIFT_SUCCESS(5558,"pk送礼成功"),
    LIVING_ROOM_PK_ONLINE(5559,"pk连线"),
    RED_PACKET_RAIN_SEND(5560,"红包雨发送"),
    RED_PACKET_RECEIVE_SUCCESS(5561,"红包领取成功"),
    ORDER_STATUS_CHANGE(5562,"订单状态变更"),
    LIVING_STREAM_STATUS_CHANGE(5563,"推流状态变更"),
    LIVING_RECORD_DONE(5564,"直播回放生成"),
    LIVING_ROOM_CLOSE(5565,"直播间关闭"),
    RISK_MSG_BLOCKED(5566,"消息被风控拦截/禁言提示"),
    OPEN_LIVING_PUSH(5567,"关注的主播开播推送"),
    LEVEL_UP_EFFECT(5570,"用户升级特效");

    int code;
    String desc;

    ImMsgBizCodeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
