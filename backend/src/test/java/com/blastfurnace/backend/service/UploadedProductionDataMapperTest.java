package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.service.trainer.RandomForestTrainer;
import com.blastfurnace.backend.service.trainer.TrainingResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UploadedProductionDataMapperTest {

    @Test
    public void evaluationSplitShouldMatchTrainingSplit() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            double temperature = 1300 + i;
            double pressure = 150 + (i % 10);
            double gasFlow = 2500 + i * 2;
            double materialHeight = 3.0 + (i % 5) * 0.1;
            double constantSignal = 1.0;
            double y = 0.5 * temperature + 0.1 * pressure + 0.01 * gasFlow + materialHeight + constantSignal;

            Map<String, String> row = new HashMap<>();
            row.put("temperature", Double.toString(temperature));
            row.put("pressure", Double.toString(pressure));
            row.put("gasFlow", Double.toString(gasFlow));
            row.put("materialHeight", Double.toString(materialHeight));
            row.put("constantSignal", Double.toString(constantSignal));
            row.put("energyConsumption", Double.toString(y));
            rows.add(row);
        }

        String selectedFeatures = "temperature,pressure,gasFlow,materialHeight,constantSignal";
        List<ProductionData> dataList = UploadedProductionDataMapper.toProductionDataList(
                rows,
                selectedFeatures,
                "energyConsumption"
        );

        ModelTraining training = new ModelTraining();
        training.setTargetVariable("energyConsumption");
        training.setSelectedFeatures(selectedFeatures);

        ModelConfig config = new ModelConfig();
        config.setTreeCount(50);
        config.setMaxDepth(10);

        RandomForestTrainer trainer = new RandomForestTrainer();
        TrainingResult trainingResult = trainer.train(dataList, training, config, selectedFeatures.split(","));

        List<ProductionData> shuffled = new ArrayList<>(dataList);
        Collections.shuffle(shuffled, new Random(123));
        int splitIndex = (int) (shuffled.size() * 0.8);
        List<ProductionData> evalList = splitIndex < shuffled.size()
                ? new ArrayList<>(shuffled.subList(splitIndex, shuffled.size()))
                : new ArrayList<>(shuffled);

        TrainingResult evalResult = trainer.evaluate(
                evalList,
                training,
                config,
                selectedFeatures.split(","),
                trainingResult.getModel(),
                trainingResult.getPreprocessor()
        );

        assertEquals(trainingResult.getR2Score(), evalResult.getR2Score(), 1e-9);
        assertEquals(trainingResult.getMae(), evalResult.getMae(), 1e-9);
        assertEquals(trainingResult.getRmse(), evalResult.getRmse(), 1e-9);
    }
}

