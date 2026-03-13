package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.annotation.LogAction;
import com.blastfurnace.backend.dto.UserDTO;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final UserService userService;

    @GetMapping("/users")
    public Result<Page<UserDTO>> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        try {
            String q = (keyword != null && !keyword.isBlank()) ? keyword : username;
            Page<UserDTO> page = userService.findPage(q, role, enabled, pageable).map(SystemController::toUserDTO);
            return Result.success(page);
        } catch (Exception e) {
            return Result.error("获取用户列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/users")
    @LogAction(module = "系统管理", value = "新增用户")
    public Result<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                return Result.error("请求体不能为空");
            }
            String password = (request.password() == null || request.password().isBlank()) ? "123456" : request.password();
            User user = userService.createUser(request.username(), password, request.email(), request.role(), request.enabled());
            return Result.success(toUserDTO(user), "添加用户成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("添加用户失败: " + e.getMessage());
        }
    }

    @PutMapping("/users/{id}/status")
    @LogAction(module = "系统管理", value = "更新用户状态")
    public Result<UserDTO> updateUserStatus(@PathVariable Long id, @RequestBody UpdateUserStatusRequest request) {
        try {
            if (request == null || request.enabled() == null) {
                return Result.error("enabled不能为空");
            }
            User user = userService.updateUserStatus(id, request.enabled());
            return Result.success(toUserDTO(user), "更新用户状态成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("更新用户状态失败: " + e.getMessage());
        }
    }

    @PutMapping("/users/{id}")
    @LogAction(module = "系统管理", value = "编辑用户")
    public Result<UserDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        try {
            if (request == null) {
                return Result.error("请求体不能为空");
            }
            User user = userService.updateUser(id, request.email(), request.role(), request.enabled());
            return Result.success(toUserDTO(user), "更新用户成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("更新用户失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    @LogAction(module = "系统管理", value = "删除用户")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return Result.successMsg("删除用户成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("删除用户失败: " + e.getMessage());
        }
    }

    @PostMapping("/users/{id}/reset-password")
    @LogAction(module = "系统管理", value = "重置用户密码")
    public Result<String> resetPassword(@PathVariable Long id, @RequestBody(required = false) ResetPasswordAdminRequest request) {
        try {
            String newPassword = (request == null || request.newPassword() == null || request.newPassword().isBlank())
                    ? "123456"
                    : request.newPassword();
            userService.resetPassword(id, newPassword);
            return Result.successMsg("密码重置成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("重置密码失败: " + e.getMessage());
        }
    }

    @PutMapping("/users/status/batch")
    @LogAction(module = "系统管理", value = "批量更新用户状态")
    public Result<String> batchUpdateUserStatus(@RequestBody BatchUpdateStatusRequest request) {
        try {
            if (request == null || request.enabled() == null) {
                return Result.error("enabled不能为空");
            }
            int count = userService.batchUpdateUserStatus(request.ids(), request.enabled());
            return Result.successMsg("批量更新成功，共" + count + "条");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("批量更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/users/reset-password/batch")
    @LogAction(module = "系统管理", value = "批量重置用户密码")
    public Result<String> batchResetPassword(@RequestBody BatchResetPasswordRequest request) {
        try {
            int count = userService.batchResetPassword(request == null ? null : request.ids(), request == null ? null : request.newPassword());
            return Result.successMsg("批量重置成功，共" + count + "条");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("批量重置失败: " + e.getMessage());
        }
    }
    
    // 系统设置
    @GetMapping("/settings")
    public Result<String> getSystemSettings() {
        return Result.success("获取系统设置成功");
    }
    
    @PutMapping("/settings")
    @LogAction(module = "系统管理", value = "修改系统设置")
    public Result<String> updateSystemSettings(@RequestBody Object data) {
        return Result.success("更新系统设置成功");
    }
    
    private static UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        String role = user.getRoles() == null ? null : user.getRoles().stream()
                .map(Role::getRoleCode)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .findFirst()
                .orElse(null);
        dto.setRole(role);
        dto.setEnabled(user.isEnabled());
        return dto;
    }

    public record CreateUserRequest(String username, String password, String email, String role, Boolean enabled) {}

    public record UpdateUserStatusRequest(Boolean enabled) {}

    public record ResetPasswordAdminRequest(String newPassword) {}

    public record UpdateUserRequest(String email, String role, Boolean enabled) {}

    public record BatchUpdateStatusRequest(List<Long> ids, Boolean enabled) {}

    public record BatchResetPasswordRequest(List<Long> ids, String newPassword) {}
}
