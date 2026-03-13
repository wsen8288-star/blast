package com.blastfurnace.backend.config;

import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.repository.RoleRepository;
import com.blastfurnace.backend.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UserRoleMigrationRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserRoleMigrationRunner(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> users = userRepository.findAllWithRoles();
        if (users == null || users.isEmpty()) {
            return;
        }

        List<User> changed = new ArrayList<>();
        for (User user : users) {
            if (user == null) {
                continue;
            }

            String roleField = user.getRole() == null ? "" : user.getRole().trim();
            Set<Role> roles = user.getRoles();
            boolean hasRoles = roles != null && !roles.isEmpty();

            if (!hasRoles) {
                if (!roleField.isBlank()) {
                    String normalized = roleField.toUpperCase();
                    Role role = roleRepository.findByRoleCode(normalized).orElse(null);
                    if (role != null) {
                        user.setRoles(new HashSet<>(Set.of(role)));
                        user.setRole(normalized);
                        changed.add(user);
                    }
                }
                continue;
            }

            String primaryRole = roles.stream()
                    .map(Role::getRoleCode)
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> code.trim().toUpperCase())
                    .findFirst()
                    .orElse(null);
            if (primaryRole != null && !primaryRole.equalsIgnoreCase(roleField)) {
                user.setRole(primaryRole);
                changed.add(user);
            }
        }

        if (!changed.isEmpty()) {
            userRepository.saveAll(changed);
        }
    }
}

