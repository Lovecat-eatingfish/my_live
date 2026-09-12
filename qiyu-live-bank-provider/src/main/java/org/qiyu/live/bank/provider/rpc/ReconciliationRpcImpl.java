package org.qiyu.live.bank.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.bank.dto.ReconciliationDetailDTO;
import org.qiyu.live.bank.interfaces.IReconciliationRpc;
import org.qiyu.live.bank.provider.dao.po.ReconciliationDetailPO;
import org.qiyu.live.bank.provider.service.IReconciliationService;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;

import java.text.SimpleDateFormat;
import java.util.List;

@DubboService
public class ReconciliationRpcImpl implements IReconciliationRpc {

    @Resource
    private IReconciliationService reconciliationService;

    @Override
    public PageWrapper<ReconciliationDetailDTO> listDetails(String bizDate, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 || pageSize > 100 ? 20 : pageSize;
        List<ReconciliationDetailPO> pos = reconciliationService.listDetails(bizDate, p, size);
        PageWrapper<ReconciliationDetailDTO> pageWrapper = new PageWrapper<>();
        List<ReconciliationDetailDTO> dtoList = pos.stream().map(po -> {
            ReconciliationDetailDTO dto = ConvertBeanUtils.convert(po, ReconciliationDetailDTO.class);
            if (po.getBizDate() != null) {
                dto.setBizDate(new SimpleDateFormat("yyyy-MM-dd").format(po.getBizDate()));
            }
            if (po.getCreateTime() != null) {
                dto.setCreateTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(po.getCreateTime()));
            }
            return dto;
        }).toList();
        pageWrapper.setList(dtoList);
        pageWrapper.setHasNext(dtoList.size() == size);
        return pageWrapper;
    }

    @Override
    public int triggerReconcile(String bizDate) {
        return reconciliationService.reconcileDay(bizDate);
    }
}
