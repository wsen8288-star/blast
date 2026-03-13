package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.CollectionSettings;
import com.blastfurnace.backend.repository.CollectionSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionSettingsService {
    @Autowired
    private CollectionSettingsRepository collectionSettingsRepository;

    @Autowired
    private SysConfigService sysConfigService;
    
    // 获取默认采集设置
    public CollectionSettings getDefaultSettings() {
        CollectionSettings settings = collectionSettingsRepository.findByIsDefaultTrue();
        if (settings == null) {
            // 如果没有默认设置，创建一个默认设置
            settings = createDefaultSettings();
        }
        return settings;
    }
    
    // 保存采集设置
    @Transactional
    public CollectionSettings saveSettings(CollectionSettings settings) {
        CollectionSettings existing = collectionSettingsRepository.findByIsDefaultTrue();
        if (existing == null) {
            settings.setIsDefault(true);
            return collectionSettingsRepository.save(settings);
        }
        existing.setFrequency(settings.getFrequency());
        existing.setStoragePath(settings.getStoragePath());
        existing.setPoints(settings.getPoints());
        existing.setFurnaceId(settings.getFurnaceId());
        existing.setScriptTemplateKey(settings.getScriptTemplateKey());
        existing.setScriptSeed(settings.getScriptSeed());
        existing.setIsDefault(true);
        return collectionSettingsRepository.save(existing);
    }
    
    // 创建默认采集设置
    private CollectionSettings createDefaultSettings() {
        CollectionSettings settings = new CollectionSettings();
        settings.setFrequency("10s");
        settings.setStoragePath("D:/blast-furnace/data/collection");
        settings.setPoints(100);
        settings.setFurnaceId(sysConfigService.getString("system_default_furnace_id", "BF-001"));
        settings.setScriptTemplateKey("steady_day");
        settings.setScriptSeed(20260304L);
        settings.setIsDefault(true);
        return collectionSettingsRepository.save(settings);
    }
    
    // 初始化采集设置
    public void initCollectionSettings() {
        if (collectionSettingsRepository.count() == 0) {
            createDefaultSettings();
        }
    }
}
