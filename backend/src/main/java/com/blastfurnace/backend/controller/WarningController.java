package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.WarningHandleRequestDTO;
import com.blastfurnace.backend.dto.WarningBatchHandleRequestDTO;
import com.blastfurnace.backend.dto.WarningStatsDTO;
import com.blastfurnace.backend.model.AnomalyRecord;
import com.blastfurnace.backend.model.AnomalyStatus;
import com.blastfurnace.backend.repository.AnomalyRecordRepository;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.AnomalyDetectionService;
import com.blastfurnace.backend.websocket.WarningWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/warning")
@RequiredArgsConstructor
public class WarningController {

    private final AnomalyDetectionService anomalyDetectionService;
    private final AnomalyRecordRepository anomalyRecordRepository;
    private final WarningWebSocketHandler warningWebSocketHandler;
    private final ObjectMapper objectMapper;
    
    @GetMapping("/settings")
    public Result<String> getWarningSettings() {
        return Result.success("获取预警设置成功");
    }
    
    @PutMapping("/settings")
    public Result<String> updateWarningSettings(@RequestBody Object data) {
        return Result.success("更新预警设置成功");
    }
    
    @GetMapping("/list")
    public Result<Page<AnomalyRecord>> getWarningList(
            @RequestParam(required = false) String furnaceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String level,
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "status", direction = Sort.Direction.ASC),
                    @SortDefault(sort = "detectionTime", direction = Sort.Direction.DESC)
            }) Pageable pageable) {
        List<Integer> statuses = status == null ? null : List.of(status);
        return Result.success(anomalyDetectionService.getAnomalies(furnaceId, statuses, level, null, null, null, pageable));
    }
    
    @PutMapping("/handle")
    public Result<String> handleWarning(@RequestBody WarningHandleRequestDTO request) {
        if (request == null || request.getId() == null) {
            return Result.error("id不能为空");
        }
        Integer status = request.getStatus() == null ? AnomalyStatus.CLOSED.getCode() : request.getStatus();
        AnomalyRecord updated = anomalyDetectionService.handleAnomaly(
                request.getId(),
                request.getHandlerUser(),
                request.getHandlerContent(),
                status
        );
        broadcastStatusChanged(updated);
        return Result.successMsg("处理预警成功");
    }

    @PutMapping("/handle/batch")
    public Result<Map<String, Object>> handleWarningBatch(@RequestBody WarningBatchHandleRequestDTO request) {
        List<Long> ids = request == null || request.getIds() == null ? List.of() : request.getIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Result.error("ids不能为空");
        }
        Integer status = request.getStatus() == null ? AnomalyStatus.RESOLVED.getCode() : request.getStatus();
        boolean allowSevere = Boolean.TRUE.equals(request.getAllowSevere());
        int success = 0;
        List<Long> skipped = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                AnomalyRecord record = anomalyRecordRepository.findById(id).orElse(null);
                if (record == null) {
                    failed.add(Map.of("id", id, "reason", "记录不存在"));
                    continue;
                }
                if (!allowSevere && "严重".equals(String.valueOf(record.getLevel()))) {
                    skipped.add(id);
                    continue;
                }
                AnomalyRecord updated = anomalyDetectionService.handleAnomaly(
                        id,
                        request.getHandlerUser(),
                        request.getHandlerContent(),
                        status
                );
                broadcastStatusChanged(updated);
                success++;
            } catch (Exception ex) {
                failed.add(Map.of("id", id, "reason", ex.getMessage() == null ? "处理失败" : ex.getMessage()));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", ids.size());
        payload.put("success", success);
        payload.put("skipped", skipped);
        payload.put("failed", failed);
        payload.put("status", status);
        return Result.success(payload, "批量处理完成");
    }

    @GetMapping("/stats")
    public Result<WarningStatsDTO> getWarningStats(
            @RequestParam(required = false) String furnaceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String level) {
        long tipCount = countByLevel(furnaceId, status, level, "提示");
        long warningCount = countByLevel(furnaceId, status, level, "警告");
        long severeCount = countByLevel(furnaceId, status, level, "严重");
        return Result.success(new WarningStatsDTO(tipCount, warningCount, severeCount));
    }

    @PostMapping("/push-test")
    public Result<String> pushTest() {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of(
                            "type", "NEW_WARNING",
                            "data", Map.of(
                                    "id", 0,
                                    "furnaceId", "BF-TEST",
                                    "parameterName", "pressure",
                                    "actualValue", 999,
                                    "expectedRange", "TEST",
                                    "level", "测试",
                                    "status", 0,
                                    "detectionTime", java.time.LocalDateTime.now().toString(),
                                    "globalEnable", true
                            )
                    )
            );
            int sent = warningWebSocketHandler.broadcast(payload);
            return Result.successMsg("push-test sent to " + sent + " session(s)");
        } catch (Exception e) {
            return Result.error("push-test failed: " + e.getMessage());
        }
    }

    private long countByLevel(String furnaceId, Integer status, String selectedLevel, String targetLevel) {
        Specification<AnomalyRecord> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (furnaceId != null && !furnaceId.isBlank()) {
                predicates.add(cb.equal(root.get("furnaceId"), furnaceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (selectedLevel != null && !selectedLevel.isBlank()) {
                predicates.add(cb.equal(root.get("level"), selectedLevel));
            }
            predicates.add(cb.equal(root.get("level"), targetLevel));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return anomalyRecordRepository.count(spec);
    }

    private void broadcastStatusChanged(AnomalyRecord record) {
        if (record == null) return;
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of(
                            "type", "WARNING_STATUS_CHANGED",
                            "data", Map.of(
                                    "id", record.getId(),
                                    "status", record.getStatus(),
                                    "handlerUser", record.getHandlerUser(),
                                    "handlerContent", record.getHandlerContent(),
                                    "handleTime", String.valueOf(record.getHandleTime())
                            )
                    )
            );
            warningWebSocketHandler.broadcast(payload);
        } catch (Exception ignored) {
        }
    }
}
