package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.AnomalyThreshold;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.AnomalyConfigService;
import com.blastfurnace.backend.service.ThresholdResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anomaly/config")
@RequiredArgsConstructor
public class AnomalyConfigController {

    private final AnomalyConfigService anomalyConfigService;
    private final ThresholdResolverService thresholdResolverService;

    @GetMapping("/thresholds")
    public Result<List<AnomalyThreshold>> getThresholds(@RequestParam(required = false) String furnaceId) {
        return Result.success(anomalyConfigService.getThresholds(furnaceId));
    }

    @GetMapping("/thresholds/effective")
    public Result<Map<String, ThresholdResolverService.ResolvedThreshold>> getEffectiveThresholds(
            @RequestParam(required = false) String furnaceId) {
        return Result.success(thresholdResolverService.resolveAll(furnaceId));
    }

    @PostMapping("/thresholds")
    public Result<String> saveThreshold(@RequestBody AnomalyThreshold threshold) {
        anomalyConfigService.saveThreshold(threshold);
        return Result.successMsg("Configuration saved successfully");
    }
    
    @DeleteMapping("/thresholds/{id}")
    public Result<String> deleteThreshold(@PathVariable Long id) {
        anomalyConfigService.deleteThreshold(id);
        return Result.successMsg("Configuration deleted successfully");
    }
}
