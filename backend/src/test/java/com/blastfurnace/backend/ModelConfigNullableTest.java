package com.blastfurnace.backend;

import com.blastfurnace.backend.model.ModelConfig;
import com.blastfurnace.backend.repository.ModelConfigRepository;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class ModelConfigNullableTest {

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Test
    @Transactional
    void shouldSaveModelConfigWithNullDropoutRate() {
        var now = new Date();
        var config = new ModelConfig();
        config.setConfigName("test-null-dropout");
        config.setHiddenLayers(2);
        config.setNeuronsPerLayer("64,32");
        config.setActivationFunction("relu");
        config.setLossFunction("mse");
        config.setOptimizer("adam");
        config.setDropoutRate(null);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        modelConfigRepository.saveAndFlush(config);
    }
}
