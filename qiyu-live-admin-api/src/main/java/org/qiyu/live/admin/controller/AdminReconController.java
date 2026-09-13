package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.bank.dto.ReconciliationDetailDTO;
import org.qiyu.live.bank.interfaces.IReconciliationRpc;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对账中心：差错查询 / 手动对账 / 标记处理
 */
@RestController
@RequestMapping("/recon")
public class AdminReconController {

    @DubboReference(check = false)
    private IReconciliationRpc reconciliationRpc;

    @PostMapping("/list")
    public WebResponseVO list(String bizDate, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        PageWrapper<ReconciliationDetailDTO> wrapper = reconciliationRpc.listDetails(bizDate, p, ps);
        return WebResponseVO.success(java.util.Map.of("list", wrapper.getList(), "hasNext", wrapper.isHasNext()));
    }

    @PostMapping("/trigger")
    public WebResponseVO trigger(String bizDate) {
        String target = (bizDate == null || bizDate.isEmpty())
                ? java.time.LocalDate.now().minusDays(1).toString() : bizDate;
        return WebResponseVO.success(reconciliationRpc.triggerReconcile(target));
    }

    @PostMapping("/mark")
    public WebResponseVO mark(Long id, String remark) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(reconciliationRpc.markProcessed(id, remark));
    }
}
