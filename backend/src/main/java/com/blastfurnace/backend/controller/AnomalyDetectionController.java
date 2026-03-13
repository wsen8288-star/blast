package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.AnomalyDetectionRequest;
import com.blastfurnace.backend.model.AnomalyRecord;
import com.blastfurnace.backend.model.AnomalyStatus;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anomaly")
@RequiredArgsConstructor
public class AnomalyDetectionController {

    private final AnomalyDetectionService anomalyDetectionService;

    @PostMapping("/detect")
    public Result<Map<String, Object>> detectAnomalies(@RequestBody AnomalyDetectionRequest request) {
        return Result.success(
                anomalyDetectionService.detectAnomalies(
                        request.getFurnaceId(),
                        request.getParams(),
                        request.getAlgorithm(),
                        request.getDetectionMode(),
                        request.getStartTime(),
                        request.getEndTime(),
                        request.getBatchSize()
                ),
                "Detection executed"
        );
    }

    @PostMapping("/schedule/start")
    public Result<Map<String, Object>> startSchedule(@RequestBody AnomalyDetectionRequest request) {
        return Result.success(
                anomalyDetectionService.startScheduledDetection(
                        request.getFurnaceId(),
                        request.getParams(),
                        request.getAlgorithm(),
                        request.getScheduleIntervalSeconds(),
                        request.getBatchSize()
                ),
                "Schedule started"
        );
    }

    @PostMapping("/schedule/stop")
    public Result<Map<String, Object>> stopSchedule() {
        return Result.success(anomalyDetectionService.stopScheduledDetection(), "Schedule stopped");
    }

    @GetMapping("/schedule/status")
    public Result<Map<String, Object>> getScheduleStatus() {
        return Result.success(anomalyDetectionService.getScheduleStatus(), "Schedule status");
    }

    @GetMapping("/realtime")
    public Result<Page<AnomalyRecord>> getRealtimeAnomalies(
            @RequestParam(required = false) String furnaceId,
            @RequestParam(required = false) String status,
            @PageableDefault(sort = "detectionTime", direction = Sort.Direction.DESC) Pageable pageable) {

        List<Integer> statusList = Arrays.asList(0, 1);
        if (status != null && !status.isEmpty()) {
            statusList = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .map(this::parseStatus)
                    .toList();
        }

        return Result.success(anomalyDetectionService.getAnomalies(
                furnaceId, 
                statusList, 
                null, 
                null, 
                null, 
                null, 
                pageable
        ));
    }

    @GetMapping("/history")
    public Result<Page<AnomalyRecord>> getHistoryAnomalies(
            @RequestParam(required = false) String furnaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String parameterName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @PageableDefault(sort = "detectionTime", direction = Sort.Direction.DESC) Pageable pageable) {

        return Result.success(anomalyDetectionService.getAnomalies(
                furnaceId, 
                status != null && !status.isEmpty() ? Collections.singletonList(parseStatus(status)) : null,
                level, 
                parameterName,
                startTime, 
                endTime, 
                pageable
        ));
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> getStatistics(@RequestParam(required = false) String furnaceId) {
        return Result.success(anomalyDetectionService.getStatistics(furnaceId));
    }

    @PutMapping("/{id}/handle")
    public Result<String> handleAnomaly(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Long handlerUser = null;
        if (body.get("handlerUser") != null && !body.get("handlerUser").isBlank()) {
            handlerUser = Long.parseLong(body.get("handlerUser").trim());
        }
        String handlerContent = body.get("handlerContent");
        Integer status = body.get("status") != null && !body.get("status").isBlank()
                ? parseStatus(body.get("status"))
                : AnomalyStatus.CLOSED.getCode();

        anomalyDetectionService.handleAnomaly(id, handlerUser, handlerContent, status);
        return Result.successMsg("Anomaly handled successfully");
    }

    @GetMapping("/charts")
    public Result<Map<String, Object>> getChartData() {
        return Result.success(anomalyDetectionService.getChartData());
    }

    private Integer parseStatus(String raw) {
        AnomalyStatus status = AnomalyStatus.fromRaw(raw);
        if (status != null) {
            return status.getCode();
        }
        return Integer.parseInt(String.valueOf(raw));
    }
}
