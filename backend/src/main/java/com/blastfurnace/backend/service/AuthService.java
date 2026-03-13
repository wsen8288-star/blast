package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.dto.LoginRequestDTO;
import com.blastfurnace.backend.dto.RegisterRequestDTO;
import com.blastfurnace.backend.dto.JwtResponseDTO;
import com.blastfurnace.backend.dto.ForgotPasswordRequestDTO;
import com.blastfurnace.backend.dto.ResetPasswordRequestDTO;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.repository.UserRepository;
import com.blastfurnace.backend.repository.RoleRepository;
import com.blastfurnace.backend.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;
    
    public JwtResponseDTO login(LoginRequestDTO loginRequest) {
        // 直接使用 userService.findByUsername 方法来获取用户
        User user = userService.findByUsername(loginRequest.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 使用 PasswordEncoder 来验证密码
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 验证用户是否启用
        if (!user.isEnabled()) {
            throw new RuntimeException("用户已注册但未启用");
        }
        
        if (loginRequest.getRole() != null && !loginRequest.getRole().isBlank()) {
            String selectedRole = loginRequest.getRole().trim();
            boolean roleBindingMatched = user.getId() != null
                    && userRepository.existsByIdAndRoles_RoleCodeIgnoreCase(user.getId(), selectedRole);
            if (!roleBindingMatched) {
                throw new RuntimeException("角色验证失败");
            }
        }
        
        // 创建一个 Authentication 对象
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            user.getUsername(),
            user.getPassword(),
            new ArrayList<>()
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        String effectiveRole = loginRequest.getRole() == null ? null : loginRequest.getRole().trim().toUpperCase();
        return new JwtResponseDTO(jwt, user.getId(), user.getUsername(), user.getEmail(), effectiveRole);
    }
    
    public User register(RegisterRequestDTO registerRequest) {
        // 检查用户名是否已存在
        if (userService.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("邮箱已存在");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setEmail(registerRequest.getEmail());
        user.setRole(registerRequest.getRole());
        
        // 保存用户
        return userService.registerUser(user);
    }
    
    public List<String> getRoles() {
        return roleRepository.findAll().stream()
            .map(Role::getRoleCode)
            .filter(role -> role != null && !role.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }
    
    public String forgotPassword(ForgotPasswordRequestDTO forgotPasswordRequest) {
        // 根据邮箱查找用户
        User user = userService.findByEmail(forgotPasswordRequest.getEmail());
        if (user == null) {
            throw new RuntimeException("邮箱不存在");
        }
        
        // 生成临时密码（实际项目中应该发送邮件）
        String tempPassword = "Temp" + System.currentTimeMillis();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userService.save(user);
        
        return tempPassword;
    }
    
    public void resetPassword(ResetPasswordRequestDTO resetPasswordRequest) {
        // 验证新密码和确认密码是否一致
        if (!resetPasswordRequest.getNewPassword().equals(resetPasswordRequest.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }
        
        // 根据邮箱查找用户
        User user = userService.findByEmail(resetPasswordRequest.getEmail());
        if (user == null) {
            throw new RuntimeException("邮箱不存在");
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
        userService.save(user);
    }
}
