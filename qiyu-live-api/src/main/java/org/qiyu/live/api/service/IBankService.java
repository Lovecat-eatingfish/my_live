package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.req.PayProductReqVO;
import org.qiyu.live.api.vo.resp.PayProductRespVO;
import org.qiyu.live.api.vo.resp.PayProductVO;
import org.qiyu.live.bank.dto.ReconciliationDetailDTO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;

/**
 * @Author idea
 * @Date: Created in 08:26 2023/8/17
 * @Description
 */
public interface IBankService {

    /**
     * 查询相关的产品列表信息
     *
     * @param type
     * @return
     */
    PayProductVO products(Integer type);

    /**
     * 发起支付
     *
     * @param payProductReqVO
     * @return
     */
    PayProductRespVO payProduct(PayProductReqVO payProductReqVO);

    /**
     * 查询当前登录用户的金币余额
     */
    Integer getBalance();

    /**
     * 分页查询对账差错明细
     */
    PageWrapper<ReconciliationDetailDTO> reconList(String bizDate, Integer page, Integer pageSize);

    /**
     * 手动触发对账（不传日期默认核对昨天）
     */
    int reconTrigger(String bizDate);
}
