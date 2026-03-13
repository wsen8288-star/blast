package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.AnomalyRecord;
import com.blastfurnace.backend.websocket.WarningWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class WarningBroadcastService {

    private final WarningWebSocketHandler warningWebSocketHandler;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;

    private final Map<String, Long> lastBroadcastAtByKey = new ConcurrentHashMap<>();

    public void broadcastNewWarning(AnomalyRecord record) {
        if (!sysConfigService.getBoolean("alarm_global_enable", true)) {
            log.warn("Alarm push disabled by config: recordId={}", record == null ? null : record.getId());
            return;
        }
        long intervalSeconds = Math.max(0L, sysConfigService.getLong("alarm_push_interval", 0L));
        long intervalMs = intervalSeconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : intervalSeconds * 1000L;
        String key = buildKey(record);
        long now = System.currentTimeMillis();
        if (intervalMs > 0L) {
            long prev = lastBroadcastAtByKey.getOrDefault(key, 0L);
            if (prev > 0L && now - prev < intervalMs) {
                log.info("Alarm push throttled: key={} recordId={}", key, record.getId());
                return;
            }
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of(
                            "type", "NEW_WARNING",
                            "data", Map.of(
                                    "id", record.getId(),
                                    "furnaceId", record.getFurnaceId(),
                                    "parameterName", record.getParameterName(),
                                    "actualValue", record.getActualValue(),
                                    "expectedRange", record.getExpectedRange(),
                                    "level", record.getLevel(),
                                    "status", record.getStatus(),
                                    "detectionTime", String.valueOf(record.getDetectionTime()),
                                    "globalEnable", true
                            )
                    )
            );
            int sent = warningWebSocketHandler.broadcast(payload);
            if (sent > 0) {
                lastBroadcastAtByKey.put(key, now);
                log.info("Alarm push sent: recordId={} key={} sessions={}", record.getId(), key, sent);
            } else {
                log.warn("Alarm push skipped (no sessions): key={} recordId={}", key, record.getId());
            }
        } catch (Exception ignored) {
            log.warn("Alarm push failed: recordId={}", record == null ? null : record.getId(), ignored);
        }
    }

    private String buildKey(AnomalyRecord record) {
        if (record == null) return "NULL";
        String furnaceId = record.getFurnaceId() == null ? "" : record.getFurnaceId();
        String param = record.getParameterName() == null ? "" : record.getParameterName();
        String level = record.getLevel() == null ? "" : record.getLevel();
        return furnaceId + ":" + param + ":" + level;
    }
}
