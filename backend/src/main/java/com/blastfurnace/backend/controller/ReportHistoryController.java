package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.ReportHistory;
import com.blastfurnace.backend.repository.ReportHistoryRepository;
import com.blastfurnace.backend.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report/history")
public class ReportHistoryController {

    @Autowired
    private ReportHistoryRepository reportHistoryRepository;

    // 1. 获取历史列表
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('report:read')")
    public Result<List<ReportHistory>> getHistoryList() {
        return Result.success(reportHistoryRepository.findAllByOrderByCreateTimeDesc());
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('OPERATOR') or hasRole('USER') or hasAuthority('report:read')")
    public Result<Map<String, Object>> downloadReport(@PathVariable Long id) {
        ReportHistory history = reportHistoryRepository.findById(id).orElse(null);
        if (history == null) {
            return Result.error("报表记录不存在");
        }
        File file = new File(history.getFilePath());
        if (!file.exists() || !file.isFile()) {
            return Result.error("报表文件不存在，可能已被删除");
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            Map<String, Object> data = new HashMap<>();
            data.put("fileName", history.getFileName());
            data.put("contentBase64", base64);
            data.put("fileSize", history.getFileSize());
            return Result.success(data, "下载数据已准备");
        } catch (Exception e) {
            return Result.error("读取报表文件失败: " + e.getMessage());
        }
    }


    // 2. 删除报表
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('report:write')")
    public Result<String> deleteReport(@PathVariable Long id) {
        ReportHistory history = reportHistoryRepository.findById(id).orElse(null);
        if (history != null) {
            // 1. 删除物理文件 (可选，看你是否想保留文件只删记录)
            File file = new File(history.getFilePath());
            if (file.exists()) {
                file.delete();
            }
            // 2. 删除数据库记录
            reportHistoryRepository.deleteById(id);
        }
        return Result.success("删除成功");
    }
    @DeleteMapping("/clean")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasAuthority('report:write')")
    public Result<String> cleanInvalidRecords() {
        try {
            List<ReportHistory> allRecords = reportHistoryRepository.findAll();
            int deletedCount = 0;
            
            for (ReportHistory record : allRecords) {
                File file = new File(record.getFilePath());
                // 如果文件不存在，或者是一个目录（异常情况），则视为无效记录
                if (!file.exists() || !file.isFile()) {
                    reportHistoryRepository.delete(record);
                    deletedCount++;
                }
            }
            
            if (deletedCount > 0) {
                return Result.success("清理完成，共移除 " + deletedCount + " 条无效记录");
            } else {
                return Result.success("数据一致，无需清理");
            }
        } catch (Exception e) {
            return Result.error("清理失败: " + e.getMessage());
        }
    }
}
