package com.blastfurnace.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PreprocessingRequest {
    private String runId;
    private String fileId;
    private String importPreviewId;
    private List<Map<String, Object>> data;
    private String missingValueStrategy;
    private List<String> outlierDetectionMethods;
    private String outlierHandlingStrategy;
    private String normalizationMethod;
    private List<String> featureSelectionMethods;
    private String idColumn;
    private String processParameterRangeStrategy;
    private List<String> missingSentinelValues;
}
