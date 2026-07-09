/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.entity.Permission;
import com.carolcoral.mockserver.entity.RolePermission;
import com.carolcoral.mockserver.repository.PermissionRepository;
import com.carolcoral.mockserver.repository.RolePermissionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionService.class);

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final JdbcTemplate jdbcTemplate;

    public PermissionService(PermissionRepository permissionRepository,
                             RolePermissionRepository rolePermissionRepository,
                             JdbcTemplate jdbcTemplate) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取所有权限（按分组和排序号排列）
     */
    public ApiResponse<List<Map<String, Object>>> getAllPermissionsGrouped() {
        try {
            List<Permission> all = permissionRepository.findAllByOrderBySortOrderAsc();
            Map<String, List<Permission>> grouped = all.stream()
                    .collect(Collectors.groupingBy(Permission::getGroupName, LinkedHashMap::new, Collectors.toList()));

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, List<Permission>> entry : grouped.entrySet()) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("groupName", entry.getKey());
                group.put("permissions", entry.getValue());
                result.add(group);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取权限列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取权限列表失败");
        }
    }

    /**
     * 获取某角色的权限ID列表
     */
    public ApiResponse<List<Long>> getRolePermissionIds(Long roleId) {
        try {
            List<RolePermission> rps = rolePermissionRepository.findByRoleId(roleId);
            List<Long> permIds = rps.stream().map(RolePermission::getPermissionId).collect(Collectors.toList());
            return ApiResponse.success(permIds);
        } catch (Exception e) {
            log.error("获取角色权限失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取角色权限失败");
        }
    }

    /**
     * 为角色分配权限（使用原生 SQL 避免 SQLite JPA 方言的 INSERT OR IGNORE 不支持问题）
     */
    @Transactional
    public ApiResponse<Void> assignPermissions(Long roleId, List<Long> permissionIds) {
        try {
            // 先删除旧权限
            rolePermissionRepository.deleteByRoleId(roleId);
            rolePermissionRepository.flush();
            // 用 INSERT OR IGNORE 批量插入，避免唯一约束冲突
            if (permissionIds != null && !permissionIds.isEmpty()) {
                for (Long permId : permissionIds) {
                    jdbcTemplate.update(
                        "INSERT OR IGNORE INTO t_role_permission (role_id, permission_id) VALUES (?, ?)",
                        roleId, permId);
                }
            }
            log.info("角色权限分配成功: roleId={}, permCount={}", roleId, permissionIds != null ? permissionIds.size() : 0);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配角色权限失败: {}", e.getMessage(), e);
            return ApiResponse.error("分配权限失败");
        }
    }

    /**
     * 获取当前用户的所有权限编码列表
     */
    public Set<String> getUserPermissionCodes(List<Long> roleIds) {
        try {
            if (roleIds == null || roleIds.isEmpty()) return Collections.emptySet();
            List<RolePermission> rps = rolePermissionRepository.findByRoleIdIn(roleIds);
            Set<Long> permIds = rps.stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
            if (permIds.isEmpty()) return Collections.emptySet();
            return permissionRepository.findAllById(permIds).stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("获取用户权限编码失败: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }
}
