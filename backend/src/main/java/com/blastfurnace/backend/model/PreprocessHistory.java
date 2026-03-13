package com.blastfurnace.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Data
@Entity
@Table(name = "preprocess_history")
public class PreprocessHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id")
    private String runId;

    @Column(nullable = false)
    private String status;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
