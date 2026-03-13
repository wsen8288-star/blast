package com.blastfurnace.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "model_deployment")
public class ModelDeployment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long trainingId;

    @Column(nullable = true)
    private Long secondaryTrainingId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private Date deployTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Column(nullable = true, length = 1000)
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String config;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String logs;
}
