package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.BackupHistory;
import com.blastfurnace.backend.model.StorageConfig;
import com.blastfurnace.backend.repository.BackupHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BackupService {
    @Autowired
    private BackupHistoryRepository backupHistoryRepository;
    
    @Autowired
    private StorageConfigService storageConfigService;

    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    
    // 执行手动备份
    public BackupHistory executeManualBackup() {
        StorageConfig config = storageConfigService.getDefaultConfig();
        return executeBackup(config, "manual");
    }

    public Map<String, Object> startManualBackupTask(String operatorName, String sourceIp) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = TaskInfo.running(taskId, "backup", operatorName, sourceIp);
        tasks.put(taskId, task);
        taskExecutor.submit(() -> {
            try {
                BackupHistory history = executeManualBackup();
                task.markSuccess(history == null ? null : history.getId());
            } catch (Exception e) {
                task.markFailed(e == null ? "备份失败" : String.valueOf(e.getMessage()));
            }
        });
        return task.toMap();
    }

    public Map<String, Object> startRestoreTask(String backupPoint, String operatorName, String sourceIp) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = TaskInfo.running(taskId, "restore", operatorName, sourceIp);
        tasks.put(taskId, task);
        taskExecutor.submit(() -> {
            try {
                BackupHistory history = restoreData(backupPoint, operatorName, sourceIp);
                task.markSuccess(history == null ? null : history.getId());
            } catch (Exception e) {
                task.markFailed(e == null ? "恢复失败" : String.valueOf(e.getMessage()));
            }
        });
        return task.toMap();
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Map.of("exists", false);
        }
        TaskInfo task = tasks.get(taskId.trim());
        if (task == null) {
            return Map.of("exists", false);
        }
        return task.toMap();
    }
    
    // 执行备份
    private BackupHistory executeBackup(StorageConfig config, String backupType) {
        BackupHistory history = new BackupHistory();
        history.setBackupTime(new Date());
        history.setBackupType(backupType);
        
        try {
            // 确保备份目录存在
            String backupRootPath = config.getBackupStoragePath();
            File backupRootDir = new File(backupRootPath);
            if (!backupRootDir.exists()) {
                backupRootDir.mkdirs();
            }
            String backupDirName = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(history.getBackupTime());
            File backupDir = new File(backupRootDir, backupDirName);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // 执行备份操作
            String mainPath = config.getMainStoragePath();
            String collectionPath = resolveCollectionPath(config);
            File mainDir = new File(mainPath);
            
            // 确保主存储目录存在
            if (!mainDir.exists()) {
                mainDir.mkdirs();
            }
            
            // 同时备份数据采集路径
            File dataDir = new File(collectionPath);
            
            // 确保数据采集目录存在
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            
            // 计算备份大小
            long size = 0;
            if (mainDir.exists() && mainDir.isDirectory()) {
                size += calculateDirectorySize(mainDir);
            }
            if (dataDir.exists() && dataDir.isDirectory()) {
                size += calculateDirectorySize(dataDir);
            }
            history.setBackupSize(formatFileSize(size));
            
            // 创建备份子目录
            File backupMainDir = new File(backupDir, "main");
            if (!backupMainDir.exists()) {
                backupMainDir.mkdirs();
            }
            
            File backupDataDir = new File(backupDir, "data");
            if (!backupDataDir.exists()) {
                backupDataDir.mkdirs();
            }
            
            // 复制主存储目录文件
            if (mainDir.exists() && mainDir.isDirectory()) {
                copyFiles(mainDir, backupMainDir);
            }
            
            // 复制数据采集目录文件，排除备份目录
            if (dataDir.exists() && dataDir.isDirectory()) {
                copyFilesExcluding(dataDir, backupDataDir, backupRootDir);
            }
            
            history.setBackupPath(backupDir.getAbsolutePath());
            history.setStatus("success");
        } catch (Exception e) {
            e.printStackTrace();
            history.setBackupSize("0 KB");
            history.setBackupPath(config.getBackupStoragePath());
            history.setStatus("failed");
        }
        
        return backupHistoryRepository.save(history);
    }
    
    // 复制文件（递归）
    private void copyFiles(File sourceDir, File targetDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                if (file.isDirectory()) {
                    copyFiles(file, targetFile);
                } else {
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    
    // 复制文件（递归），排除指定目录
    private void copyFilesExcluding(File sourceDir, File targetDir, File excludeDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                // 排除备份目录
                if (file.equals(excludeDir)) {
                    continue;
                }
                
                File targetFile = new File(targetDir, file.getName());
                if (file.isDirectory()) {
                    copyFilesExcluding(file, targetFile, excludeDir);
                } else {
                    Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
    
    // 删除目录
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // 从最深层开始删除
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
    }
    
    // 复制目录
    private void copyDirectory(Path source, Path target) throws IOException {
        // 检查目标目录是否是源目录的子目录，避免递归复制
        if (target.startsWith(source)) {
            System.out.println("目标目录是源目录的子目录，跳过复制操作");
            return;
        }
        
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        
        Files.walk(source).forEach(sourcePath -> {
            try {
                // 跳过目标目录，避免递归复制
                if (sourcePath.startsWith(target)) {
                    return;
                }
                
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
    
    // 恢复数据
    public BackupHistory restoreData(String backupPoint, String operatorName, String sourceIp) {
        BackupHistory history = new BackupHistory();
        history.setBackupTime(new Date());
        history.setBackupType("restore");
        history.setOperatorName(operatorName);
        history.setSourceIp(sourceIp);
        
        try {
            StorageConfig config = storageConfigService.getDefaultConfig();
            BackupHistory sourceHistory = resolveBackupHistory(backupPoint);
            if (sourceHistory == null || sourceHistory.getBackupPath() == null || sourceHistory.getBackupPath().isBlank()) {
                throw new IllegalArgumentException("未找到可恢复的备份版本");
            }
            history.setRestoreSourceBackupId(sourceHistory.getId());
            Path backupRoot = Paths.get(config.getBackupStoragePath()).toAbsolutePath().normalize();
            Path backupDir = Paths.get(sourceHistory.getBackupPath()).toAbsolutePath().normalize();
            if (!backupDir.startsWith(backupRoot) || backupDir.equals(backupRoot)) {
                throw new IllegalArgumentException("备份路径不合法");
            }
            String backupPath = backupDir.toString();
            String mainPath = config.getMainStoragePath();
            String collectionPath = resolveCollectionPath(config);
            
            // 确保主存储目录存在
            File mainDir = new File(mainPath);
            if (!mainDir.exists()) {
                mainDir.mkdirs();
            }
            File collectionDir = new File(collectionPath);
            if (!collectionDir.exists()) {
                collectionDir.mkdirs();
            }
            
            // 执行恢复操作
            Path backupMainPath = backupDir.resolve("main");
            Path backupDataPath = backupDir.resolve("data");
            
            if (Files.exists(backupMainPath) || Files.exists(backupDataPath)) {
                // 恢复主存储数据
                if (Files.exists(backupMainPath)) {
                    copyDirectory(backupMainPath, Paths.get(mainPath));
                }
                
                // 恢复数据采集数据
                if (Files.exists(backupDataPath)) {
                    copyDirectory(backupDataPath, Paths.get(collectionPath));
                }
                
                history.setBackupSize("0 KB");
                history.setBackupPath(backupPath);
                history.setStatus("success");
            } else {
                history.setBackupSize("0 KB");
                history.setBackupPath(backupPath);
                history.setStatus("failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            history.setBackupSize("0 KB");
            history.setBackupPath("");
            history.setStatus("failed");
        }
        
        return backupHistoryRepository.save(history);
    }
    
    // 获取备份历史
    public List<BackupHistory> getBackupHistory() {
        return backupHistoryRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getBackupTime() == null && b.getBackupTime() == null) return 0;
                    if (a.getBackupTime() == null) return 1;
                    if (b.getBackupTime() == null) return -1;
                    return b.getBackupTime().compareTo(a.getBackupTime());
                })
                .toList();
    }
    
    // 计算目录大小
    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }
    
    // 格式化文件大小
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return (size / (1024 * 1024)) + " MB";
        } else {
            return (size / (1024 * 1024 * 1024)) + " GB";
        }
    }
    
    // 删除备份历史
    public boolean deleteBackupHistory(Long backupId) {
        try {
            // 查找备份记录
            BackupHistory backupHistory = backupHistoryRepository.findById(backupId).orElse(null);
            if (backupHistory == null) {
                return false;
            }
            
            // 删除对应的备份文件
            String backupPath = backupHistory.getBackupPath();
            if (backupPath != null && !backupPath.isEmpty()) {
                File backupDir = new File(backupPath);
                if (backupDir.exists() && backupDir.isDirectory()) {
                    Path backupRoot = Paths.get(storageConfigService.getDefaultConfig().getBackupStoragePath()).toAbsolutePath().normalize();
                    Path target = backupDir.toPath().toAbsolutePath().normalize();
                    if (target.startsWith(backupRoot) && !target.equals(backupRoot)) {
                        deleteDirectory(target);
                    }
                }
            }
            
            // 删除数据库记录
            backupHistoryRepository.delete(backupHistory);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolveCollectionPath(StorageConfig config) {
        String configured = System.getProperty("blastfurnace.collection-path");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("BLAST_FURNACE_COLLECTION_PATH");
        }
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        Path mainPath = Paths.get(config.getMainStoragePath()).toAbsolutePath().normalize();
        Path parent = mainPath.getParent();
        if (parent == null) {
            return mainPath.resolve("collection").toString();
        }
        return parent.resolve("collection").toString();
    }

    private BackupHistory resolveBackupHistory(String backupPoint) {
        if (backupPoint == null || backupPoint.isBlank()) {
            return null;
        }
        String value = backupPoint.trim();
        try {
            Long backupId = Long.parseLong(value);
            Optional<BackupHistory> byId = backupHistoryRepository.findById(backupId);
            if (byId.isPresent() && "success".equals(byId.get().getStatus())) {
                return byId.get();
            }
        } catch (NumberFormatException ignored) {
        }
        Date parsed = parseDateTime(value);
        if (parsed == null) {
            return null;
        }
        return backupHistoryRepository.findFirstByBackupTimeAndStatusOrderByIdDesc(parsed, "success").orElse(null);
    }

    private Date parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS"
        };
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern).parse(value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static final class TaskInfo {
        private final String id;
        private final String type;
        private final String operatorName;
        private final String sourceIp;
        private final long startedAt;
        private volatile long finishedAt;
        private volatile String status;
        private volatile String message;
        private volatile Long backupHistoryId;

        private TaskInfo(String id, String type, String operatorName, String sourceIp, long startedAt) {
            this.id = id;
            this.type = type;
            this.operatorName = operatorName;
            this.sourceIp = sourceIp;
            this.startedAt = startedAt;
            this.status = "RUNNING";
        }

        static TaskInfo running(String id, String type, String operatorName, String sourceIp) {
            return new TaskInfo(id, type, operatorName, sourceIp, System.currentTimeMillis());
        }

        void markSuccess(Long backupHistoryId) {
            this.backupHistoryId = backupHistoryId;
            this.status = "SUCCESS";
            this.finishedAt = System.currentTimeMillis();
        }

        void markFailed(String message) {
            this.message = message;
            this.status = "FAILED";
            this.finishedAt = System.currentTimeMillis();
        }

        Map<String, Object> toMap() {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("exists", true);
            out.put("taskId", id);
            out.put("type", type);
            out.put("status", status);
            out.put("message", message);
            out.put("backupHistoryId", backupHistoryId);
            out.put("operatorName", operatorName);
            out.put("sourceIp", sourceIp);
            out.put("startedAt", startedAt);
            out.put("finishedAt", finishedAt);
            return out;
        }
    }
}
