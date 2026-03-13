package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeuralNetworkTrainerDataQualityTest {

    @Test
    public void trainShouldDropMissingTargetRows() {
        List<ProductionData> dataList = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ProductionData d = new ProductionData();
            d.setTemperature(1300.0 + i);
            d.setPressure(150.0 + (i % 10));
            d.setGasFlow(2500.0 + i * 2);
            d.setMaterialHeight(3.0 + (i % 5) * 0.1);
            d.setConstantSignal(1.0);
            d.setEnergyConsumption(0.5 * d.getTemperature() + 0.1 * d.getPressure() + 0.01 * d.getGasFlow());
            dataList.add(d);
        }
        dataList.get(5).setEnergyConsumption(null);

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        modelTraining.setTargetVariable("energyConsumption");
        modelTraining.setLearningRate(0.001);
        modelTraining.setEpochs(2);
        modelTraining.setBatchSize(16);

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setHiddenLayers(1);
        modelConfig.setNeuronsPerLayer("8");
        modelConfig.setActivationFunction("relu");
        modelConfig.setLossFunction("mse");
        modelConfig.setOptimizer("adam");
        modelConfig.setDropoutRate(0.0);

        String[] features = {"temperature", "pressure", "gasFlow", "materialHeight", "constantSignal", "energyConsumption"};
        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("丢弃目标缺失样本: 1"));
    }
}

