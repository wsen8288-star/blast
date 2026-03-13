package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.CollectionDevice;
import com.blastfurnace.backend.model.CollectionHistory;
import com.blastfurnace.backend.service.CollectionSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.Date;

@Service
public class DataInitializer {
    
    @Autowired
    private CollectionDeviceService collectionDeviceService;
    
    @Autowired
    private CollectionHistoryService collectionHistoryService;
    
    @Autowired
    private CollectionSettingsService collectionSettingsService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @PostConstruct
    public void initData() {
        // 初始化采集设备：仅当设备表为空时才插入默认设备
        if (collectionDeviceService.count() == 0) {
            CollectionDevice device1 = new CollectionDevice();
            device1.setName("高炉1传感器组");
            device1.setType("温度传感器");
            device1.setStatus("online");
            device1.setIp("192.168.1.101");
            device1.setDescription("高炉1的温度传感器组");
            collectionDeviceService.save(device1);
            
            CollectionDevice device2 = new CollectionDevice();
            device2.setName("高炉1传感器组");
            device2.setType("压力传感器");
            device2.setStatus("online");
            device2.setIp("192.168.1.102");
            device2.setDescription("高炉1的压力传感器组");
            collectionDeviceService.save(device2);
            
            CollectionDevice device3 = new CollectionDevice();
            device3.setName("高炉1传感器组");
            device3.setType("流量传感器");
            device3.setStatus("online");
            device3.setIp("192.168.1.103");
            device3.setDescription("高炉1的流量传感器组");
            collectionDeviceService.save(device3);
            
            CollectionDevice device4 = new CollectionDevice();
            device4.setName("高炉2传感器组");
            device4.setType("温度传感器");
            device4.setStatus("offline");
            device4.setIp("192.168.1.104");
            device4.setDescription("高炉2的温度传感器组");
            collectionDeviceService.save(device4);
            
            CollectionDevice device5 = new CollectionDevice();
            device5.setName("高炉2传感器组");
            device5.setType("压力传感器");
            device5.setStatus("online");
            device5.setIp("192.168.1.105");
            device5.setDescription("高炉2的压力传感器组");
            collectionDeviceService.save(device5);
        }
        
        // 注意：采集历史记录不再在系统启动时初始化
        // 采集历史应该由用户实际操作产生，不应该自动生成假数据
        
        // 初始化采集参数设置
        collectionSettingsService.initCollectionSettings();
        
        backfillHotMetalTemperature();
    }
    
    private void backfillHotMetalTemperature() {
        try {
            if (!columnExists("production_data", "hotMetal_Temperature")) {
                return;
            }
            boolean hasTempRedundant = columnExists("production_data", "temp_redundant");
            boolean hasSnake = columnExists("production_data", "hot_metal_temperature");
            if (!hasTempRedundant && !hasSnake) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE production_data SET `hotMetal_Temperature` = COALESCE(`hotMetal_Temperature`");
            if (hasTempRedundant) sb.append(", temp_redundant");
            if (hasSnake) sb.append(", hot_metal_temperature");
            sb.append(") WHERE `hotMetal_Temperature` IS NULL");
            jdbcTemplate.update(sb.toString());
        } catch (Exception ignored) {
        }
    }
    
    private boolean columnExists(String tableName, String columnName) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return cnt != null && cnt > 0;
    }
}
