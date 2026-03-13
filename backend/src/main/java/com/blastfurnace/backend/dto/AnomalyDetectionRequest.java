package com.blastfurnace.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnomalyDetectionRequest {
    private String furnaceId;
    private String detectionMode; // realtime, batch, scheduled
    private String algorithm; // THRESHOLD, Z_SCORE, IQR, ALL
    private List<String> params;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer batchSize;
    private Integer scheduleIntervalSeconds;
}
