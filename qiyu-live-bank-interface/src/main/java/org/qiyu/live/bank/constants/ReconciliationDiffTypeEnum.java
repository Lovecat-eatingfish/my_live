package org.qiyu.live.bank.constants;

/**
 * 对账差错类型
 */
public enum ReconciliationDiffTypeEnum {

    ORDER_NO_TRADE(1, "订单有流水无（少入账）"),
    TRADE_NO_ORDER(2, "流水有订单无（多入账）"),
    AMOUNT_MISMATCH(3, "金额不平");

    private final int code;
    private final String desc;

    ReconciliationDiffTypeEnum(int code, String desc) {
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
