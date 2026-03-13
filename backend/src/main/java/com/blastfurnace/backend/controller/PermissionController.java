package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.Permission;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:read')")
    public Result<List<Permission>> list() {
        try {
            return Result.success(permissionRepository.findAll());
        } catch (Exception e) {
            return Result.error("获取权限列表失败: " + e.getMessage());
        }
    }
}
