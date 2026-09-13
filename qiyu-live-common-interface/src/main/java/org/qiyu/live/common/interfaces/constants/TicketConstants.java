package org.qiyu.live.common.interfaces.constants;

/**
 * 付费直播间常量（api 写、stream/api 读，全局固定前缀）
 */
public class TicketConstants {

    /** 购票标记 key：{roomId}:{userId}，值=1，TTL 12h 兜底 */
    public static final String ROOM_TICKET_KEY_PREFIX = "qiyu:live:ticket:";

    public static final int TICKET_TTL_HOURS = 12;
}
