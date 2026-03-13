package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.annotation.PostConstruct;
import java.util.Date;

@Data
@NoArgsConstructor
@Entity
@Table(name = "model_training")
public class ModelTraining {
    
    // 自定义构造方法，包含所有字段
    public ModelTraining(Long id, String modelType, String trainingData, Integer epochs, Integer batchSize, 
                        Double learningRate, String selectedFeatures, String status, Integer progress, 
                        String targetVariable, Integer currentEpoch, Double trainingLoss, Double r2Score, 
                        Double mae, Double rmse, Date startTime, Date endTime, String customDataId, 
                        ModelConfig modelConfig) {
        this.id = id;
        this.modelType = modelType;
        this.trainingData = trainingData;
        this.epochs = epochs;
        this.batchSize = batchSize;
        this.learningRate = learningRate;
        this.selectedFeatures = selectedFeatures;
        this.status = status;
        this.progress = progress;
        this.targetVariable = targetVariable;
        this.currentEpoch = currentEpoch;
        this.trainingLoss = trainingLoss;
        this.r2Score = r2Score;
        this.mae = mae;
        this.rmse = rmse;
        this.startTime = startTime;
        this.endTime = endTime;
        this.customDataId = customDataId;
        this.modelConfig = modelConfig;
    }
    
    // 初始化方法，设置默认值
    @PostConstruct
    public void init() {
        if (mae == null) {
            mae = 0.0;
        }
        if (rmse == null) {
            rmse = 0.0;
        }
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "model_type", nullable = false)
    private String modelType;
    
    @Column(name = "training_data", nullable = false)
    private String trainingData;
    
    @Column(name = "epochs", nullable = false)
    private Integer epochs;
    
    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;
    
    @Column(name = "learning_rate", nullable = false)
    private Double learningRate;
    
    @Column(name = "selected_features", nullable = false)
    private String selectedFeatures;

    @Column(name = "target_variable")
    private String targetVariable;
    
    @Column(name = "status", nullable = false)
    private String status; // idle, running, completed, failed
    
    @Column(name = "progress", nullable = false)
    private Integer progress;
    
    @Column(name = "current_epoch", nullable = false)
    private Integer currentEpoch;
    
    @Column(name = "training_loss", nullable = false)
    private Double trainingLoss;
    
    @Column(name = "validation_accuracy", nullable = false)
    private Double r2Score;
    
    @Column(name = "mae", nullable = false)
    private Double mae;
    
    @Column(name = "rmse", nullable = false)
    private Double rmse;
    
    @Column(name = "start_time", nullable = true)
    private Date startTime;
    
    @Column(name = "end_time", nullable = true)
    private Date endTime;
    
    @Column(name = "custom_data_id")
    private String customDataId;

    @Column(name = "run_id")
    private String runId;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "model_bytes", columnDefinition = "LONGBLOB")
    private byte[] modelBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "preprocessor_bytes", columnDefinition = "LONGBLOB")
    private byte[] preprocessorBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "training_data_bytes", columnDefinition = "LONGBLOB")
    private byte[] trainingDataBytes;

    @Column(name = "model_serialized_at")
    private Date modelSerializedAt;

    @Column(name = "split_mode")
    private String splitMode;

    @Column(name = "split_mode_used")
    private String splitModeUsed;

    @Column(name = "split_ratio")
    private Double splitRatio;

    @Column(name = "split_seed")
    private Long splitSeed;

    @Column(name = "split_has_timestamp")
    private Boolean splitHasTimestamp;
    
    @ManyToOne
    @JoinColumn(name = "model_config_id")
    private ModelConfig modelConfig;
}
