package org.qiyu.live.bank.provider.service;

import org.qiyu.live.bank.provider.dao.po.ReconciliationDetailPO;

import java.util.List;

/**
 * 对账服务：核对指定业务日期的充值订单与金币入账流水
 */
public interface IReconciliationService {

    /**
     * 对账指定日期（yyyy-MM-dd），先清空当日差错记录再重新核对，可重复执行
     *
     * @return 本日发现差错条数
     */
    int reconcileDay(String bizDate);

    /**
     * 分页查询差错明细
     *
     * @param bizDate 为空则查全部日期
     */
    List<ReconciliationDetailPO> listDetails(String bizDate, int page, int pageSize);

    /**
     * 标记差错已处理
     */
    boolean markProcessed(Long id, String remark);
}
