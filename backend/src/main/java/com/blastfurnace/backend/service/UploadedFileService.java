package com.blastfurnace.backend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 上传文件数据管理服务，用于临时存储上传的文件数据
 */
@Service
public class UploadedFileService {
    
    // 临时存储上传的文件数据，使用fileId作为key
    private final ConcurrentHashMap<String, List<Map<String, String>>> uploadedFileDataMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> uploadedAtMap = new ConcurrentHashMap<>();

    public record UploadedFileNormalized(
            List<Map<String, String>> rows,
            List<String> originalHeaders,
            List<String> normalizedHeaders,
            Map<String, String> headerMapping
    ) {
    }

    private final ConcurrentHashMap<String, UploadedFileNormalized> normalizedDataMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ImportPreviewSession> importPreviewMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> previewCreatedAtMap = new ConcurrentHashMap<>();
    private static final long FILE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);
    
    // 定时清理过期的临时数据，避免内存泄漏
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    public UploadedFileService() {
        // 每小时清理一次过期数据
        scheduler.scheduleAtFixedRate(() -> {
            cleanupExpiredData();
        }, 1, 1, TimeUnit.HOURS);
    }

    public record ImportPreviewSession(
            String previewId,
            String templateKey,
            List<Map<String, String>> rows,
            List<String> originalHeaders,
            Map<String, String> headerMapping
    ) {
    }
    
    /**
     * 保存上传的文件数据
     */
    public void saveUploadedData(String fileId, List<Map<String, String>> data) {
        uploadedFileDataMap.put(fileId, data);
        uploadedAtMap.put(fileId, System.currentTimeMillis());
    }
    
    /**
     * 获取上传的文件数据
     */
    public List<Map<String, String>> getUploadedData(String fileId) {
        cleanupIfExpired(fileId);
        return uploadedFileDataMap.get(fileId);
    }

    public void saveNormalizedData(String fileId, UploadedFileNormalized data) {
        if (fileId == null || fileId.isBlank() || data == null) {
            return;
        }
        normalizedDataMap.put(fileId, data);
        uploadedAtMap.putIfAbsent(fileId, System.currentTimeMillis());
    }

    public UploadedFileNormalized getNormalizedData(String fileId) {
        cleanupIfExpired(fileId);
        return normalizedDataMap.get(fileId);
    }

    public void saveImportPreview(ImportPreviewSession session) {
        if (session == null || session.previewId() == null || session.previewId().isBlank()) {
            return;
        }
        importPreviewMap.put(session.previewId(), session);
        previewCreatedAtMap.put(session.previewId(), System.currentTimeMillis());
    }

    public ImportPreviewSession getImportPreview(String previewId) {
        cleanupPreviewIfExpired(previewId);
        return importPreviewMap.get(previewId);
    }

    public void removeImportPreview(String previewId) {
        importPreviewMap.remove(previewId);
        previewCreatedAtMap.remove(previewId);
    }
    
    /**
     * 删除上传的文件数据
     */
    public void removeUploadedData(String fileId) {
        uploadedFileDataMap.remove(fileId);
        normalizedDataMap.remove(fileId);
        uploadedAtMap.remove(fileId);
    }
    
    /**
     * 清理所有上传的文件数据
     */
    public void clearAllUploadedData() {
        uploadedFileDataMap.clear();
        normalizedDataMap.clear();
        uploadedAtMap.clear();
        importPreviewMap.clear();
        previewCreatedAtMap.clear();
    }

    private void cleanupExpiredData() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : uploadedAtMap.entrySet()) {
            if (now - entry.getValue() >= FILE_TTL_MILLIS) {
                removeUploadedData(entry.getKey());
            }
        }
        for (Map.Entry<String, Long> entry : previewCreatedAtMap.entrySet()) {
            if (now - entry.getValue() >= FILE_TTL_MILLIS) {
                removeImportPreview(entry.getKey());
            }
        }
    }

    private void cleanupIfExpired(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        Long uploadedAt = uploadedAtMap.get(fileId);
        if (uploadedAt == null) {
            return;
        }
        if (System.currentTimeMillis() - uploadedAt >= FILE_TTL_MILLIS) {
            removeUploadedData(fileId);
        }
    }

    private void cleanupPreviewIfExpired(String previewId) {
        if (previewId == null || previewId.isBlank()) {
            return;
        }
        Long createdAt = previewCreatedAtMap.get(previewId);
        if (createdAt == null) {
            return;
        }
        if (System.currentTimeMillis() - createdAt >= FILE_TTL_MILLIS) {
            removeImportPreview(previewId);
        }
    }
}
