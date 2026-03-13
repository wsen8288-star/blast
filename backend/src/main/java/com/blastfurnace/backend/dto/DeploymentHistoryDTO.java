package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class DeploymentHistoryDTO {
    private Long id;
    private String modelName;
    private String environment;
    private String name;
    private String version;
    private String deployTime;
    private String status;
    private String serviceUrl;
    private String config;
}
