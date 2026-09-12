package org.qiyu.live.bank.provider.job;

import jakarta.annotation.Resource;
import org.qiyu.live.bank.provider.service.IReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * T+1 对账定时任务：每天凌晨核对前一自然日的充值订单与金币流水
 */
@Component
public class ReconciliationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private IReconciliationService reconciliationService;

    @Scheduled(cron = "${qiyu.recon.cron:0 30 1 * * ?}")
    public void reconcileYesterday() {
        String bizDate = LocalDate.now().minusDays(1).format(FORMATTER);
        try {
            int diffs = reconciliationService.reconcileDay(bizDate);
            LOGGER.info("[ReconciliationJob] bizDate={} diffs={}", bizDate, diffs);
        } catch (Exception e) {
            LOGGER.error("[ReconciliationJob] bizDate={} failed", bizDate, e);
        }
    }
}
