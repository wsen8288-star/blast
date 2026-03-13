package com.blastfurnace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "model_config")
public class ModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "config_name", nullable = false)
    private String configName;
    
    @Column(name = "hidden_layers", nullable = false)
    private Integer hiddenLayers;
    
    @Column(name = "neurons_per_layer", nullable = false)
    private String neuronsPerLayer;
    
    @Column(name = "activation_function", nullable = false)
    private String activationFunction;
    
    @Column(name = "loss_function", nullable = false)
    private String lossFunction;
    
    @Column(name = "optimizer", nullable = false)
    private String optimizer;
    
    @Column(name = "dropout_rate")
    private Double dropoutRate;

    // Random Forest / Gradient Boosting specific
    @Column(name = "tree_count")
    private Integer treeCount;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @Column(name = "feature_count")
    private Integer featureCount;

    // Gradient Boosting specific
    @Column(name = "base_complexity")
    private Double baseComplexity;

    @Column(name = "subsample")
    private Double subsample;

    @Column(name = "max_nodes")
    private Integer maxNodes;

    @Column(name = "node_size")
    private Integer nodeSize;

    // GPR specific
    @Column(name = "gpr_length_scale")
    private Double gprLengthScale;

    @Column(name = "gpr_noise_variance")
    private Double gprNoiseVariance;

    // Common/Gradient Boosting specific
    @Column(name = "learning_rate")
    private Double learningRate;
    
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;
}
