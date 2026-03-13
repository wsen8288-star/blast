package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.repository.UserRepository;
import com.blastfurnace.backend.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class UserService {
    private static final String SUPER_ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_ROLE = "ADMIN";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final BCryptPasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();
    
    public User registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user不能为空");
        }
        applyRoleBinding(user, user.getRole());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true);
        return userRepository.save(user);
    }
    
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Page<User> findPage(String username, String role, Boolean enabled, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                String like = "%" + username.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("email")), like)
                ));
            }
            if (role != null && !role.isBlank()) {
                String normalized = role.trim().toLowerCase();
                jakarta.persistence.criteria.Join<User, Role> roleJoin = root.join("roles", jakarta.persistence.criteria.JoinType.LEFT);
                query.distinct(true);
                predicates.add(cb.equal(cb.lower(roleJoin.get("roleCode")), normalized));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return userRepository.findAll(spec, pageable);
    }

    public User createUser(String username, String rawPassword, String email, String role, Boolean enabled) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        applyRoleBinding(user, role);
        user.setEnabled(enabled == null || enabled);
        user.setPassword(bcryptPasswordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public User updateUser(Long id, String email, String role, Boolean enabled) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String trimmedEmail = email.trim();
        if (!Objects.equals(trimmedEmail, user.getEmail()) && userRepository.existsByEmailAndIdNot(trimmedEmail, id)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        if (isProtectedSuperAdmin(user)) {
            if (enabled != null && !enabled) {
                throw new IllegalArgumentException("超级管理员账号不可禁用");
            }
            if (!SUPER_ADMIN_ROLE.equalsIgnoreCase(role.trim())) {
                throw new IllegalArgumentException("超级管理员账号角色不可修改");
            }
        }
        user.setEmail(trimmedEmail);
        applyRoleBinding(user, role);
        if (enabled != null) {
            user.setEnabled(enabled);
        }
        return userRepository.save(user);
    }

    public User updateUserStatus(Long id, boolean enabled) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!enabled && isSelfOperation(user)) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
        if (!enabled && isProtectedSuperAdmin(user)) {
            throw new IllegalArgumentException("超级管理员账号不可禁用");
        }
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    public User resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (isProtectedSuperAdmin(user)) {
            throw new IllegalArgumentException("超级管理员账号不支持后台重置密码");
        }
        user.setPassword(bcryptPasswordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    @Transactional
    public int batchUpdateUserStatus(List<Long> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        List<User> users = userRepository.findAllById(ids);
        if (users.isEmpty()) {
            return 0;
        }
        if (!enabled) {
            boolean hasProtectedAdmin = users.stream().anyMatch(this::isProtectedSuperAdmin);
            if (hasProtectedAdmin) {
                throw new IllegalArgumentException("超级管理员账号不可禁用");
            }
            boolean hasSelf = users.stream().anyMatch(this::isSelfOperation);
            if (hasSelf) {
                throw new IllegalArgumentException("不能批量禁用当前登录账号");
            }
        }
        users.forEach(u -> u.setEnabled(enabled));
        userRepository.saveAll(users);
        return users.size();
    }

    @Transactional
    public int batchResetPassword(List<Long> ids, String newPassword) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        String pwd = (newPassword == null || newPassword.isBlank()) ? "123456" : newPassword;
        List<User> users = userRepository.findAllById(ids);
        if (users.isEmpty()) {
            return 0;
        }
        boolean hasProtectedAdmin = users.stream().anyMatch(this::isProtectedSuperAdmin);
        if (hasProtectedAdmin) {
            throw new IllegalArgumentException("超级管理员账号不支持批量重置密码");
        }
        users.forEach(u -> u.setPassword(bcryptPasswordEncoder.encode(pwd)));
        userRepository.saveAll(users);
        return users.size();
    }
    
    public User save(User user) {
        return userRepository.save(user);
    }
    
    public void delete(Long id) {
        deleteUser(id);
    }

    public void deleteUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id不能为空");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (isSelfOperation(user)) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }
        if (isProtectedSuperAdmin(user)) {
            throw new IllegalArgumentException("超级管理员账号不可删除");
        }
        userRepository.deleteById(id);
    }
    
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    private boolean isProtectedSuperAdmin(User user) {
        if (user == null) {
            return false;
        }
        String username = user.getUsername();
        if (username != null && SUPER_ADMIN_USERNAME.equalsIgnoreCase(username.trim())) {
            return true;
        }
        Long userId = user.getId();
        if (userId == null) {
            return false;
        }
        return userRepository.existsByIdAndRoles_RoleCodeIgnoreCase(userId, SUPER_ADMIN_ROLE);
    }

    private void applyRoleBinding(User user, String roleCode) {
        if (user == null) {
            throw new IllegalArgumentException("user不能为空");
        }
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String normalized = roleCode.trim().toUpperCase();
        Role role = roleRepository.findByRoleCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + normalized));
        user.setRole(normalized);
        user.setRoles(new java.util.HashSet<>(Set.of(role)));
    }

    private boolean isSelfOperation(User user) {
        if (user == null) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String currentUsername = authentication.getName();
        if (currentUsername == null || currentUsername.isBlank()) {
            return false;
        }
        String targetUsername = user.getUsername();
        if (targetUsername == null || targetUsername.isBlank()) {
            return false;
        }
        return currentUsername.trim().equalsIgnoreCase(targetUsername.trim());
    }
}
