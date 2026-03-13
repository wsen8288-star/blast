package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ProductionData;
import org.junit.jupiter.api.Assumptions;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TestTrainingDataLoader {
    private TestTrainingDataLoader() {}

    public static List<ProductionData> load() throws Exception {
        InputStream is = openStream();
        Assumptions.assumeTrue(is != null, "test_training_data.csv not found");

        List<ProductionData> dataList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                String[] values = line.split(",");
                if (values.length < 7) {
                    continue;
                }
                ProductionData data = new ProductionData();
                data.setTemperature(Double.parseDouble(values[0]));
                data.setPressure(Double.parseDouble(values[1]));
                data.setMaterialHeight(Double.parseDouble(values[2]));
                data.setGasFlow(Double.parseDouble(values[3]));
                data.setOxygenLevel(Double.parseDouble(values[4]));
                data.setProductionRate(Double.parseDouble(values[5]));
                data.setEnergyConsumption(Double.parseDouble(values[6]));
                dataList.add(data);
            }
        }

        return dataList;
    }

    private static InputStream openStream() {
        String csvPath = System.getProperty("trainer.csv");
        try {
            if (csvPath != null && !csvPath.isBlank()) {
                return new FileInputStream(csvPath);
            }
        } catch (Exception ignored) {
            return null;
        }

        return TestTrainingDataLoader.class.getClassLoader().getResourceAsStream("test_training_data.csv");
    }
}
