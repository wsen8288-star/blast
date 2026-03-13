package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSplitConsistencyTest {

    @Test
    public void randomForestTimeSplitShouldMatchEvaluationSplit() {
        List<ProductionData> dataList = new ArrayList<>();
        long base = 1700000000000L;
        for (int i = 0; i < 60; i++) {
            ProductionData d = new ProductionData();
            d.setTimestamp(new Date(base + i * 60000L));
            d.setTemperature(1300.0 + i);
            d.setPressure(150.0 + (i % 10));
            d.setGasFlow(2500.0 + i * 2);
            d.setMaterialHeight(3.0 + (i % 5) * 0.1);
            d.setConstantSignal(1.0);
            d.setEnergyConsumption(0.5 * d.getTemperature() + 0.1 * d.getPressure() + 0.01 * d.getGasFlow() + d.getMaterialHeight());
            dataList.add(d);
        }

        ModelTraining training = new ModelTraining();
        training.setTargetVariable("energyConsumption");
        training.setSplitMode("auto");
        training.setSplitRatio(0.8);
        training.setSplitSeed(123L);
        training.setSplitHasTimestamp(true);

        ModelConfig config = new ModelConfig();
        config.setTreeCount(200);
        config.setMaxDepth(20);
        config.setFeatureCount(4);
        config.setNodeSize(5);
        config.setSubsample(1.0);

        String[] features = {"temperature", "pressure", "gasFlow", "materialHeight", "constantSignal"};

        RandomForestTrainer trainer = new RandomForestTrainer();
        TrainingResult trainResult = trainer.train(dataList, training, config, features);

        DataSplitUtil.SplitResult split = DataSplitUtil.split(dataList, training);
        TrainingResult evalResult = trainer.evaluate(
                split.validation(),
                training,
                config,
                features,
                trainResult.getModel(),
                trainResult.getPreprocessor()
        );

        assertEquals(trainResult.getR2Score(), evalResult.getR2Score(), 1e-9);
        assertEquals(trainResult.getMae(), evalResult.getMae(), 1e-9);
        assertEquals(trainResult.getRmse(), evalResult.getRmse(), 1e-9);
    }

    @Test
    public void randomForestRandomSplitShouldMatchEvaluationSplit() {
        List<ProductionData> dataList = new ArrayList<>();
        long base = 1700000000000L;
        for (int i = 0; i < 60; i++) {
            ProductionData d = new ProductionData();
            d.setTimestamp(new Date(base + i * 60000L));
            d.setTemperature(1300.0 + i);
            d.setPressure(150.0 + (i % 10));
            d.setGasFlow(2500.0 + i * 2);
            d.setMaterialHeight(3.0 + (i % 5) * 0.1);
            d.setConstantSignal(1.0);
            d.setEnergyConsumption(0.5 * d.getTemperature() + 0.1 * d.getPressure() + 0.01 * d.getGasFlow() + d.getMaterialHeight());
            dataList.add(d);
        }

        ModelTraining training = new ModelTraining();
        training.setTargetVariable("energyConsumption");
        training.setSplitMode("auto");
        training.setSplitRatio(0.8);
        training.setSplitSeed(123L);
        training.setSplitHasTimestamp(false);

        ModelConfig config = new ModelConfig();
        config.setTreeCount(200);
        config.setMaxDepth(20);
        config.setFeatureCount(4);
        config.setNodeSize(5);
        config.setSubsample(1.0);

        String[] features = {"temperature", "pressure", "gasFlow", "materialHeight", "constantSignal"};

        RandomForestTrainer trainer = new RandomForestTrainer();
        TrainingResult trainResult = trainer.train(dataList, training, config, features);

        DataSplitUtil.SplitResult split = DataSplitUtil.split(dataList, training);
        TrainingResult evalResult = trainer.evaluate(
                split.validation(),
                training,
                config,
                features,
                trainResult.getModel(),
                trainResult.getPreprocessor()
        );

        assertEquals(trainResult.getR2Score(), evalResult.getR2Score(), 1e-9);
        assertEquals(trainResult.getMae(), evalResult.getMae(), 1e-9);
        assertEquals(trainResult.getRmse(), evalResult.getRmse(), 1e-9);
    }
}

