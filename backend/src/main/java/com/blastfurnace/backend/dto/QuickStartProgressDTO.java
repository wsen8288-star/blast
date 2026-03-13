package com.blastfurnace.backend.dto;

import lombok.Data;

import java.util.Date;

@Data
public class QuickStartProgressDTO {
    private String runId;
    private Integer windowHours;
    private Integer current;
    private Date updatedAt;
    private Step collect;
    private Step preprocess;
    private Step train;
    private Step optimize;

    @Data
    public static class Step {
        private String status;
        private Date updatedAt;
    }
}
