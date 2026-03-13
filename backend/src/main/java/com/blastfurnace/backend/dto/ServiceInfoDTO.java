package com.blastfurnace.backend.dto;

import lombok.Data;

@Data
public class ServiceInfoDTO {
    private Long id;
    private Long trainingId;
    private String targetVariable;
    private String name;
    private String modelName;
    private String environment;
    private String status;
    private String url;
    private String version;
}
