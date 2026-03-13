package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "anomaly_thresholds")
public class AnomalyThreshold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "furnace_id")
    private String furnaceId; // "GLOBAL" or specific ID like "BF-001"

    @Column(name = "parameter_name", nullable = false)
    private String parameterName;

    @Column(name = "min_val")
    private Double minVal;

    @Column(name = "max_val")
    private Double maxVal;

    @Column(name = "tip_offset_pct")
    private Double tipOffsetPct;

    @Column(name = "warning_offset_pct")
    private Double warningOffsetPct;

    @Column(name = "severe_offset_pct")
    private Double severeOffsetPct;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
