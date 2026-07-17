/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理", description = "角色管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "获取所有角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:view')")
    @GetMapping
    public ApiResponse<List<Role>> getAllRoles() {
        return roleService.getAllRoles();
    }

    @Operation(summary = "获取角色详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:view')")
    @GetMapping("/{id}")
    public ApiResponse<Role> getRoleById(@PathVariable Long id) {
        return roleService.getRoleById(id);
    }

    @Operation(summary = "创建角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:create')")
    @PostMapping
    public ApiResponse<Role> createRole(@RequestBody Role role) {
        return roleService.createRole(role);
    }

    @Operation(summary = "更新角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:edit')")
    @PutMapping("/{id}")
    public ApiResponse<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        return roleService.updateRole(id, role);
    }

    @Operation(summary = "删除角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    @Operation(summary = "设为注册默认角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:edit')")
    @PutMapping("/{id}/set-default")
    public ApiResponse<Role> setDefaultRole(@PathVariable Long id) {
        return roleService.setDefaultRole(id);
    }

    @Operation(summary = "获取默认角色")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:view')")
    @GetMapping("/default")
    public ApiResponse<Role> getDefaultRole() {
        return roleService.getDefaultRole();
    }
}
