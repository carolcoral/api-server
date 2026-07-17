/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RoleService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public ApiResponse<List<Role>> getAllRoles() {
        try {
            return ApiResponse.success(roleRepository.findAll());
        } catch (Exception e) {
            log.error("获取角色列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取角色列表失败");
        }
    }

    public ApiResponse<Role> getRoleById(Long id) {
        try {
            Optional<Role> role = roleRepository.findById(id);
            return role.map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.error("角色不存在"));
        } catch (Exception e) {
            log.error("获取角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取角色失败");
        }
    }

    @Transactional
    public ApiResponse<Role> createRole(Role role) {
        try {
            if (roleRepository.existsByCode(role.getCode())) {
                return ApiResponse.error("角色编码已存在");
            }
            if (roleRepository.existsByName(role.getName())) {
                return ApiResponse.error("角色名称已存在");
            }
            // 如果设置为默认角色，先取消其他默认角色
            if (Boolean.TRUE.equals(role.getIsDefault())) {
                clearDefaultRole();
            }
            Role saved = roleRepository.save(role);
            log.info("创建角色成功: {}", saved.getName());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("创建角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建角色失败");
        }
    }

    @Transactional
    public ApiResponse<Role> updateRole(Long id, Role role) {
        try {
            Optional<Role> existing = roleRepository.findById(id);
            if (existing.isEmpty()) {
                return ApiResponse.error("角色不存在");
            }
            Role r = existing.get();
            // 检查编码唯一性
            if (!r.getCode().equals(role.getCode()) && roleRepository.existsByCode(role.getCode())) {
                return ApiResponse.error("角色编码已存在");
            }
            if (!r.getName().equals(role.getName()) && roleRepository.existsByName(role.getName())) {
                return ApiResponse.error("角色名称已存在");
            }
            r.setName(role.getName());
            r.setCode(role.getCode());
            r.setDescription(role.getDescription());
            // 如果设置为默认角色，先取消其他默认角色
            if (Boolean.TRUE.equals(role.getIsDefault()) && !Boolean.TRUE.equals(r.getIsDefault())) {
                clearDefaultRole();
            }
            r.setIsDefault(role.getIsDefault());
            Role saved = roleRepository.save(r);
            log.info("更新角色成功: {}", saved.getName());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("更新角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新角色失败");
        }
    }

    @Transactional
    public ApiResponse<Void> deleteRole(Long id) {
        try {
            Optional<Role> role = roleRepository.findById(id);
            if (role.isEmpty()) {
                return ApiResponse.error("角色不存在");
            }
            if ("ROLE_ADMIN".equals(role.get().getCode())) {
                return ApiResponse.error("不能删除系统管理员角色");
            }
            roleRepository.deleteById(id);
            log.info("删除角色成功: {}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除角色失败");
        }
    }

    @Transactional
    public ApiResponse<Role> setDefaultRole(Long id) {
        try {
            Optional<Role> role = roleRepository.findById(id);
            if (role.isEmpty()) {
                return ApiResponse.error("角色不存在");
            }
            clearDefaultRole();
            Role r = role.get();
            r.setIsDefault(true);
            Role saved = roleRepository.save(r);
            log.info("设置默认角色成功: {}", saved.getName());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("设置默认角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("设置默认角色失败");
        }
    }

    public ApiResponse<Role> getDefaultRole() {
        try {
            Optional<Role> role = roleRepository.findByIsDefaultTrue();
            return role.map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.error("未设置默认角色"));
        } catch (Exception e) {
            log.error("获取默认角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取默认角色失败");
        }
    }

    private void clearDefaultRole() {
        Optional<Role> defaultRole = roleRepository.findByIsDefaultTrue();
        if (defaultRole.isPresent()) {
            Role r = defaultRole.get();
            r.setIsDefault(false);
            roleRepository.save(r);
        }
    }
}
