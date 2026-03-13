package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.StorageDevice;
import com.blastfurnace.backend.repository.StorageDeviceRepository;
import com.blastfurnace.backend.dto.StorageStatusDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StorageDeviceService {
    @Autowired
    private StorageDeviceRepository storageDeviceRepository;
    
    public List<StorageDevice> findAll() {
        return storageDeviceRepository.findAll();
    }
    
    public StorageDevice findById(Long id) {
        return storageDeviceRepository.findById(id).orElse(null);
    }
    
    public StorageDevice save(StorageDevice storageDevice) {
        return storageDeviceRepository.save(storageDevice);
    }
    
    public void delete(Long id) {
        storageDeviceRepository.deleteById(id);
    }
    
    // 获取存储状态统计信息
    public StorageStatusDTO getStorageStatus() {
        List<StorageDevice> devices = storageDeviceRepository.findAll();
        StorageStatusDTO statusDTO = new StorageStatusDTO();
        
        // 计算总存储容量、已使用容量和剩余容量
        int totalStorage = 0;
        int usedStorage = 0;
        int remainingStorage = 0;
        
        for (StorageDevice device : devices) {
            totalStorage += device.getCapacity();
            usedStorage += device.getUsed();
            remainingStorage += device.getRemaining();
        }
        
        // 计算使用率
        int usedStoragePercentage = totalStorage > 0 ? (usedStorage * 100) / totalStorage : 0;
        
        // 设置统计信息
        statusDTO.setTotalStorage(totalStorage);
        statusDTO.setUsedStorage(usedStorage);
        statusDTO.setRemainingStorage(remainingStorage);
        statusDTO.setUsedStoragePercentage(usedStoragePercentage);
        statusDTO.setFileCount(12500); // 模拟数据文件数
        statusDTO.setLastBackupTime("2026-01-21 18:30:00"); // 模拟最近备份时间
        statusDTO.setBackupStatus("success"); // 模拟备份状态
        statusDTO.setStorageDevices(devices);
        
        return statusDTO;
    }
    
    // 初始化存储设备数据
    public void initStorageDevices() {
        if (storageDeviceRepository.count() == 0) {
            StorageDevice device1 = new StorageDevice(null, "主存储设备", "SSD", "online", 500, 350, 150, 70);
            StorageDevice device2 = new StorageDevice(null, "备份存储设备", "HDD", "online", 500, 300, 200, 60);
            StorageDevice device3 = new StorageDevice(null, "归档存储设备", "NAS", "offline", 2000, 1000, 1000, 50);
            storageDeviceRepository.saveAll(List.of(device1, device2, device3));
        }
    }
}