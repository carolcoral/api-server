/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.RoleRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * 自定义用户详情服务
 * 在 Spring Security 加载用户时，同步加载角色权限
 *
 * @author carolcoral
 * @since 2.3.0
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final RoleRepository roleRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                     PermissionService permissionService,
                                     RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.roleRepository = roleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        User user = userOpt.get();
        // 加载用户权限
        loadPermissions(user);
        return user;
    }

    /**
     * 通过用户名加载用户（支持邮箱登录）
     */
    public UserDetails loadUserByUsernameOrEmail(String account) throws UsernameNotFoundException {
        Optional<User> userOpt;
        if (account.contains("@")) {
            userOpt = userRepository.findByEmail(account);
        } else {
            userOpt = userRepository.findByUsername(account);
        }
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("用户不存在: " + account);
        }
        User user = userOpt.get();
        loadPermissions(user);
        return user;
    }

    /**
     * 加载用户权限并设置到 User 对象上，同时根据 roleId 同步 role 枚举
     */
    public void loadPermissions(User user) {
        try {
            Long roleId = user.getRoleId();
            // 如果 role_id 为空但 role 字段明确，按 role 字段补全 role_id 以加载对应权限
            if (roleId == null && user.getRole() != null) {
                if (user.getRole() == User.UserRole.ADMIN) {
                    roleId = 1L;
                } else if (user.getRole() == User.UserRole.USER) {
                    roleId = 2L;
                }
            }

            if (roleId != null) {
                // 根据 roleId 同步 role 枚举，避免 t_user.role 与 t_role 数据不一致导致权限判断失败
                roleRepository.findById(roleId).ifPresent(role -> syncUserRole(user, role));

                java.util.Set<String> permCodes = permissionService.getUserPermissionCodes(
                        Collections.singletonList(roleId));
                user.setPermissions(permCodes);
                log.debug("用户 {} 权限加载完成: roleId={}, role={}, permissions={}",
                        user.getUsername(), roleId, user.getRole(), permCodes);
            } else {
                user.setPermissions(Collections.emptySet());
            }
        } catch (Exception e) {
            log.warn("加载用户权限失败: username={}, error={}", user.getUsername(), e.getMessage());
            user.setPermissions(Collections.emptySet());
        }
    }

    /**
     * 根据 Role 编码同步 User 的 role 枚举
     */
    private void syncUserRole(User user, Role role) {
        String code = role.getCode();
        if (code == null || code.isEmpty()) {
            return;
        }
        String roleName = code.startsWith("ROLE_") ? code.substring(5) : code;
        try {
            User.UserRole newRole = User.UserRole.valueOf(roleName);
            if (user.getRole() != newRole) {
                log.info("同步用户角色枚举: username={}, roleId={}, {} -> {}",
                        user.getUsername(), user.getRoleId(), user.getRole(), newRole);
                user.setRole(newRole);
            }
        } catch (IllegalArgumentException e) {
            log.warn("无法同步用户角色枚举: username={}, roleCode={}", user.getUsername(), code);
        }
    }
}
