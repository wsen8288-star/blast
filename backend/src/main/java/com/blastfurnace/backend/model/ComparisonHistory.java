package com.blastfurnace.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "comparison_history")
public class ComparisonHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mode;

    private String schemeA;

    private String schemeB;

    private String result;

    private Double scoreA;

    private Double scoreB;

    private Date createdAt;

    private String historyType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "run_id")
    private String runId;
}
