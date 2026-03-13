package com.blastfurnace.backend.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class ModelStoreService {
    private final ConcurrentHashMap<Long, StoredModel> store = new ConcurrentHashMap<>();
    
    public void save(Long trainingId, StoredModel model) {
        store.put(trainingId, model);
    }
    
    public Optional<StoredModel> get(Long trainingId) {
        return Optional.ofNullable(store.get(trainingId));
    }
    
    public void remove(Long trainingId) {
        store.remove(trainingId);
    }
    
    public static class StoredModel {
        private final String modelType;
        private final Object model;
        private final Object preprocessor;
        private final String[] features;
        
        public StoredModel(String modelType, Object model, Object preprocessor, String[] features) {
            this.modelType = modelType;
            this.model = model;
            this.preprocessor = preprocessor;
            this.features = features;
        }
        
        public String getModelType() {
            return modelType;
        }
        
        public Object getModel() {
            return model;
        }
        
        public Object getPreprocessor() {
            return preprocessor;
        }
        
        public String[] getFeatures() {
            return features;
        }
    }
}
