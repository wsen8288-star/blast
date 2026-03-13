package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class ReportGenerationResponseDTO {
    private Long reportId;
    private String fileName;
    private String reportFormat;
    private String effectiveTimeGrain;
    private String storagePath;
    private String downloadUrl;
}
