package com.blastfurnace.backend.controller;
import com.blastfurnace.backend.model.CollectionDevice;
import com.blastfurnace.backend.model.CollectionHistory;
import com.blastfurnace.backend.model.CollectionSettings;
import com.blastfurnace.backend.service.CollectionDeviceService;
import com.blastfurnace.backend.service.CollectionHistoryService;
import com.blastfurnace.backend.service.CollectionTaskService;
import com.blastfurnace.backend.service.CollectionSettingsService;
import com.blastfurnace.backend.service.AnomalyDetectionService;
import com.blastfurnace.backend.service.SysConfigService;
import com.blastfurnace.backend.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collection")
public class CollectionController {
    
    @Autowired
    private CollectionDeviceService collectionDeviceService;
    
    @Autowired
    private CollectionHistoryService collectionHistoryService;
    
    @Autowired
    private CollectionTaskService collectionTaskService;
    
    @Autowired
    private CollectionSettingsService collectionSettingsService;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private SysConfigService sysConfigService;
    
    // 当前运行的任务ID
    private volatile String currentTaskId;
    
    // 获取采集设备列表
    @GetMapping("/devices")
    public Result<List<CollectionDevice>> getCollectionDevices() {
        try {
            List<CollectionDevice> devices = collectionDeviceService.findAll();
            System.out.println("获取采集设备列表，数量: " + devices.size());
            System.out.println("设备数据: " + devices);
            return Result.success(devices, "获取采集设备列表成功");
        } catch (Exception e) {
            return Result.error("获取采集设备列表失败: " + e.getMessage());
        }
    }

    // 获取采集状态
    @GetMapping("/status")
    public Result<Map<String, Object>> getCollectionStatus() {
        try {
            boolean isRunning = collectionTaskService.isTaskRunning();
            int taskCount = collectionTaskService.getRunningTaskCount();
            
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("isRunning", isRunning);
            status.put("taskCount", taskCount);
            status.put("currentTaskId", currentTaskId);
            
            return Result.success(status, "获取采集状态成功");
        } catch (Exception e) {
            return Result.error("获取采集状态失败: " + e.getMessage());
        }
    }
    
    // 获取采集历史记录
    @GetMapping("/history")
    public Result<List<CollectionHistory>> getCollectionHistory(
            @RequestParam(value = "taskName", required = false) String taskName,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        try {
            System.out.println("接收到的查询参数 - 任务名: " + taskName + ", 开始日期: " + startDate + ", 结束日期: " + endDate);
            List<CollectionHistory> historyList = collectionHistoryService.search(taskName, startDate, endDate);
            System.out.println("获取采集历史记录，数量: " + historyList.size());
            return Result.success(historyList, "获取采集历史记录成功");
        } catch (Exception e) {
            System.out.println("获取采集历史记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取采集历史记录失败: " + e.getMessage());
        }
    }
    
    // 开始采集
    @PostMapping("/start")
    public Result<CollectionHistory> startCollection(@RequestBody Map<String, Object> params) {
        try {
            System.out.println("开始采集数据，参数: " + params);
            
            CollectionSettings defaultSettings = collectionSettingsService.getDefaultSettings();
            String runId = params.get("runId") != null ? params.get("runId").toString().trim() : null;
            String frequency = readStringOrDefault(params.get("frequency"), defaultSettings.getFrequency(), "10s");
            String storagePath = readStringOrDefault(params.get("storagePath"), defaultSettings.getStoragePath(), "data/collection");
            String furnaceId = readStringOrDefault(params.get("furnaceId"), defaultSettings.getFurnaceId(), null);
            String scriptTemplateKey = readStringOrDefault(params.get("scriptTemplateKey"), defaultSettings.getScriptTemplateKey(), "steady_day");
            Long scriptSeed = readLongOrDefault(params.get("scriptSeed"), defaultSettings.getScriptSeed());
            Integer points = readIntOrDefault(params.get("points"), defaultSettings.getPoints(), 100);
            if (frequency.isEmpty()) {
                frequency = defaultSettings.getFrequency();
            }
            if (storagePath.isEmpty()) {
                storagePath = defaultSettings.getStoragePath();
            }
            String furnaceLabel = "ALL";
            if (furnaceId != null && !furnaceId.isBlank()) {
                furnaceLabel = "RANDOM_THREE".equalsIgnoreCase(furnaceId) ? "RANDOM_THREE" : furnaceId;
            }
            
            // 创建采集历史记录
            CollectionHistory history = new CollectionHistory();
            history.setTaskName("数据采集任务");
            history.setStartTime(new java.util.Date());
            history.setStatus("running");
            history.setRecordCount(0);
            if (runId != null && !runId.isBlank()) {
                history.setRunId(runId);
            }
            
            // 保存采集参数到描述字段
            String description = "采集频率: " + frequency + ", 存储路径: " + storagePath + ", 采集点数: " + points
                    + ", 高炉: " + furnaceLabel
                    + ", 工况模板: " + scriptTemplateKey + (scriptSeed == null ? "" : (", 脚本种子: " + scriptSeed));
            history.setDescription(description);
            
            // 保存到数据库
            CollectionHistory savedHistory = collectionHistoryService.save(history);
            
            // 启动采集任务
            String taskId = collectionTaskService.startTask(
                    frequency,
                    points,
                    savedHistory.getId(),
                    storagePath,
                    furnaceId,
                    scriptTemplateKey,
                    scriptSeed
            );
            currentTaskId = taskId;

            String anomalyFurnaceId = normalizeAnomalyFurnaceId(furnaceId);
            anomalyDetectionService.startScheduledDetection(
                    anomalyFurnaceId,
                    List.of(
                            "temperature",
                            "pressure",
                            "materialHeight",
                            "gasFlow",
                            "oxygenLevel",
                            "productionRate",
                            "energyConsumption",
                            "hotMetalTemperature",
                            "siliconContent"
                    ),
                    "ALL",
                    10,
                    anomalyFurnaceId == null ? 150 : 50
            );
            
            System.out.println("开始执行采集任务，频率: " + frequency + "，目标点数: " + points);
            System.out.println("数据将存储到: " + storagePath);
            System.out.println("任务ID: " + taskId);
            
            return Result.success(savedHistory, "开始采集数据成功");
        } catch (Exception e) {
            return Result.error("开始采集数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/scenario-templates")
    public Result<List<Map<String, Object>>> getScenarioTemplates() {
        try {
            return Result.success(collectionTaskService.getScenarioTemplates(), "获取工况脚本模板成功");
        } catch (Exception e) {
            return Result.error("获取工况脚本模板失败: " + e.getMessage());
        }
    }
    
    // 停止采集
    @PostMapping("/stop")
    public Result<String> stopCollection() {
        try {
            System.out.println("停止采集数据");
            
            // 停止当前运行的采集任务，状态更新已移至 Service 层
            if (currentTaskId != null) {
                int collectedPoints = collectionTaskService.stopTask(currentTaskId);
                System.out.println("停止采集任务: " + currentTaskId + "，采集点数: " + collectedPoints);
                currentTaskId = null;
            } else {
                // 如果没有 currentTaskId，兜底查找运行中的历史记录并关闭
                List<CollectionHistory> historyList = collectionHistoryService.findAll();
                for (CollectionHistory history : historyList) {
                    if ("running".equals(history.getStatus())) {
                        history.setStatus("completed");
                        history.setEndTime(new java.util.Date());
                        collectionHistoryService.save(history);
                    }
                }
            }
            anomalyDetectionService.stopScheduledDetection();
            
            return Result.success("停止采集数据成功");
        } catch (Exception e) {
            return Result.error("停止采集数据失败: " + e.getMessage());
        }
    }

    // 下载采集历史文件
    @GetMapping("/history/{id}/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadHistoryFile(@PathVariable Long id) {
        try {
            CollectionHistory history = collectionHistoryService.findById(id);
            if (history == null) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            String filePath = history.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
            if (resource.exists() && resource.isReadable()) {
                String filename = path.getFileName().toString();
                // 处理文件名编码，防止中文乱码
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
                
                return org.springframework.http.ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                        .body(resource);
            } else {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
    
    // 保存采集设置
    @PostMapping("/settings")
    public Result<Map<String, Object>> saveCollectionSettings(@RequestBody Map<String, Object> settings) {
        try {
            System.out.println("保存采集设置: " + settings);
            CollectionSettings defaultSettings = collectionSettingsService.getDefaultSettings();
            
            CollectionSettings collectionSettings = new CollectionSettings();
            String defaultFurnaceId = sysConfigService.getString("system_default_furnace_id", "BF-001");
            collectionSettings.setFrequency(readStringOrDefault(settings.get("frequency"), defaultSettings.getFrequency(), "10s"));
            collectionSettings.setStoragePath(readStringOrDefault(settings.get("storagePath"), defaultSettings.getStoragePath(), "data/collection"));
            collectionSettings.setPoints(readIntOrDefault(settings.get("points"), defaultSettings.getPoints(), 100));
            collectionSettings.setFurnaceId(readStringOrDefault(settings.get("furnaceId"), defaultSettings.getFurnaceId(), defaultFurnaceId));
            collectionSettings.setScriptTemplateKey(readStringOrDefault(settings.get("scriptTemplateKey"), defaultSettings.getScriptTemplateKey(), "steady_day"));
            collectionSettings.setScriptSeed(readLongOrDefault(settings.get("scriptSeed"), defaultSettings.getScriptSeed()));
            
            collectionSettingsService.saveSettings(collectionSettings);

            Map<String, Object> saved = new java.util.HashMap<>();
            saved.put("frequency", collectionSettings.getFrequency());
            saved.put("storagePath", collectionSettings.getStoragePath());
            saved.put("points", collectionSettings.getPoints());
            saved.put("furnaceId", collectionSettings.getFurnaceId());
            saved.put("scriptTemplateKey", collectionSettings.getScriptTemplateKey());
            saved.put("scriptSeed", collectionSettings.getScriptSeed());
            return Result.success(saved, "保存采集设置成功");
        } catch (Exception e) {
            return Result.error("保存采集设置失败: " + e.getMessage());
        }
    }
    
    // 获取采集设置
    @GetMapping("/settings")
    public Result<Map<String, Object>> getCollectionSettings() {
        try {
            // 从数据库中获取设置
            CollectionSettings collectionSettings = collectionSettingsService.getDefaultSettings();
            
            Map<String, Object> settings = new java.util.HashMap<>();
            settings.put("frequency", collectionSettings.getFrequency());
            settings.put("storagePath", collectionSettings.getStoragePath());
            settings.put("points", collectionSettings.getPoints());
            settings.put("furnaceId", collectionSettings.getFurnaceId());
            settings.put("scriptTemplateKey", collectionSettings.getScriptTemplateKey());
            settings.put("scriptSeed", collectionSettings.getScriptSeed());
            
            System.out.println("获取采集设置: " + settings);
            return Result.success(settings, "获取采集设置成功");
        } catch (Exception e) {
            return Result.error("获取采集设置失败: " + e.getMessage());
        }
    }
    
    // 删除采集历史记录
    @DeleteMapping("/history/{id}")
    public Result<String> deleteCollectionHistory(@PathVariable Long id) {
        try {
            collectionHistoryService.delete(id);
            return Result.success("删除采集历史记录成功");
        } catch (Exception e) {
            return Result.error("删除采集历史记录失败: " + e.getMessage());
        }
    }
    
    // 根据文件路径删除对应的采集历史记录
    @PostMapping("/file/delete")
    public Result<String> deleteHistoryByFilePath(@RequestBody Map<String, String> request) {
        try {
            String filePath = request.get("filePath");
            if (filePath != null && !filePath.isEmpty()) {
                collectionHistoryService.deleteByFilePath(filePath);
                return Result.success("删除采集历史记录成功");
            } else {
                return Result.error("文件路径不能为空");
            }
        } catch (Exception e) {
            return Result.error("删除采集历史记录失败: " + e.getMessage());
        }
    }

    private String normalizeAnomalyFurnaceId(String furnaceId) {
        if (furnaceId == null || furnaceId.isBlank()) {
            return null;
        }
        String normalized = furnaceId.trim();
        if ("RANDOM_THREE".equalsIgnoreCase(normalized) || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String readStringOrDefault(Object raw, String fallback, String hardDefault) {
        if (raw != null) {
            String value = raw.toString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return hardDefault;
    }

    private Integer readIntOrDefault(Object raw, Integer fallback, Integer hardDefault) {
        if (raw != null && !raw.toString().isBlank()) {
            return Integer.parseInt(raw.toString().trim());
        }
        if (fallback != null) {
            return fallback;
        }
        return hardDefault;
    }

    private Long readLongOrDefault(Object raw, Long fallback) {
        if (raw != null && !raw.toString().isBlank()) {
            return Long.parseLong(raw.toString().trim());
        }
        return fallback;
    }
}
