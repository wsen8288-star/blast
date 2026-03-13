package com.blastfurnace.backend.dto;

import java.util.List;
import java.util.Map;

public class PreprocessingResponse {
    private List<Map<String, Object>> processedData;
    private Map<String, Object> stats;

    public List<Map<String, Object>> getProcessedData() {
        return processedData;
    }

    public void setProcessedData(List<Map<String, Object>> processedData) {
        this.processedData = processedData;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }
}
