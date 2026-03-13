package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

@EnabledIfSystemProperty(named = "runTrainerTests", matches = "true")
public class LabelNormalizationTest {

    private List<ProductionData> loadData() throws Exception {
        return TestTrainingDataLoader.load();
    }

    @Test
    public void testTanhWithLabelNormalization() throws Exception {
        System.out.println("========== Testing Tanh with Label Normalization ==========");
        List<ProductionData> dataList = loadData();

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        // Settings that previously failed or gave weird results
        modelConfig.setActivationFunction("tanh");
        modelConfig.setNeuronsPerLayer("64,32");
        modelConfig.setHiddenLayers(2);
        
        modelTraining.setLearningRate(0.01); 
        modelTraining.setEpochs(2000);        
        modelTraining.setBatchSize(16);       

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("Tanh Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
        
        if (result.getR2Score() < 10) {
             throw new RuntimeException("R2 is too low: " + result.getR2Score());
        }
    }

    @Test
    public void testReLUWithLabelNormalization() throws Exception {
        System.out.println("========== Testing ReLU with Label Normalization ==========");
        List<ProductionData> dataList = loadData();

        NeuralNetworkTrainer trainer = new NeuralNetworkTrainer();
        ModelTraining modelTraining = new ModelTraining();
        ModelConfig modelConfig = new ModelConfig();
        
        modelConfig.setActivationFunction("relu");
        modelConfig.setNeuronsPerLayer("64,32");
        modelConfig.setHiddenLayers(2);
        
        modelTraining.setLearningRate(0.005); 
        modelTraining.setEpochs(2000);        
        modelTraining.setBatchSize(16);       

        String[] features = {"temperature", "pressure", "materialHeight", "gasFlow", "oxygenLevel", "productionRate"};

        TrainingResult result = trainer.train(dataList, modelTraining, modelConfig, features);

        System.out.println("ReLU Result:");
        System.out.println("Loss: " + result.getTrainingLoss());
        System.out.println("R2Score (R2*100): " + result.getR2Score());
        System.out.println("MAE: " + result.getMae());
        System.out.println("RMSE: " + result.getRmse());
    }
}
