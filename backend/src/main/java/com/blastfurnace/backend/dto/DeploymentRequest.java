package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class DeploymentRequest {
    private Long trainingId;
    private Long secondaryTrainingId;
    private String environment;
    private String name;
    private String version;
    private String description;
    private String config;
}
