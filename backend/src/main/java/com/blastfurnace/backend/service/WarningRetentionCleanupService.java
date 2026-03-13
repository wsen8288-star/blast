package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AnomalyStatus;
import com.blastfurnace.backend.repository.AnomalyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarningRetentionCleanupService {
    private final SysConfigService sysConfigService;
    private final AnomalyRecordRepository anomalyRecordRepository;

    @Scheduled(cron = "0 25 3 * * ?")
    public void cleanupExpiredWarnings() {
        int retentionDays = sysConfigService.getInt("warning_retention_days", 30);
        if (retentionDays < 1) {
            retentionDays = 1;
        }

        ZoneId zoneId = resolveSystemZoneId();
        LocalDateTime cutoff = LocalDateTime.now(zoneId).minusDays(retentionDays);

        long deleted = anomalyRecordRepository.deleteByDetectionTimeBeforeAndStatusIn(
                cutoff,
                List.of(AnomalyStatus.RESOLVED.getCode(), AnomalyStatus.CLOSED.getCode())
        );
        log.info("预警数据清理完成 retentionDays={}, cutoff={}, deleted={}", retentionDays, cutoff, deleted);
    }

    private ZoneId resolveSystemZoneId() {
        String zone = sysConfigService.getString("system_timezone", "Asia/Shanghai");
        try {
            return ZoneId.of(zone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
