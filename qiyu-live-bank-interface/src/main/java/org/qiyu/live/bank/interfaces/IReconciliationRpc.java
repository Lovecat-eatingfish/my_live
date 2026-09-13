package org.qiyu.live.bank.interfaces;

import org.qiyu.live.bank.dto.ReconciliationDetailDTO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;

/**
 * 对账RPC：T+1 双向核对充值订单与金币流水，差错落入 t_reconciliation_detail
 */
public interface IReconciliationRpc {

    /**
     * 分页查询差错明细
     *
     * @param bizDate  对账业务日期 yyyy-MM-dd，为空则查全部
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     */
    PageWrapper<ReconciliationDetailDTO> listDetails(String bizDate, Integer page, Integer pageSize);

    /**
     * 手动触发对账（默认定时任务每天凌晨1点核对前一天数据）
     *
     * @param bizDate 对账业务日期 yyyy-MM-dd
     * @return 本日发现差错条数
     */
    int triggerReconcile(String bizDate);

    /**
     * 标记差错已处理（运营台用）
     */
    boolean markProcessed(Long id, String remark);
}
