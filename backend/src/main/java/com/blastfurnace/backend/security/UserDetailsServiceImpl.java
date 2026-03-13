package com.blastfurnace.backend.security;

import com.blastfurnace.backend.model.User;
import com.blastfurnace.backend.model.Role;
import com.blastfurnace.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserService userService;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        Set<Role> roles = user.getRoles();
        if (roles == null) {
            roles = Set.of();
        }

        Set<GrantedAuthority> authoritiesSet = new LinkedHashSet<>();
        for (Role role : roles) {
            if (role == null || role.getRoleCode() == null || role.getRoleCode().isBlank()) continue;
            authoritiesSet.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
            if (role.getPermissions() == null) continue;
            role.getPermissions().forEach(permission -> {
                if (permission == null) return;
                String code = permission.getPermissionCode();
                if (code == null || code.isBlank()) return;
                authoritiesSet.add(new SimpleGrantedAuthority(code));
            });
        }
        List<GrantedAuthority> authorities = new ArrayList<>(authoritiesSet);
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            user.isEnabled(),
            true,
            true,
            true,
            authorities
        );
    }
}
