package com.blastfurnace.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeploymentResponse {
    private Long deploymentId;
    private String deploymentStatus;
    private Integer deploymentProgress;
    private String deployedModelName;
    private String deployedEnvironment;
    private String deploymentTime;
    private String serviceUrl;
    private String serviceStatus;
    private String apiVersion;
    private List<String> deploymentLogs;
}
