package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class VerificationTest {

    private List<ProductionData> loadUserCsv() throws Exception {
        // Correct path based on user input and workspace structure
        File file = new File("c:\\Users\\ws\\Desktop\\naive-ui-admin-main\\backend\\backend\\data\\collection\\collection_collection-task-1772854948689_1772854997737.csv");
        if (!file.exists()) {
            // Fallback for different CWD
            file = new File("backend/data/collection/collection_collection-task-1772854948689_1772854997737.csv");
        }
        
        System.out.println("Loading data from: " + file.getAbsolutePath());
        
        List<ProductionData> dataList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                // Skip BOM and header
                if (first) {
                    first = false;
                    continue;
                }
                String[] values = line.split(",");
                if (values.length < 13) {
                    continue;
                }
                
                try {
                    ProductionData data = new ProductionData();
                    // Indices based on analysis: 
                    // 2:Temp, 3:Press, 4:Wind, 5:Coal, 6:Height, 7:Gas, 8:Oxy, 9:Prod, 10:Energy, 12:Silicon
                    data.setTemperature(Double.parseDouble(values[2]));
                    data.setPressure(Double.parseDouble(values[3]));
                    data.setWindVolume(Double.parseDouble(values[4]));
                    data.setCoalInjection(Double.parseDouble(values[5]));
                    data.setMaterialHeight(Double.parseDouble(values[6]));
                    data.setGasFlow(Double.parseDouble(values[7]));
                    data.setOxygenLevel(Double.parseDouble(values[8]));
                    data.setProductionRate(Double.parseDouble(values[9]));
                    data.setEnergyConsumption(Double.parseDouble(values[10]));
                    // Column 11 is Iron Temperature
                    if (values.length > 11 && !values[11].isEmpty()) {
                         data.setHotMetalTemperature(Double.parseDouble(values[11]));
                    }
                    data.setSiliconContent(Double.parseDouble(values[12]));
                    
                    dataList.add(data);
                } catch (NumberFormatException e) {
                    // Skip bad lines
                }
            }
        }
        System.out.println("Loaded " + dataList.size() + " samples.");
        return dataList;
    }

    @Test
    public void verifySiliconPrediction() throws Exception {
        System.out.println("========== Verifying 7.2.1 Silicon Content Prediction on User Data ==========");
        List<ProductionData> dataList = loadUserCsv();
        if (dataList.isEmpty()) {
            System.out.println("No data loaded, skipping verification.");
            return;
        }

        String[] features = {"temperature", "pressure", "windVolume", "coalInjection", "materialHeight", "gasFlow", "oxygenLevel", "hotMetalTemperature"};
        
        // 1. Random Forest
        System.out.println("\n--- Testing Random Forest ---");
        RandomForestTrainer rfTrainer = new RandomForestTrainer();
        ModelTraining rfTraining = new ModelTraining();
        rfTraining.setTargetVariable("siliconContent");
        ModelConfig rfConfig = new ModelConfig();
        rfConfig.setTreeCount(100);
        rfConfig.setMaxDepth(15);
        rfConfig.setFeatureCount(3);
        
        TrainingResult rfResult = rfTrainer.train(dataList, rfTraining, rfConfig, features);
        System.out.println("RF RMSE: " + rfResult.getRmse());
        System.out.println("RF R2: " + rfResult.getR2Score());

        // 2. Gradient Boosting
        System.out.println("\n--- Testing Gradient Boosting ---");
        GradientBoostingTrainer gbTrainer = new GradientBoostingTrainer();
        ModelTraining gbTraining = new ModelTraining();
        gbTraining.setTargetVariable("siliconContent");
        ModelConfig gbConfig = new ModelConfig();
        gbConfig.setTreeCount(100);
        gbConfig.setMaxDepth(5);
        gbTraining.setLearningRate(0.1);
        
        TrainingResult gbResult = gbTrainer.train(dataList, gbTraining, gbConfig, features);
        System.out.println("GB RMSE: " + gbResult.getRmse());
        System.out.println("GB R2: " + gbResult.getR2Score());
        
        // 3. Gaussian Process Regression
        System.out.println("\n--- Testing GPR ---");
        GaussianProcessRegressionTrainer gprTrainer = new GaussianProcessRegressionTrainer();
        ModelTraining gprTraining = new ModelTraining();
        gprTraining.setTargetVariable("siliconContent");
        ModelConfig gprConfig = new ModelConfig();
        gprConfig.setGprLengthScale(1.0);
        gprConfig.setGprNoiseVariance(0.1);
        
        TrainingResult gprResult = gprTrainer.train(dataList, gprTraining, gprConfig, features);
        System.out.println("GPR RMSE: " + gprResult.getRmse());
        System.out.println("GPR R2: " + gprResult.getR2Score());
        
        System.out.println("\nVerification Conclusion: " + 
            (gbResult.getRmse() < 0.05 ? "Data supports high accuracy (Low RMSE)." : "Data might require tuning.")
        );
    }
}
