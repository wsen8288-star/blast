package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.StorageDevice;
import com.blastfurnace.backend.model.StorageConfig;
import com.blastfurnace.backend.model.BackupHistory;
import com.blastfurnace.backend.service.StorageDeviceService;
import com.blastfurnace.backend.service.StorageConfigService;
import com.blastfurnace.backend.service.BackupService;
import com.blastfurnace.backend.dto.StorageStatusDTO;
import com.blastfurnace.backend.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data/storage")
public class StorageDeviceController {
    @Autowired
    private StorageDeviceService storageDeviceService;
    
    @Autowired
    private StorageConfigService storageConfigService;
    
    @Autowired
    private BackupService backupService;
    
    @GetMapping("/devices")
    public Result<List<StorageDevice>> getStorageDevices() {
        List<StorageDevice> devices = storageDeviceService.findAll();
        return Result.success(devices, "获取存储设备成功");
    }
    
    @GetMapping("/devices/{id}")
    public Result<StorageDevice> getStorageDevice(@PathVariable Long id) {
        StorageDevice device = storageDeviceService.findById(id);
        if (device == null) {
            return Result.error("存储设备不存在");
        }
        return Result.success(device, "获取存储设备成功");
    }
    
    @PostMapping("/devices")
    public Result<StorageDevice> createStorageDevice(@RequestBody StorageDevice storageDevice) {
        StorageDevice savedDevice = storageDeviceService.save(storageDevice);
        return Result.success(savedDevice, "创建存储设备成功");
    }
    
    @PutMapping("/devices/{id}")
    public Result<StorageDevice> updateStorageDevice(@PathVariable Long id, @RequestBody StorageDevice storageDevice) {
        StorageDevice existingDevice = storageDeviceService.findById(id);
        if (existingDevice == null) {
            return Result.error("存储设备不存在");
        }
        storageDevice.setId(id);
        StorageDevice updatedDevice = storageDeviceService.save(storageDevice);
        return Result.success(updatedDevice, "更新存储设备成功");
    }
    
    @DeleteMapping("/devices/{id}")
    public Result<?> deleteStorageDevice(@PathVariable Long id) {
        StorageDevice existingDevice = storageDeviceService.findById(id);
        if (existingDevice == null) {
            return Result.error("存储设备不存在");
        }
        storageDeviceService.delete(id);
        return Result.success(null, "删除存储设备成功");
    }
    
    @GetMapping("/status")
    public Result<StorageStatusDTO> getStorageStatus() {
        StorageStatusDTO statusDTO = storageDeviceService.getStorageStatus();
        return Result.success(statusDTO, "获取存储状态成功");
    }
    
    @GetMapping("/config")
    public Result<StorageConfig> getStorageConfig() {
        StorageConfig config = storageConfigService.getDefaultConfig();
        return Result.success(config, "获取存储配置成功");
    }
    
    @PostMapping("/config")
    public Result<StorageConfig> saveStorageConfig(@RequestBody StorageConfig config) {
        StorageConfig savedConfig = storageConfigService.saveConfig(config);
        return Result.success(savedConfig, "保存存储配置成功");
    }
    
    @PostMapping("/backup")
    public Result<Map<String, Object>> startBackup(HttpServletRequest httpRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String operatorName = authentication != null && authentication.getName() != null ? authentication.getName() : "anonymous";
        String sourceIp = extractSourceIp(httpRequest);
        return Result.success(backupService.startManualBackupTask(operatorName, sourceIp), "备份任务已提交");
    }
    
    @GetMapping("/backup/history")
    public Result<List<BackupHistory>> getBackupHistory() {
        List<BackupHistory> history = backupService.getBackupHistory();
        return Result.success(history, "获取备份历史成功");
    }
    
    @PostMapping("/restore")
    public Result<Map<String, Object>> restoreData(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String backupPoint = request.get("backupPoint");
        if (backupPoint == null || backupPoint.isBlank()) {
            backupPoint = request.get("backupTime");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String operatorName = authentication != null && authentication.getName() != null ? authentication.getName() : "anonymous";
        String sourceIp = extractSourceIp(httpRequest);
        return Result.success(backupService.startRestoreTask(backupPoint, operatorName, sourceIp), "恢复任务已提交");
    }

    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> getTask(@PathVariable String taskId) {
        return Result.success(backupService.getTaskStatus(taskId), "获取任务状态成功");
    }
    
    @DeleteMapping("/backup/history/{id}")
    public Result<?> deleteBackupHistory(@PathVariable Long id) {
        boolean success = backupService.deleteBackupHistory(id);
        if (success) {
            return Result.success(null, "删除备份历史成功");
        } else {
            return Result.error("删除备份历史失败");
        }
    }

    private String extractSourceIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
