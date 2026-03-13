package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

@EnabledIfSystemProperty(named = "runTrainerTests", matches = "true")
public class TreeModelTest {

    private List<ProductionData> loadData() throws Exception {
        return TestTrainingDataLoader.load();
    }

    @Test
    public void testRandomForest() throws Exception {
        System.out.println("========== Testing Random Forest ==========");
        List<ProductionData> dataList = loadData();

        RandomForestTrainer trainer = new RandomForestTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        modelConfig.setTreeCount(200);
        modelConfig.setMaxDepth(20);
        modelConfig.setFeatureCount(4);
        
        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("Random Forest Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }

    @Test
    public void testGradientBoosting() throws Exception {
        System.out.println("========== Testing Gradient Boosting ==========");
        List<ProductionData> dataList = loadData();

        GradientBoostingTrainer trainer = new GradientBoostingTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        modelConfig.setTreeCount(200); // Instead of setIterations
        modelTraining.setLearningRate(0.1); // Instead of modelConfig.setLearningRate
        modelConfig.setMaxDepth(5);
        
        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("Gradient Boosting Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }
}
