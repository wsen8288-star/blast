package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.CorrelationAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/correlation")
@RequiredArgsConstructor
public class CorrelationAnalysisController {

    private final CorrelationAnalysisService correlationAnalysisService;

    /**
     * 计算相关性矩阵
     */
    @PostMapping("/matrix")
    public Result<Map<String, Object>> calculateCorrelationMatrix(
            @RequestParam String furnaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            @RequestParam(required = false, defaultValue = "pearson") String method,
            @RequestParam(required = false, defaultValue = "0") Integer maxLag,
            @RequestParam(required = false, defaultValue = "20") Integer minOverlap,
            @RequestBody List<String> parameters) {

        try {
            return Result.success(
                    correlationAnalysisService.calculateCorrelationAnalysis(
                            furnaceId, startTime, endTime, parameters, method, maxLag, minOverlap));
        } catch (Exception e) {
            return Result.error("计算相关性矩阵失败: " + e.getMessage());
        }
    }
}
