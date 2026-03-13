package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ProductionData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

/**
 * 模型训练器接口，定义训练模型的统一方法
 */
public interface ModelTrainer {
    
    /**
     * 训练模型
     * @param trainingData 训练数据
     * @param modelTraining 训练任务信息
     * @param modelConfig 模型配置
     * @param selectedFeatures 选择的特征列表
     * @return 训练结果，包含损失值和准确率
     */
    TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining, 
                        ModelConfig modelConfig, String[] selectedFeatures);

    default TrainingResult train(List<ProductionData> trainingData, ModelTraining modelTraining,
                        ModelConfig modelConfig, String[] selectedFeatures,
                        java.util.function.Consumer<ModelTraining> progressCallback) {
        return train(trainingData, modelTraining, modelConfig, selectedFeatures);
    }
                        
    TrainingResult evaluate(List<ProductionData> evaluationData, ModelTraining modelTraining, 
                        ModelConfig modelConfig, String[] selectedFeatures, Object model, Object preprocessor);

    default String getTargetVariable(ModelTraining modelTraining) {
        String raw = modelTraining == null ? null : modelTraining.getTargetVariable();
        if (raw == null) {
            return "productionRate";
        }
        String t = raw.trim();
        return t.isEmpty() ? "productionRate" : t;
    }

    default Double getTargetValue(ProductionData data, String targetVariable) {
        if (data == null) {
            return null;
        }
        String t = targetVariable == null ? "" : targetVariable.trim();
        if (t.isEmpty()) {
            t = "productionRate";
        }
        return switch (t) {
            case "productionRate" -> data.getProductionRate();
            case "energyConsumption" -> data.getEnergyConsumption();
            case "hotMetalTemperature" -> data.getHotMetalTemperature();
            case "siliconContent" -> data.getSiliconContent();
            default -> data.getProductionRate();
        };
    }

    default void setTargetValue(ProductionData data, String targetVariable, Double value) {
        if (data == null) {
            return;
        }
        String t = targetVariable == null ? "" : targetVariable.trim();
        if (t.isEmpty()) {
            t = "productionRate";
        }
        switch (t) {
            case "productionRate" -> data.setProductionRate(value);
            case "energyConsumption" -> data.setEnergyConsumption(value);
            case "hotMetalTemperature" -> data.setHotMetalTemperature(value);
            case "siliconContent" -> data.setSiliconContent(value);
            default -> data.setProductionRate(value);
        }
    }

    default byte[] serializeModel(Object model) throws Exception {
        if (model == null) {
            return null;
        }
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(model);
            oos.flush();
            return bos.toByteArray();
        }
    }

    default Object deserializeModel(byte[] modelBytes) throws Exception {
        if (modelBytes == null || modelBytes.length == 0) {
            return null;
        }
        try (var bis = new ByteArrayInputStream(modelBytes);
             var ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }

    default byte[] serializePreprocessor(Object preprocessor) throws Exception {
        if (preprocessor == null) {
            return null;
        }
        try (var bos = new ByteArrayOutputStream();
             var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(preprocessor);
            oos.flush();
            return bos.toByteArray();
        }
    }

    default Object deserializePreprocessor(byte[] preprocessorBytes) throws Exception {
        if (preprocessorBytes == null || preprocessorBytes.length == 0) {
            return null;
        }
        try (var bis = new ByteArrayInputStream(preprocessorBytes);
             var ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }
                        
    /**
     * 获取模型类型
     * @return 模型类型名称
     */
    String getModelType();
}
