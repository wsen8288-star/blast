package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.StorageConfig;
import com.blastfurnace.backend.repository.StorageConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StorageConfigService {
    @Autowired
    private StorageConfigRepository storageConfigRepository;
    
    // 获取默认存储配置
    public StorageConfig getDefaultConfig() {
        StorageConfig config = storageConfigRepository.findByIsDefaultTrue();
        if (config == null) {
            // 如果没有默认配置，创建一个默认配置
            config = createDefaultConfig();
        }
        return config;
    }
    
    // 保存存储配置
    public StorageConfig saveConfig(StorageConfig config) {
        // 查找现有的默认配置
        StorageConfig existingConfig = storageConfigRepository.findByIsDefaultTrue();
        
        if (existingConfig != null) {
            // 如果存在默认配置，更新它
            existingConfig.setMainStoragePath(config.getMainStoragePath());
            existingConfig.setBackupStoragePath(config.getBackupStoragePath());
            existingConfig.setStorageFormat(config.getStorageFormat());
            existingConfig.setAutoBackup(config.getAutoBackup());
            existingConfig.setBackupFrequency(config.getBackupFrequency());
            // 保持isDefault为true
            return storageConfigRepository.save(existingConfig);
        } else {
            // 如果不存在默认配置，创建新的
            config.setIsDefault(Boolean.TRUE);
            return storageConfigRepository.save(config);
        }
    }
    
    // 创建默认存储配置
    private StorageConfig createDefaultConfig() {
        StorageConfig config = new StorageConfig();
        config.setMainStoragePath("D:/blast-furnace/data/main");
        config.setBackupStoragePath("D:/blast-furnace/data/backup");
        config.setStorageFormat("parquet");
        config.setAutoBackup(Boolean.TRUE);
        config.setBackupFrequency("daily");
        config.setIsDefault(Boolean.TRUE);
        return storageConfigRepository.save(config);
    }
    
    // 初始化存储配置
    public void initStorageConfig() {
        if (storageConfigRepository.count() == 0) {
            createDefaultConfig();
        }
    }
}
