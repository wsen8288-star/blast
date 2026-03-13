package com.blastfurnace.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "model_evaluation")
public class ModelEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long trainingId;
    @Column(nullable = false)
    private String modelType;
    @Column(nullable = false)
    private String dataSource;
    @Column(nullable = true, length = 1000)
    private String features;
    @Column(nullable = false)
    private Double r2;
    @Column(nullable = false)
    private Double mae;
    @Column(nullable = false)
    private Double rmse;
    @Column(nullable = false)
    private Date createdAt;

    @Lob
    @Column(name = "true_values_json", columnDefinition = "LONGTEXT")
    private String trueValuesJson;

    @Lob
    @Column(name = "predicted_values_json", columnDefinition = "LONGTEXT")
    private String predictedValuesJson;

    @Lob
    @Column(name = "feature_importance_json", columnDefinition = "LONGTEXT")
    private String featureImportanceJson;
}
