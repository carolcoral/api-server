/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.entity.Permission;
import com.carolcoral.mockserver.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.carolcoral.mockserver.service.PermissionScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "权限管理", description = "权限管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:view')")
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionScanner permissionScanner;

    public PermissionController(PermissionService permissionService, PermissionScanner permissionScanner) {
        this.permissionService = permissionService;
        this.permissionScanner = permissionScanner;
    }

    @Operation(summary = "获取所有权限（按分组）")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAllPermissions() {
        return permissionService.getAllPermissionsGrouped();
    }

    @Operation(summary = "获取角色拥有的权限ID列表")
    @GetMapping("/role/{roleId}")
    public ApiResponse<List<Long>> getRolePermissions(@PathVariable Long roleId) {
        return permissionService.getRolePermissionIds(roleId);
    }

    @Operation(summary = "为角色分配权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:assign')")
    @PutMapping("/role/{roleId}")
    public ApiResponse<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        return permissionService.assignPermissions(roleId, permissionIds);
    }

    @Operation(summary = "创建新权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:create')")
    @PostMapping
    public ApiResponse<Permission> createPermission(@RequestBody Permission permission) {
        return permissionService.createPermission(permission);
    }

    @Operation(summary = "更新权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:edit')")
    @PutMapping("/{id}")
    public ApiResponse<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        return permissionService.updatePermission(id, permission);
    }

    @Operation(summary = "删除权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        return permissionService.deletePermission(id);
    }

    // ==================== 权限扫描相关接口 ====================

    @Operation(summary = "扫描系统权限", description = "扫描所有 @PreAuthorize 注解中的权限编码，返回扫描结果")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:view')")
    @GetMapping("/scan")
    public ApiResponse<List<Map<String, Object>>> scanPermissions() {
        List<Map<String, Object>> result = permissionScanner.getAllScannedPermissions().stream()
            .map(info -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("code", info.getCode());
                map.put("name", info.getName());
                map.put("groupName", info.getGroupName());
                map.put("type", info.getType());
                map.put("controllerName", info.getControllerName());
                map.put("methodName", info.getMethodName());
                map.put("httpMethod", info.getHttpMethod());
                return map;
            })
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取权限建议数据", description = "返回以分组为一级的级联建议列表，名称/编码按分组过滤，已使用的标记为 disabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:view')")
    @GetMapping("/suggestions")
    public ApiResponse<Map<String, Object>> getPermissionSuggestions() {
        // 获取已存在的权限编码
        Set<String> existingCodes = permissionService.getAllPermissionCodes();

        // 按分组整理：groupName -> { codes, names }
        Map<String, List<Map<String, Object>>> groupedByGroup = new java.util.LinkedHashMap<>();

        for (PermissionScanner.PermissionScanInfo info : permissionScanner.getAllScannedPermissions()) {
            String group = info.getGroupName() != null ? info.getGroupName() : "";
            groupedByGroup.computeIfAbsent(group, k -> new ArrayList<>());

            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("code", info.getCode());
            item.put("name", info.getName());
            item.put("type", info.getType());
            item.put("disabled", existingCodes.contains(info.getCode()));
            groupedByGroup.get(group).add(item);
        }

        // 构建分组下拉列表（含分组下所有编码和名称）
        List<Map<String, Object>> groupSuggestions = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByGroup.entrySet()) {
            Map<String, Object> groupItem = new java.util.LinkedHashMap<>();
            groupItem.put("value", entry.getKey());
            groupItem.put("label", entry.getKey());
            groupItem.put("codes", entry.getValue().stream()
                    .map(i -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("value", i.get("code"));
                        m.put("label", i.get("code") + " (" + i.get("name") + ")");
                        m.put("name", i.get("name"));
                        m.put("type", i.get("type"));
                        m.put("disabled", i.get("disabled"));
                        return m;
                    })
                    .collect(Collectors.toList()));
            groupItem.put("names", entry.getValue().stream()
                    .map(i -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("value", i.get("name"));
                        m.put("label", i.get("name"));
                        m.put("code", i.get("code"));
                        return m;
                    })
                    .collect(Collectors.toList()));
            groupSuggestions.add(groupItem);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("groups", groupSuggestions);
        result.put("existingCodes", existingCodes);

        return ApiResponse.success(result);
    }
}
