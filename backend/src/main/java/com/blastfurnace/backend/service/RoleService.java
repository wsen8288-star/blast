package com.blastfurnace.backend.service;

import com.blastfurnace.backend.model.Permission;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.repository.PermissionRepository;
import com.blastfurnace.backend.repository.RoleRepository;
import com.blastfurnace.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {
    private static final String SUPER_ADMIN_ROLE = "ADMIN";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public List<Role> listAll() {
        return roleRepository.findAll();
    }

    public Role create(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            throw new IllegalArgumentException("roleCode不能为空");
        }
        if (roleRepository.existsByRoleCode(role.getRoleCode().trim())) {
            throw new IllegalArgumentException("roleCode已存在");
        }
        role.setId(null);
        role.setRoleCode(role.getRoleCode().trim());
        return roleRepository.save(role);
    }

    public Role update(Long id, Role role) {
        Role existing = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        if (role == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        if (role.getRoleName() != null) {
            existing.setRoleName(role.getRoleName());
        }
        if (role.getDescription() != null) {
            existing.setDescription(role.getDescription());
        }
        if (role.getRoleCode() != null && !role.getRoleCode().isBlank()) {
            String newCode = role.getRoleCode().trim();
            if (SUPER_ADMIN_ROLE.equalsIgnoreCase(existing.getRoleCode())
                    && !SUPER_ADMIN_ROLE.equalsIgnoreCase(newCode)) {
                throw new IllegalArgumentException("系统内置超级管理员角色编码不可修改");
            }
            if (!newCode.equals(existing.getRoleCode()) && roleRepository.existsByRoleCode(newCode)) {
                throw new IllegalArgumentException("roleCode已存在");
            }
            existing.setRoleCode(newCode);
        }
        return roleRepository.save(existing);
    }

    public void delete(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        validateRoleDeletable(role);
        roleRepository.deleteById(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        List<Long> uniqueIds = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("ids不能为空");
        }
        List<Role> roles = roleRepository.findAllById(uniqueIds);
        if (roles.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("存在已删除或不存在的角色");
        }
        for (Role role : roles) {
            validateRoleDeletable(role);
        }
        roleRepository.deleteAllById(uniqueIds);
        return uniqueIds.size();
    }

    @Transactional(readOnly = true)
    public Set<Permission> getRolePermissions(Long roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        Set<Permission> permissions = role.getPermissions();
        return permissions == null ? Set.of() : permissions;
    }

    @Transactional
    public Role setRolePermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        Set<Permission> permissions = new HashSet<>();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissions.addAll(permissionRepository.findAllById(permissionIds));
        }
        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    private void validateRoleDeletable(Role role) {
        String roleCode = role.getRoleCode() == null ? "" : role.getRoleCode().trim();
        if (SUPER_ADMIN_ROLE.equalsIgnoreCase(roleCode)) {
            throw new IllegalArgumentException("系统内置超级管理员角色不可删除");
        }
        long relationCountByJoin = userRepository.countByRoles_Id(role.getId());
        if (relationCountByJoin > 0) {
            throw new IllegalArgumentException("角色仍被" + relationCountByJoin + "个用户使用，禁止删除");
        }
    }
}
