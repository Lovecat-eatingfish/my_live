package org.qiyu.live.bank.interfaces;

import org.qiyu.live.bank.dto.AccountTradeReqDTO;
import org.qiyu.live.bank.dto.AccountTradeRespDTO;
import org.qiyu.live.bank.dto.QiyuCurrencyAccountDTO;

/**
 * @Author idea
 * @Date: Created in 10:28 2023/8/6
 * @Description
 */
public interface IQiyuCurrencyAccountRpc {

    /**
     * 增加虚拟币
     *
     * @param userId
     * @param num
     */
    void incr(long userId,int num);

    /**
     * 扣减虚拟币
     *
     * @param userId
     * @param num
     */
    void decr(long userId,int num);

    /**
     * 查询余额
     *
     * @param userId
     * @return
     */
    Integer getBalance(long userId);


    /**
     * 专门给送礼业务调用的扣减库存逻辑
     *
     * @param accountTradeReqDTO
     */
    AccountTradeRespDTO consumeForSendGift(AccountTradeReqDTO accountTradeReqDTO);

    /**
     * 红包发送扣费（余额不足返回失败，流水类型记为红包支出）
     */
    AccountTradeRespDTO consumeForRedPacket(long userId, int num);

    /**
     * 红包结算退还剩余金额给主播（流水类型记为红包退还）
     */
    void incrForRedPacketRefund(long userId, int num);

}
