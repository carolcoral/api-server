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

import java.util.List;
import java.util.Map;

@Tag(name = "权限管理", description = "权限管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:view')")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
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
}
