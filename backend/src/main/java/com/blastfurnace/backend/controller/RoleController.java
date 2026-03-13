package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.model.Permission;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:read')")
    public Result<List<Role>> list() {
        try {
            return Result.success(roleService.listAll());
        } catch (Exception e) {
            return Result.error("获取角色列表失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:write')")
    public Result<Role> create(@RequestBody Role role) {
        try {
            return Result.success(roleService.create(role), "创建角色成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建角色失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:write')")
    public Result<Role> update(@PathVariable Long id, @RequestBody Role role) {
        try {
            return Result.success(roleService.update(id, role), "更新角色成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("更新角色失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:write')")
    public Result<String> delete(@PathVariable Long id) {
        try {
            roleService.delete(id);
            return Result.successMsg("删除角色成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("删除角色失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:write')")
    public Result<String> batchDelete(@RequestBody BatchDeleteRequest request) {
        try {
            int count = roleService.batchDelete(request == null ? null : request.ids());
            return Result.successMsg("批量删除成功，共" + count + "条");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("批量删除角色失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:read')")
    public Result<Set<Permission>> getPermissions(@PathVariable Long id) {
        try {
            return Result.success(roleService.getRolePermissions(id));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("获取角色权限失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:role:write')")
    public Result<Role> setPermissions(@PathVariable Long id, @RequestBody RolePermissionsRequest request) {
        try {
            List<Long> ids = request == null ? null : request.permissionIds();
            return Result.success(roleService.setRolePermissions(id, ids), "分配权限成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("分配权限失败: " + e.getMessage());
        }
    }

    public record RolePermissionsRequest(List<Long> permissionIds) {}

    public record BatchDeleteRequest(List<Long> ids) {}
}
