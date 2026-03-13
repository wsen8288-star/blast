package com.blastfurnace.backend.service;

import com.blastfurnace.backend.repository.AuditLogRepository;
import com.blastfurnace.backend.repository.OperationLogRepository;
import com.blastfurnace.backend.repository.SystemRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogCleanupService {
    private final SysConfigService sysConfigService;
    private final SystemRequestLogRepository systemRequestLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final OperationLogRepository operationLogRepository;

    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanupExpiredLogs() {
        int retentionDays = sysConfigService.getInt("system_log_retention_days", 30);
        if (retentionDays <= 0) {
            return;
        }
        long cutoffMs = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
        Date cutoff = new Date(cutoffMs);
        long requestDeleted = systemRequestLogRepository.deleteByCreatedAtBefore(cutoff);
        long auditDeleted = auditLogRepository.deleteByTimeBefore(cutoff);
        long operationDeleted = operationLogRepository.deleteByExecutionTimeBefore(cutoff);
        log.info("系统日志清理完成 retentionDays={}, requestDeleted={}, auditDeleted={}, operationDeleted={}",
                retentionDays, requestDeleted, auditDeleted, operationDeleted);
    }
}

