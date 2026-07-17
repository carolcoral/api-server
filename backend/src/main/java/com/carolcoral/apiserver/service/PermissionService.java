/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.Permission;
import com.carolcoral.apiserver.entity.RolePermission;
import com.carolcoral.apiserver.repository.PermissionRepository;
import com.carolcoral.apiserver.repository.RolePermissionRepository;
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
     * 获取所有权限（按前端菜单顺序排列分组）
     * 排序规则与 DashboardLayout.vue 侧边栏菜单顺序一致
     */
    /**
     * 权限分组排序，严格遵循前端 DashboardLayout.vue 侧边栏菜单顺序（从上到下）。
     * 不在菜单中直接展示的权限组（如项目成员管理、认证管理等）排在所属菜单项的附近。
     */
    private static final List<String> GROUP_ORDER = List.of(
        "仪表盘管理",       // 1. 仪表盘 /dashboard
        "项目管理",         // 2. 业务管理 > 项目管理 /projects
        "项目成员管理",     //    （项目管理子功能）
        "接口管理",         //    业务管理 > 接口管理 /apis
        "自定义代码模板管理",//   业务管理 > 代码模板 /code-templates
        "请求录制与回放",   //    业务管理 > 录制回放 /record-replay
        "AI聊天",          // 3. AI 对话 /ai-chat
        "统计管理",         // 4. 数据统计 > 统计 /statistics
        "请求日志",         //    数据统计 > 调试面板 /debug-panel
        "用户管理",         // 5. 权限管理 > 用户管理 /users
        "角色管理",         //    权限管理 > 角色管理 /roles
        "权限管理",         //    权限管理 > 权限管理 /permissions
        "AI用户自助",       // 6. AI 服务 /ai-subscription
        "邮件模板管理",     // 7. 系统管理 > 邮件模板 /email-templates
        "AI配置",           //    系统管理 > AI 设置 /ai-settings
        "AI配置管理",       //    （AI设置别名，同组紧随）
        "AI管理",           //    系统管理 > AI 服务管理 /ai-service
        "AI代理",           //    （AI代理功能，附属于AI管理）
        "系统配置",         //    系统管理 > 系统设置 /settings
        "系统信息",         //    （系统信息子页面）
        "系统管理",         //    （系统管理其他）
        "认证管理"          //    （认证相关，无独立菜单入口）
    );

    public ApiResponse<List<Map<String, Object>>> getAllPermissionsGrouped() {
        try {
            List<Permission> all = permissionRepository.findAllByOrderByIdDesc();
            Map<String, List<Permission>> grouped = all.stream()
                    .collect(Collectors.groupingBy(Permission::getGroupName, LinkedHashMap::new, Collectors.toList()));

            // 按前端菜单顺序排序分组
            List<Map.Entry<String, List<Permission>>> sortedEntries = new ArrayList<>(grouped.entrySet());
            sortedEntries.sort((a, b) -> {
                int idxA = GROUP_ORDER.indexOf(a.getKey());
                int idxB = GROUP_ORDER.indexOf(b.getKey());
                if (idxA >= 0 && idxB >= 0) return Integer.compare(idxA, idxB);
                if (idxA >= 0) return -1;
                if (idxB >= 0) return 1;
                return a.getKey().compareTo(b.getKey());
            });

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, List<Permission>> entry : sortedEntries) {
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
     * 创建新权限
     */
    @Transactional
    public ApiResponse<Permission> createPermission(Permission permission) {
        try {
            if (permissionRepository.existsByCode(permission.getCode())) {
                return ApiResponse.error("权限编码已存在");
            }
            Permission saved = permissionRepository.save(permission);
            log.info("创建权限成功: id={}, code={}", saved.getId(), saved.getCode());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("创建权限失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建权限失败");
        }
    }

    /**
     * 更新权限
     */
    @Transactional
    public ApiResponse<Permission> updatePermission(Long id, Permission updated) {
        try {
            Permission existing = permissionRepository.findById(id).orElse(null);
            if (existing == null) {
                return ApiResponse.error("权限不存在");
            }
            // 检查编码是否与其他权限冲突
            if (!existing.getCode().equals(updated.getCode()) && permissionRepository.existsByCode(updated.getCode())) {
                return ApiResponse.error("权限编码已存在");
            }
            existing.setName(updated.getName());
            existing.setCode(updated.getCode());
            existing.setGroupName(updated.getGroupName());
            existing.setType(updated.getType());
            if (updated.getSortOrder() != null) {
                existing.setSortOrder(updated.getSortOrder());
            }
            Permission saved = permissionRepository.save(existing);
            log.info("更新权限成功: id={}, code={}", saved.getId(), saved.getCode());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("更新权限失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新权限失败");
        }
    }

    /**
     * 删除权限（同时清理角色-权限关联）
     */
    @Transactional
    public ApiResponse<Void> deletePermission(Long id) {
        try {
            if (!permissionRepository.existsById(id)) {
                return ApiResponse.error("权限不存在");
            }
            // 先删除角色-权限关联
            rolePermissionRepository.deleteByPermissionId(id);
            rolePermissionRepository.flush();
            // 再删除权限本身
            permissionRepository.deleteById(id);
            log.info("删除权限成功: id={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除权限失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除权限失败");
        }
    }

    /**
     * 获取所有权限编码
     */
    public Set<String> getAllPermissionCodes() {
        return permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());
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
