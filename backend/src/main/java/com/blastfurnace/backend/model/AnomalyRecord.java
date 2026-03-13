package com.blastfurnace.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "anomaly_records")
public class AnomalyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String furnaceId;

    @Column(nullable = false)
    private LocalDateTime detectionTime;

    @Column(nullable = false)
    private String parameterName;

    @Column(nullable = false)
    private Double actualValue;

    private String expectedRange;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private Integer status;

    private String handlerContent;

    private Long handlerUser;

    private String handler;

    private LocalDateTime handleTime;

    @Column(length = 500)
    private String description;

    @Column(name = "related_data_id")
    private Long relatedDataId;
}
