package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.service.DataCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DataCleanupController {

    @Autowired
    private DataCleanupService dataCleanupService;

    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanupData() {
        try {
            dataCleanupService.cleanupAndInitializeData();
            return ResponseEntity.ok("数据清理和初始化成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("数据清理失败: " + e.getMessage());
        }
    }
}
