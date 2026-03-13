package com.blastfurnace.backend.service.trainer;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型训练器工厂，用于根据模型类型创建相应的训练器实例
 */
@Component
public class ModelTrainerFactory {
    
    private final Map<String, ModelTrainer> trainers = new HashMap<>();
    
    /**
     * 构造方法，注册所有训练器
     */
    public ModelTrainerFactory() {
        // 注册训练器
        trainers.put("neural_network", new NeuralNetworkTrainer());
        trainers.put("random_forest", new RandomForestTrainer());
        trainers.put("gradient_boosting", new GradientBoostingTrainer());
        trainers.put("gpr", new GaussianProcessRegressionTrainer());
    }
    
    /**
     * 获取训练器实例
     * @param modelType 模型类型
     * @return 训练器实例
     */
    public ModelTrainer getTrainer(String modelType) {
        ModelTrainer trainer = trainers.get(modelType);
        if (trainer == null) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
        return trainer;
    }
}
