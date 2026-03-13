package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

@EnabledIfSystemProperty(named = "runTrainerTests", matches = "true")
public class NeuralNetworkOptimizationTest {

    // Helper to load data
    private List<ProductionData> loadData() throws Exception {
        return TestTrainingDataLoader.load();
    }

    @Test
    public void testReLUPoorPerformanceReproduction() throws Exception {
        System.out.println("========== Reproduction Test: ReLU Poor Performance ==========");
        List<ProductionData> dataList = loadData();

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        // User's Screenshot Parameters
        modelConfig.setActivationFunction("relu");
        modelConfig.setNeuronsPerLayer("64");
        modelConfig.setHiddenLayers(1);
        
        modelTraining.setLearningRate(0.001); // User's LR
        modelTraining.setEpochs(1000);        // User's Epochs
        modelTraining.setBatchSize(32);       // User's Batch Size

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("ReLU Reproduction Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
        
        // Expecting low performance around 48% R2
    }
    
    @Test
    public void testReLUTuning_SmallBatch_HighLR() throws Exception {
        System.out.println("========== Tuning Test: Small Batch + Higher LR ==========");
        List<ProductionData> dataList = loadData();

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        // Tuned Parameters
        modelConfig.setActivationFunction("relu");
        modelConfig.setNeuronsPerLayer("64");
        modelConfig.setHiddenLayers(1); // Keep structure simple first
        
        modelTraining.setLearningRate(0.005); // Increase LR
        modelTraining.setEpochs(1000);        // Keep epochs same
        modelTraining.setBatchSize(8);        // Decrease batch size (more updates)

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("ReLU Tuned Result (LR=0.005, Batch=8):");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }

    @Test
    public void testReLUTuning_TwoLayers() throws Exception {
        System.out.println("========== Tuning Test: Two Layers + Small Batch ==========");
        List<ProductionData> dataList = loadData();

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        // Tuned Parameters
        modelConfig.setActivationFunction("relu");
        modelConfig.setNeuronsPerLayer("64,32");
        modelConfig.setHiddenLayers(2); // Two layers
        
        modelTraining.setLearningRate(0.005);
        modelTraining.setEpochs(1000);
        modelTraining.setBatchSize(8);

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("ReLU Tuned Result (2 Layers, LR=0.005, Batch=8):");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }
}
