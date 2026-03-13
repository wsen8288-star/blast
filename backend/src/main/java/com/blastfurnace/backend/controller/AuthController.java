package com.blastfurnace.backend.controller;

import com.blastfurnace.backend.dto.LoginRequestDTO;
import com.blastfurnace.backend.dto.RegisterRequestDTO;
import com.blastfurnace.backend.dto.JwtResponseDTO;
import com.blastfurnace.backend.dto.ForgotPasswordRequestDTO;
import com.blastfurnace.backend.dto.ResetPasswordRequestDTO;
import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.response.Result;
import com.blastfurnace.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    
    @PostMapping("/login")
    public Result<JwtResponseDTO> login(@RequestBody LoginRequestDTO loginRequest) {
        try {
            JwtResponseDTO response = authService.login(loginRequest);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterRequestDTO registerRequest) {
        try {
            User user = authService.register(registerRequest);
            return Result.success(user, "注册成功");
        } catch (Exception e) {
            return Result.error("注册失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/roles")
    public Result<List<String>> getRoles() {
        try {
            List<String> roles = authService.getRoles();
            return Result.success(roles);
        } catch (Exception e) {
            return Result.error("获取角色列表失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@RequestBody ForgotPasswordRequestDTO forgotPasswordRequest) {
        try {
            String tempPassword = authService.forgotPassword(forgotPasswordRequest);
            return Result.success(tempPassword, "临时密码已生成，请使用临时密码登录");
        } catch (Exception e) {
            return Result.error("忘记密码失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestBody ResetPasswordRequestDTO resetPasswordRequest) {
        try {
            authService.resetPassword(resetPasswordRequest);
            return Result.success("密码重置成功");
        } catch (Exception e) {
            return Result.error("重置密码失败: " + e.getMessage());
        }
    }

    @GetMapping("/authorities")
    @PreAuthorize("isAuthenticated()")
    public Result<List<String>> getAuthorities(Authentication authentication) {
        try {
            if (authentication == null) {
                return Result.error("未登录");
            }
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(a -> a == null ? null : a.getAuthority())
                    .filter(a -> a != null && !a.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            return Result.success(authorities);
        } catch (Exception e) {
            return Result.error("获取权限失败: " + e.getMessage());
        }
    }
}
