package com.blastfurnace.backend.service.trainer;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.model.ModelTraining;
import com.blastfurnace.backend.model.ProductionData;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class NeuralNetworkTrainerConfigTest {

    @Test
    public void testOptimizerAndDropoutApplied() throws Exception {
        var trainer = new NeuralNetworkTrainer();

        var modelTraining = new ModelTraining();
        modelTraining.setLearningRate(0.01);
        modelTraining.setEpochs(1);
        modelTraining.setBatchSize(8);

        var modelConfig = new ModelConfig();
        modelConfig.setHiddenLayers(1);
        modelConfig.setNeuronsPerLayer("4");
        modelConfig.setActivationFunction("relu");
        modelConfig.setLossFunction("mse");
        modelConfig.setOptimizer("sgd");
        modelConfig.setDropoutRate(0.25);

        var d1 = new ProductionData();
        d1.setTemperature(1400.0);
        d1.setProductionRate(50.0);

        var d2 = new ProductionData();
        d2.setTemperature(1450.0);
        d2.setProductionRate(55.0);

        var result = trainer.train(List.of(d1, d2), modelTraining, modelConfig, new String[]{"temperature", "productionRate"});
        Assertions.assertTrue(result.isSuccess(), result.getMessage());

        Object packed = result.getModel();
        Assertions.assertTrue(packed instanceof Map<?, ?>);
        Object nn = ((Map<?, ?>) packed).get("model");
        Assertions.assertTrue(nn instanceof MultiLayerNetwork);
        MultiLayerNetwork model = (MultiLayerNetwork) nn;

        Object conf0 = model.getLayerWiseConfigurations().getConf(0);
        Object layer = invokeFirst(conf0, new String[]{"getLayer"});
        Object updater = invokeFirst(layer, new String[]{"getIUpdater", "getUpdater"});
        Assertions.assertNotNull(updater);
        Assertions.assertTrue(updater.getClass().getSimpleName().toLowerCase().contains("sgd"));

        Object dropout = invokeFirst(layer, new String[]{"getIDropout", "getDropOut"});
        Assertions.assertNotNull(dropout);
        Assertions.assertTrue(dropout.getClass().getSimpleName().toLowerCase().contains("dropout"));
    }

    private Object invokeFirst(Object target, String[] methodNames) throws Exception {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + " has none of " + String.join(",", methodNames));
    }
}
