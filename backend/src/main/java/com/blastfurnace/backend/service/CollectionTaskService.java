package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.CollectionHistory;
import com.blastfurnace.backend.model.CollectionSettings;
import com.blastfurnace.backend.model.ProductionData;
import com.blastfurnace.backend.repository.ProductionDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CollectionTaskService {
    
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    
    @Autowired
    private ProductionDataRepository productionDataRepository;
    
    @Autowired
    private CollectionHistoryService collectionHistoryService;

    @Autowired
    private CollectionSettingsService collectionSettingsService;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @jakarta.annotation.PostConstruct
    public void init() {
        System.out.println("CollectionTaskService initialized.");
    }

    // 管理正在运行的任务
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> taskCounters = new ConcurrentHashMap<>();
    private final Map<String, TaskParams> taskParams = new ConcurrentHashMap<>();
    private final Map<String, List<ProductionData>> taskData = new ConcurrentHashMap<>();
    private final Map<String, Long> taskHistoryIds = new ConcurrentHashMap<>();
    private final Map<String, ScenarioRuntime> previewScenarioStates = new ConcurrentHashMap<>();
    private final Map<String, ScenarioRuntime> taskScenarioStates = new ConcurrentHashMap<>();
    
    // 全局剧本计数器
    private AtomicInteger globalMockCounter = new AtomicInteger(0);

    private static class TaskParams {
        private final int targetPoints;
        private final String taskId;
        private final String storagePath;
        private final String scriptTemplateKey;
        private final Long scriptSeed;
        
        public TaskParams(int targetPoints, String taskId, String storagePath, String scriptTemplateKey, Long scriptSeed) {
            this.targetPoints = targetPoints;
            this.taskId = taskId;
            this.storagePath = storagePath;
            this.scriptTemplateKey = scriptTemplateKey;
            this.scriptSeed = scriptSeed;
        }
        public int getTargetPoints() { return targetPoints; }
        public String getTaskId() { return taskId; }
        public String getStoragePath() { return storagePath; }
        public String getScriptTemplateKey() { return scriptTemplateKey; }
        public Long getScriptSeed() { return scriptSeed; }
    }

    private record PhaseScript(
            String key,
            String label,
            int legacyPhase,
            double ratio,
            int minSteps,
            int maxSteps,
            double disturbance,
            double anomalyProb
    ) {
    }

    private record ScenarioTemplate(
            String key,
            String label,
            String description,
            List<PhaseScript> phases
    ) {
    }

    private static class ScenarioRuntime {
        private final ScenarioTemplate template;
        private final Random random;
        private final List<PhaseScript> normalPhases;
        private final PhaseScript abnormalPhase;
        private PhaseScript currentPhase;
        private int remainSteps;
        private int currentNormalIndex;
        private int abnormalCooldown;

        private ScenarioRuntime(ScenarioTemplate template, long seed) {
            this.template = template;
            this.random = new Random(seed);
            List<PhaseScript> normals = new ArrayList<>();
            PhaseScript abnormal = null;
            for (PhaseScript phase : template.phases) {
                if ("abnormal".equalsIgnoreCase(phase.key()) || phase.legacyPhase() == 4) {
                    abnormal = phase;
                } else {
                    normals.add(phase);
                }
            }
            this.normalPhases = normals;
            this.abnormalPhase = abnormal;
        }

        private PhaseScript nextStepPhase() {
            if (currentPhase == null || remainSteps <= 0) {
                currentPhase = pickOrderedPhase();
                remainSteps = pickDuration(currentPhase);
            }
            remainSteps--;
            return currentPhase;
        }

        private PhaseScript pickOrderedPhase() {
            if (abnormalPhase != null && abnormalCooldown <= 0 && random.nextDouble() < abnormalPhase.ratio()) {
                abnormalCooldown = Math.max(2, normalPhases.size());
                return abnormalPhase;
            }
            if (normalPhases.isEmpty()) {
                return template.phases.get(0);
            }
            PhaseScript phase = normalPhases.get(currentNormalIndex);
            currentNormalIndex = (currentNormalIndex + 1) % normalPhases.size();
            if (abnormalCooldown > 0) {
                abnormalCooldown--;
            }
            return phase;
        }

        private int pickDuration(PhaseScript phase) {
            int min = Math.max(1, phase.minSteps());
            int max = Math.max(min, phase.maxSteps());
            if (max == min) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }
    }

    private static final Map<String, ScenarioTemplate> SCENARIO_TEMPLATES = buildScenarioTemplates();

    private static Map<String, ScenarioTemplate> buildScenarioTemplates() {
        Map<String, ScenarioTemplate> map = new LinkedHashMap<>();
        map.put("steady_day", new ScenarioTemplate(
                "steady_day",
                "稳态日",
                "以冶炼阶段为主，异常占比低，适合稳定工况演示",
                List.of(
                        new PhaseScript("charging", "装料中", 0, 0.18, 6, 12, 0.6, 0.002),
                        new PhaseScript("blasting", "送风中", 1, 0.22, 8, 14, 0.7, 0.003),
                        new PhaseScript("smelting", "冶炼中", 2, 0.46, 10, 18, 0.6, 0.002),
                        new PhaseScript("tapping", "出铁中", 3, 0.12, 5, 10, 0.7, 0.003),
                        new PhaseScript("abnormal", "波动调整", 4, 0.02, 2, 5, 1.2, 0.05)
                )
        ));
        map.put("disturbed_day", new ScenarioTemplate(
                "disturbed_day",
                "扰动日",
                "异常与波动阶段占比高，适合预警与鲁棒性验证",
                List.of(
                        new PhaseScript("charging", "装料中", 0, 0.14, 4, 9, 0.9, 0.01),
                        new PhaseScript("blasting", "送风中", 1, 0.20, 5, 11, 1.0, 0.015),
                        new PhaseScript("smelting", "冶炼中", 2, 0.34, 6, 12, 1.0, 0.012),
                        new PhaseScript("tapping", "出铁中", 3, 0.14, 4, 8, 1.1, 0.02),
                        new PhaseScript("abnormal", "波动调整", 4, 0.18, 3, 7, 1.6, 0.12)
                )
        ));
        return map;
    }
    
    public String startTask(String frequency, Integer points, Long historyId, String storagePath) {
        return startTask(frequency, points, historyId, storagePath, null, null, null);
    }

    public String startTask(String frequency, Integer points, Long historyId, String storagePath, String furnaceId) {
        return startTask(frequency, points, historyId, storagePath, furnaceId, null, null);
    }

    public String startTask(
            String frequency,
            Integer points,
            Long historyId,
            String storagePath,
            String furnaceId,
            String scriptTemplateKey,
            Long scriptSeed
    ) {
        System.out.println("[DEBUG_FIX] startTask called with storagePath: [" + storagePath + "]");
        String taskId = "collection-task-" + System.currentTimeMillis();
        long period = parseFrequency(frequency);
        
        taskCounters.put(taskId, new AtomicInteger(0));
        taskData.put(taskId, new ArrayList<>());
        taskHistoryIds.put(taskId, historyId);
        
        int targetPoints = points != null ? points : Integer.MAX_VALUE;
        String normalizedStoragePath = storagePath != null ? storagePath.trim() : null;
        String normalizedFurnaceId = normalizeFurnaceId(furnaceId);
        String templateKey = normalizeTemplateKey(scriptTemplateKey);
        taskParams.put(taskId, new TaskParams(targetPoints, taskId, normalizedStoragePath, templateKey, scriptSeed));
        
        // 每次开始任务重置剧本
        globalMockCounter.set(0);

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                // 【核心】使用强制剧本生成数据
                ProductionData data = generateDeterministicMockData(normalizedFurnaceId, taskId);
                data.setCollectionHistoryId(historyId);
                
                productionDataRepository.save(data);
                anomalyDetectionService.detectCollectedPoint(
                        data,
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
                        "ALL"
                );
                taskData.get(taskId).add(data);
                
                int count = taskCounters.get(taskId).incrementAndGet();
                System.out.println("采集任务 " + taskId + "：已采集 " + count + " 条 (" + data.getStatus() + ")");
                
                if (count >= targetPoints) {
                    stopTask(taskId);
                }
            } catch (Exception e) {
                System.err.println("采集任务异常: " + e.getMessage());
                e.printStackTrace();
            }
        }, period);
        
        runningTasks.put(taskId, future);
        return taskId;
    }
    
    public int stopTask(String taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
        int collectedPoints = 0;
        
        if (future != null) {
            future.cancel(true);
            AtomicInteger counter = taskCounters.remove(taskId);
            collectedPoints = counter != null ? counter.get() : 0;
            
            TaskParams params = taskParams.remove(taskId);
            List<ProductionData> dataList = taskData.remove(taskId);
            Long historyId = taskHistoryIds.remove(taskId);
            taskScenarioStates.keySet().removeIf(key -> key.startsWith(taskId + "|"));
            
            // 统一在 stopTask 中更新历史记录状态
            if (historyId != null) {
                CollectionHistory history = collectionHistoryService.findById(historyId);
                if (history != null) {
                    history.setEndTime(new Date());
                    history.setStatus("completed");
                    history.setRecordCount(collectedPoints);
                    
                    // 尝试保存文件
                    if (params != null && dataList != null && !dataList.isEmpty()) {
                        String filePath = saveDataToFile(taskId, dataList, params.getStoragePath());
                        if (filePath != null) {
                            history.setFilePath(filePath);
                            System.out.println("任务 " + taskId + " 数据已保存至: " + filePath);
                        } else {
                            System.err.println("任务 " + taskId + " 数据保存失败，请检查磁盘空间或权限");
                        }
                    } else {
                        System.err.println("[CRITICAL] Task " + taskId + " stopped but NO DATA to save!");
                        if (params == null) System.err.println("  Reason: params is null");
                        if (dataList == null) System.err.println("  Reason: dataList is null");
                        else if (dataList.isEmpty()) System.err.println("  Reason: dataList is empty (Task ran for too short time?)");
                    }
                    
                    collectionHistoryService.save(history);
                }
            }
        }
        return collectedPoints;
    }
    
    public void stopAllTasks() {
        for (String taskId : runningTasks.keySet()) stopTask(taskId);
    }
    
    private String saveDataToFile(String taskId, List<ProductionData> dataList, String storagePath) {
        System.err.println("[CRITICAL] saveDataToFile called. taskId=" + taskId + ", listSize=" + (dataList==null?0:dataList.size()) + ", rawPath=[" + storagePath + "]");
        
        String resolvedPath = resolveStoragePath(storagePath);
        System.err.println("[CRITICAL] resolvedPath=[" + resolvedPath + "]");
        
        if (resolvedPath == null || resolvedPath.isEmpty()) {
            System.err.println("[CRITICAL] resolvedPath is null/empty! Aborting.");
            return null;
        }

        // 尝试写入指定路径
        return writeCsvFile(resolvedPath, taskId, dataList);
    }

    private String writeCsvFile(String basePath, String taskId, List<ProductionData> dataList) {
        try {
            System.out.println("[DEBUG_FIX] writeCsvFile called with basePath=[" + basePath + "]");
            
            if (basePath == null || basePath.isEmpty()) {
                throw new IOException("Base path is empty");
            }

            // 打印路径字符 ASCII 码，排查隐形字符
            System.out.print("[DEBUG_FIX] Path chars: ");
            for (char c : basePath.toCharArray()) {
                System.out.print((int)c + " ");
            }
            System.out.println();

            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            System.out.println("[DEBUG_FIX] Normalized path: " + base);
            
            boolean isFilePath = basePath.toLowerCase().endsWith(".csv");
            Path targetDir = isFilePath ? base.getParent() : base;
            
            if (targetDir == null) {
                targetDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            }
            
            // 强制尝试创建目录，不依赖 Files.exists
            File dir = targetDir.toFile();
            if (!dir.exists()) {
                System.out.println("[DEBUG_FIX] Directory does not exist, creating: " + dir.getAbsolutePath());
                if (!dir.mkdirs()) {
                    System.err.println("[DEBUG_FIX] Failed to create directory using File.mkdirs(): " + dir.getAbsolutePath());
                    // 尝试 NIO 创建
                    try {
                        Files.createDirectories(targetDir);
                    } catch (IOException e) {
                        throw new IOException("无法创建目录 (mkdirs & NIO failed): " + targetDir, e);
                    }
                }
            }
            
            if (!dir.isDirectory()) {
                throw new IOException("Path is not a directory: " + dir.getAbsolutePath());
            }
            if (!dir.canWrite()) {
                    // 尝试修改权限 (允许所有人写入)
                    dir.setWritable(true, false);
                    if (!dir.canWrite()) {
                         throw new IOException("Path is not writable: " + dir.getAbsolutePath());
                    }
                } else {
                    // 即使认为可写，也尝试强制放宽权限，防止 owner 问题
                    dir.setWritable(true, false);
                }

                Path filePath;
            if (isFilePath) {
                filePath = base;
            } else {
                filePath = targetDir.resolve("collection_" + taskId + "_" + System.currentTimeMillis() + ".csv");
            }
            
            String finalPath = filePath.toAbsolutePath().toString();
            System.out.println("[DEBUG_FIX] Final file path: " + finalPath);
            
            byte[] contentBytes = buildCsvBytes(dataList);
            System.out.println("[DEBUG_FIX] Writing bytes: " + contentBytes.length);
            
            // 使用 FileOutputStream 替代 Files.write，避免 NIO NoSuchFileException 误报
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(contentBytes);
                fos.flush();
                fos.getFD().sync(); // 强制刷盘
            }
            
            System.out.println("[DEBUG_FIX] File written successfully via FileOutputStream");
            return finalPath;
        } catch (Throwable e) {
            System.out.println("Write failed, base path: " + basePath);
            System.out.println("[DEBUG_FIX] Exception in writeCsvFile: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            return null;
        }
    }

    private void writeBytes(Path filePath, byte[] contentBytes) throws IOException {
        File targetFile = filePath.toFile();
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(contentBytes);
            fos.getFD().sync();
        }
    }

    private byte[] buildCsvBytes(List<ProductionData> dataList) {
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("时间戳,高炉编号,温度,压力,风量,喷煤量,料面高度,煤气流量,氧气含量,生产率,能耗,铁水温度,铁水含硅量,常量信号,状态\n");
        if (dataList != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (ProductionData data : dataList) {
                sb.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    data.getTimestamp() != null ? sdf.format(data.getTimestamp()) : "",
                    data.getFurnaceId() == null ? "" : data.getFurnaceId(),
                    formatNullable(data.getTemperature()), formatNullable(data.getPressure()),
                    formatNullable(data.getWindVolume()), formatNullable(data.getCoalInjection()),
                    formatNullable(data.getMaterialHeight()), formatNullable(data.getGasFlow()),
                    formatNullable(data.getOxygenLevel()), formatNullable(data.getProductionRate()),
                    formatNullable(data.getEnergyConsumption()), formatNullable(data.getHotMetalTemperature()),
                    formatNullable(data.getSiliconContent()), formatNullable(data.getConstantSignal()),
                    data.getStatus() == null ? "" : data.getStatus()));
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String resolveStoragePath(String storagePath) {
        System.out.println("[SANDBOX-DEBUG] resolveStoragePath called with: [" + storagePath + "]");
        String resolved = storagePath != null ? storagePath.trim() : "";
        
        // 1. 如果为空，使用默认配置
        if (resolved.isEmpty()) {
            CollectionSettings settings = collectionSettingsService.getDefaultSettings();
            resolved = settings != null ? settings.getStoragePath() : "";
            System.out.println("[SANDBOX-DEBUG] resolved from settings: [" + resolved + "]");
        }
        
        // 2. 如果仍为空，使用系统默认目录
        if (resolved == null || resolved.isEmpty()) {
            resolved = "data/collection";
            System.out.println("[SANDBOX-DEBUG] resolved from default: [" + resolved + "]");
        }
        
        // 3. 【Trae IDE 适配】检查是否存在 d_mount 挂载点（Junction）
        try {
            File junctionDir = new File("d_mount");
            if (junctionDir.exists() && junctionDir.isDirectory()) {
                System.out.println("[SANDBOX-DEBUG] d_mount detected.");
                String normalized = resolved.replace("\\", "/");
                
                // 检查是否为 D 盘路径 (不区分大小写)
                if (normalized.toUpperCase().startsWith("D:")) {
                     String relativePart = normalized.substring(2); // 去掉 D:
                     if (relativePart.startsWith("/")) relativePart = relativePart.substring(1);
                     
                     File newPath = new File(junctionDir, relativePart);
                     String newAbsPath = newPath.getAbsolutePath();
                     System.out.println("[SANDBOX-DEBUG] Rewriting [" + resolved + "] -> [" + newAbsPath + "]");
                     return newAbsPath;
                }
            } else {
                 System.out.println("[SANDBOX-DEBUG] d_mount NOT detected.");
            }
        } catch (Exception e) {
             System.err.println("[SANDBOX-DEBUG] Error checking junction: " + e.getMessage());
        }

        try {
            // 4. 去除首尾引号
            if (resolved.startsWith("\"") && resolved.endsWith("\"") && resolved.length() >= 2) {
                resolved = resolved.substring(1, resolved.length() - 1).trim();
            }

            // 5. 使用 NIO Path 处理路径
            Path path = Paths.get(resolved);
            
            // 6. 如果是相对路径，解析为绝对路径 (基于 user.dir)
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path);
            }
            
            // 7. 规范化路径 (去除 ./ 和 ../)
            String finalPath = path.normalize().toAbsolutePath().toString();
            System.out.println("[DEBUG_FIX] resolveStoragePath result: [" + finalPath + "]");
            return finalPath;
            
        } catch (Exception e) {
            System.out.println("[DEBUG_FIX] Path resolution failed: " + resolved + ", Error: " + e.getMessage());
            e.printStackTrace(System.out);
            // 严格模式：解析失败则直接返回null，不进行降级
            return null;
        }
    }

    private String toCodePoints(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i > 0) sb.append(',');
            sb.append((int) value.charAt(i));
        }
        return sb.toString();
    }
    
    // 关键辅助方法：处理 Double null
    private String formatNullable(Double val) {
        return val == null ? "" : String.format("%.2f", val);
    }
    
    private long parseFrequency(String frequency) {
        if (frequency == null || frequency.isEmpty()) return 10000;
        try {
            long val = Long.parseLong(frequency.substring(0, frequency.length() - 1));
            if (frequency.endsWith("s")) return val * 1000;
            if (frequency.endsWith("m")) return val * 60 * 1000;
            if (frequency.endsWith("h")) return val * 60 * 60 * 1000;
        } catch (Exception e) { }
        return 10000;
    }

    
    // --- 物理仿真引擎 (Physics Simulator Engine) ---
    
    private static class SimulatorState {
        // 状态变量 (State Variables)
        double currentTemp = 1250.0;    // 当前炉温
        double targetTemp = 1250.0;     // 目标炉温
        double productionBase = 50.0;   // 基础产量
        double burdenLevel = 4.6;       // 料柱水平
        
        // 历史队列 (用于滞后计算)
        List<Double> tempHistory = new ArrayList<>();
        int lagSteps = 15; // 滞后步数
        
        // 随机游走参数 (Random Walk)
        double windVolume = 5000.0; 
        double topPressure = 175.0; 
        double coalInjection = 150.0;
        double oxygenEnrichment = 2.0;
        
        void update(int phase, double disturbance, Random random) {
            double targetWind = 5000.0;
            double targetPressure = 175.0;
            double targetCoal = 150.0;
            double targetOxygen = 2.2;
            double targetBurden = 4.6;
            
            switch (phase) {
                case 0:
                    targetWind = 4300.0;
                    targetPressure = 150.0;
                    targetCoal = 140.0;
                    targetOxygen = 1.8;
                    targetBurden = 5.6;
                    break;
                case 1:
                    targetWind = 5000.0;
                    targetPressure = 170.0;
                    targetCoal = 150.0;
                    targetOxygen = 2.1;
                    targetBurden = 4.8;
                    break;
                case 2:
                    targetWind = 5500.0;
                    targetPressure = 188.0;
                    targetCoal = 162.0;
                    targetOxygen = 2.5;
                    targetBurden = 4.2;
                    break;
                case 3:
                    targetWind = 5200.0;
                    targetPressure = 180.0;
                    targetCoal = 156.0;
                    targetOxygen = 2.3;
                    targetBurden = 3.7;
                    break;
                case 4:
                    targetWind = 5900.0 + (random.nextDouble() - 0.5) * 1200.0 * disturbance;
                    targetPressure = 205.0 + (random.nextDouble() - 0.5) * 60.0 * disturbance;
                    targetCoal = 175.0 + (random.nextDouble() - 0.5) * 20.0 * disturbance;
                    targetOxygen = 2.9 + (random.nextDouble() - 0.5) * 0.8 * disturbance;
                    targetBurden = 3.4 + (random.nextDouble() - 0.5) * 0.8 * disturbance;
                    break;
            }
            
            windVolume += (targetWind - windVolume) * 0.14 + (random.nextDouble() - 0.5) * 120.0 * disturbance;
            topPressure += (targetPressure - topPressure) * 0.16 + (random.nextDouble() - 0.5) * 6.0 * disturbance;
            coalInjection += (targetCoal - coalInjection) * 0.12 + (random.nextDouble() - 0.5) * 3.2 * disturbance;
            oxygenEnrichment += (targetOxygen - oxygenEnrichment) * 0.15 + (random.nextDouble() - 0.5) * 0.18 * disturbance;
            burdenLevel += (targetBurden - burdenLevel) * 0.18 + (random.nextDouble() - 0.5) * 0.12 * disturbance;

            windVolume = Math.max(3200.0, Math.min(6200.0, windVolume));
            topPressure = Math.max(110.0, Math.min(260.0, topPressure));
            coalInjection = Math.max(100.0, Math.min(220.0, coalInjection));
            oxygenEnrichment = Math.max(1.2, Math.min(4.2, oxygenEnrichment));
            burdenLevel = Math.max(3.0, Math.min(6.2, burdenLevel));

            productionBase =
                    48.0
                            + (windVolume - 4800.0) / 230.0
                            + (oxygenEnrichment - 2.0) * 2.2
                            - (coalInjection - 150.0) * 0.03
                            + (random.nextDouble() - 0.5) * 1.0 * disturbance;

            targetTemp =
                    1085.0
                            + 0.034 * windVolume
                            + 34.0 * oxygenEnrichment
                            + 0.18 * topPressure
                            - 0.22 * coalInjection;
            
            double alpha = 0.065;
            currentTemp += alpha * (targetTemp - currentTemp) + (random.nextDouble() - 0.5) * 8.0 * disturbance;
            
            tempHistory.add(currentTemp);
            if (tempHistory.size() > lagSteps + 1) {
                tempHistory.remove(0);
            }
        }
        
        double getLaggedTemp() {
            if (tempHistory.isEmpty()) return currentTemp;
            return tempHistory.get(0);
        }
    }

    private final Map<String, SimulatorState> simulatorStates = new ConcurrentHashMap<>();

    // 【优化】: 基于物理仿真的数据生成 (Physics-based Simulation)
    // 改为 public 以便 Controller 调用生成预览数据
    public ProductionData generateDeterministicMockData() {
        return generateDeterministicMockData(null, null);
    }
    
    // 重载方法，支持指定高炉ID（用于预览）
    public ProductionData generateDeterministicMockData(String targetFurnaceId) {
        return generateDeterministicMockData(targetFurnaceId, null);
    }

    private ProductionData generateDeterministicMockData(String targetFurnaceId, String taskId) {
        ProductionData data = new ProductionData();
        
        // 1. 确定高炉 ID
        String furnaceId;
        if (targetFurnaceId != null && !targetFurnaceId.isBlank()) {
            furnaceId = targetFurnaceId;
        } else {
            String[] furnaceIds = {"BF-001", "BF-002", "BF-003"};
            int furnaceIndex = Math.abs(globalMockCounter.getAndIncrement()) % furnaceIds.length;
            furnaceId = furnaceIds[furnaceIndex];
        }
        data.setFurnaceId(furnaceId);
        
        // 2. 获取或初始化仿真状态
        SimulatorState state = simulatorStates.computeIfAbsent(furnaceId, k -> new SimulatorState());
        
        data.setTimestamp(new Date());
        data.setOperator("AutoSim");

        ScenarioRuntime runtime = getScenarioRuntime(taskId, furnaceId);
        PhaseScript phaseScript = runtime.nextStepPhase();
        String status = phaseScript.label();
        int processPhase = phaseScript.legacyPhase();
        double disturbance = phaseScript.disturbance();
        Random random = runtime.random;
        
        // 4. 更新物理状态
        state.update(processPhase, disturbance, random);
        
        // 5. 计算输出指标 (基于物理公式)
        
        // [温度]
        double temp = state.currentTemp;
        
        // [压力] P ~ (WindVolume^2) * Resistance
        double pressure = state.topPressure;
        
        double laggedTemp = state.getLaggedTemp();
        double oxygenLevel =
                20.8
                        + state.oxygenEnrichment
                        - Math.max(0.0, state.windVolume - 5600.0) * 0.00025
                        + (random.nextDouble() - 0.5) * 0.9 * disturbance;
        double hotMetalTemperature =
                1465.0
                        + 0.24 * (laggedTemp - 1240.0)
                        + 0.05 * (state.topPressure - 175.0)
                        + (random.nextDouble() - 0.5) * 7.0 * disturbance;
        double siContent =
                0.35
                        + 0.0013 * (laggedTemp - 1245.0)
                        + 0.012 * (state.oxygenEnrichment - 2.0)
                        + 0.0005 * (state.coalInjection - 150.0)
                        + (random.nextDouble() - 0.5) * 0.06 * disturbance;
        siContent = Math.max(0.1, Math.min(1.0, siContent));

        double productionRate =
                state.productionBase
                        + (state.windVolume - 5000.0) / 95.0
                        + (state.oxygenEnrichment - 2.0) * 3.5
                        - (state.coalInjection - 150.0) * 0.015
                        + (random.nextDouble() - 0.5) * 2.5 * disturbance;

        double gasFlow =
                state.windVolume * (0.53 + (state.topPressure - 170.0) / 700.0)
                        + (random.nextDouble() - 0.5) * 180.0 * disturbance;

        double materialHeight =
                state.burdenLevel
                        - Math.max(0.0, productionRate - 50.0) * 0.01
                        + (random.nextDouble() - 0.5) * 0.35 * disturbance;
        if (processPhase == 0) {
            materialHeight += 0.25;
        }
        
        if (random.nextDouble() < phaseScript.anomalyProb()) {
            int anomalyType = random.nextInt(5);
            switch (anomalyType) {
                case 0 -> temp = 1500.0 + random.nextDouble() * 100.0;
                case 1 -> pressure = 50.0; // 压力骤降
                case 2 -> materialHeight = 1.0; // 料面异常
                case 3 -> oxygenLevel = 15.0; // 氧含量异常
                case 4 -> gasFlow = 5000.0; // 流量超限
            }
            status = "异常报警";
        }

        double energyConsumption =
                980.0
                        + (5500.0 - state.windVolume) * 0.06
                        + (state.coalInjection - 150.0) * 1.45
                        + (siContent - 0.45) * 170.0
                        + (1500.0 - hotMetalTemperature) * 0.22
                        + (pressure - 175.0) * 0.5
                        + (random.nextDouble() - 0.5) * 20.0 * disturbance;
        
        temp = IndustrialDataContract.clamp("temperature", temp);
        pressure = IndustrialDataContract.clamp("pressure", pressure);
        materialHeight = IndustrialDataContract.clamp("materialHeight", materialHeight);
        gasFlow = IndustrialDataContract.clamp("gasFlow", gasFlow);
        oxygenLevel = IndustrialDataContract.clamp("oxygenLevel", oxygenLevel);
        productionRate = IndustrialDataContract.clamp("productionRate", productionRate);
        energyConsumption = IndustrialDataContract.clamp("energyConsumption", energyConsumption);
        siContent = IndustrialDataContract.clamp("siliconContent", siContent);
        hotMetalTemperature = IndustrialDataContract.clamp("hotMetalTemperature", hotMetalTemperature);

        // 6. 填充数据对象
        data.setTemperature(round(temp, 1));
        data.setPressure(round(pressure, 2));
        data.setMaterialHeight(round(materialHeight, 2));
        data.setGasFlow(round(gasFlow, 0));
        data.setOxygenLevel(round(oxygenLevel, 2));
        data.setProductionRate(round(productionRate, 1));
        data.setEnergyConsumption(round(energyConsumption, 1));
        data.setSiliconContent(round(siContent, 3));

        data.setHotMetalTemperature(round(hotMetalTemperature, 1));
        data.setConstantSignal(5.0);

        double currentWindVolume = state.windVolume;
        double currentCoalInjection = state.coalInjection + (random.nextDouble() - 0.5) * 5.0 * disturbance;
        currentWindVolume = IndustrialDataContract.clamp("windVolume", currentWindVolume);
        currentCoalInjection = IndustrialDataContract.clamp("coalInjection", currentCoalInjection);
        data.setWindVolume(round(currentWindVolume, 1));
        data.setCoalInjection(round(currentCoalInjection, 1));
        data.setStatus(resolveIndustrialStatus(data, status));
        
        return data;
    }

    private String resolveIndustrialStatus(ProductionData data, String phaseStatus) {
        boolean severe = IndustrialDataContract.isSevere("temperature", data.getTemperature())
                || IndustrialDataContract.isSevere("pressure", data.getPressure())
                || IndustrialDataContract.isSevere("windVolume", data.getWindVolume())
                || IndustrialDataContract.isSevere("coalInjection", data.getCoalInjection())
                || IndustrialDataContract.isSevere("materialHeight", data.getMaterialHeight())
                || IndustrialDataContract.isSevere("gasFlow", data.getGasFlow())
                || IndustrialDataContract.isSevere("oxygenLevel", data.getOxygenLevel())
                || IndustrialDataContract.isSevere("productionRate", data.getProductionRate())
                || IndustrialDataContract.isSevere("energyConsumption", data.getEnergyConsumption())
                || IndustrialDataContract.isSevere("hotMetalTemperature", data.getHotMetalTemperature())
                || IndustrialDataContract.isSevere("siliconContent", data.getSiliconContent());
        if (severe) {
            return "异常报警";
        }
        boolean warning = IndustrialDataContract.isWarning("temperature", data.getTemperature())
                || IndustrialDataContract.isWarning("pressure", data.getPressure())
                || IndustrialDataContract.isWarning("windVolume", data.getWindVolume())
                || IndustrialDataContract.isWarning("coalInjection", data.getCoalInjection())
                || IndustrialDataContract.isWarning("materialHeight", data.getMaterialHeight())
                || IndustrialDataContract.isWarning("gasFlow", data.getGasFlow())
                || IndustrialDataContract.isWarning("oxygenLevel", data.getOxygenLevel())
                || IndustrialDataContract.isWarning("productionRate", data.getProductionRate())
                || IndustrialDataContract.isWarning("energyConsumption", data.getEnergyConsumption())
                || IndustrialDataContract.isWarning("hotMetalTemperature", data.getHotMetalTemperature())
                || IndustrialDataContract.isWarning("siliconContent", data.getSiliconContent());
        if (warning) {
            return "预警";
        }
        return phaseStatus;
    }
    
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    
    public int getRunningTaskCount() { return runningTasks.size(); }
    
    public boolean isTaskRunning() {
        return !runningTasks.isEmpty();
    }

    public List<Map<String, Object>> getScenarioTemplates() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ScenarioTemplate template : SCENARIO_TEMPLATES.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", template.key());
            item.put("label", template.label());
            item.put("description", template.description());
            item.put("phases", template.phases());
            list.add(item);
        }
        return list;
    }

    private ScenarioRuntime getScenarioRuntime(String taskId, String furnaceId) {
        if (taskId == null || taskId.isBlank()) {
            String previewKey = furnaceId + "|preview";
            return previewScenarioStates.computeIfAbsent(previewKey, k ->
                    new ScenarioRuntime(resolveTemplate("steady_day"), Math.abs((long) furnaceId.hashCode()) + 20260304L));
        }
        TaskParams params = taskParams.get(taskId);
        String templateKey = params == null ? "steady_day" : params.getScriptTemplateKey();
        Long seed = params == null ? null : params.getScriptSeed();
        long baseSeed = seed == null ? System.currentTimeMillis() : seed;
        String runtimeKey = taskId + "|" + furnaceId;
        return taskScenarioStates.computeIfAbsent(runtimeKey, k ->
                new ScenarioRuntime(resolveTemplate(templateKey), baseSeed + Math.abs((long) furnaceId.hashCode())));
    }

    private ScenarioTemplate resolveTemplate(String key) {
        return SCENARIO_TEMPLATES.getOrDefault(normalizeTemplateKey(key), SCENARIO_TEMPLATES.get("steady_day"));
    }

    private String normalizeTemplateKey(String key) {
        if (key == null || key.isBlank()) {
            return "steady_day";
        }
        String trimmed = key.trim();
        if (SCENARIO_TEMPLATES.containsKey(trimmed)) {
            return trimmed;
        }
        return "steady_day";
    }

    private String normalizeFurnaceId(String furnaceId) {
        if (furnaceId == null || furnaceId.isBlank()) {
            return null;
        }
        String normalized = furnaceId.trim();
        if ("RANDOM_THREE".equalsIgnoreCase(normalized) || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}
