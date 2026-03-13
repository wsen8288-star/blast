package com.blastfurnace.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "model_service")
public class ModelService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deploymentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String version;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastHeartbeat;

    @Lob
    @Column(name = "service_config", columnDefinition = "TEXT")
    private String serviceConfig;
}
