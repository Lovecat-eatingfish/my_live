package org.qiyu.live.bank.constants;

/**
 * @Author idea
 * @Date: Created in 21:52 2023/8/7
 * @Description
 */
public enum TradeTypeEnum {

    SEND_GIFT_TRADE(0,"送礼物交易"),
    LIVING_RECHARGE(1,"直播间充值"),
    RED_PACKET_SEND(2,"红包支出"),
    RED_PACKET_REFUND(3,"红包退还");

    int code;
    String desc;

    TradeTypeEnum(int code, String desc) {
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
