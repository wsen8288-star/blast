package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

@EnabledIfSystemProperty(named = "runTrainerTests", matches = "true")
public class GprTest {

    private List<ProductionData> loadData() throws Exception {
        return TestTrainingDataLoader.load();
    }

    @Test
    public void testGprWithLabelNormalization() throws Exception {
        System.out.println("========== Testing GPR with Label Normalization ==========");
        List<ProductionData> dataList = loadData();

        GaussianProcessRegressionTrainer trainer = new GaussianProcessRegressionTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();

        modelConfig.setGprLengthScale(1.0);
        modelConfig.setGprNoiseVariance(0.1);

        modelTraining.setLearningRate(0.01);

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("GPR Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }
}
