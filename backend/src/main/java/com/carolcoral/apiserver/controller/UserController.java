/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @author carolcoral
 */
@Tag(name = "用户管理", description = "用户管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserController.class);

    /**
     * 构造器
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    private final UserService userService;

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建的用户
     */
    @Operation(summary = "创建用户", description = "创建新用户，需要管理员权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:create')")
    @PostMapping
    public ApiResponse<User> createUser(@Parameter(description = "用户信息") @Valid @RequestBody User user) {
        log.info("创建用户请求: {}", user.getUsername());
        return userService.createUser(user);
    }

    /**
     * 管理员更新用户信息（角色、状态等）
     *
     * @param userId 用户ID
     * @param user 用户信息
     * @return 更新的用户
     */
    @Operation(summary = "管理员更新用户", description = "管理员更新用户信息，需要管理员权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:edit')")
    @PutMapping("/{userId}")
    public ApiResponse<User> updateUser(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Parameter(description = "用户信息") @RequestBody User user) {
        log.info("管理员更新用户请求: {}", userId);
        user.setId(userId);
        return userService.updateUser(user);
    }

    /**
     * 更新用户个人信息
     *
     * @param user 用户信息
     * @return 更新的用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息，需要管理员权限或本人操作")
    @PreAuthorize("hasRole('ADMIN') or #user.id == authentication.principal.id")
    @PutMapping("/update-profile")
    public ApiResponse<User> updateUserProfile(@RequestBody User user) {
        log.info("更新用户信息请求: {}", user.getId());
        return userService.updateUserProfile(user);
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/profile")
    @Operation(summary = "获取当前用户信息", description = "需要登录")
    public ApiResponse<User> getCurrentUserProfile() {
        return userService.getCurrentUser();
    }

    /**
     * 修改密码
     *
     * @param passwordChangeRequest 密码修改请求
     * @return 操作结果
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "需要登录，原密码必须正确")
    public ApiResponse<Void> changePassword(@RequestBody UserService.PasswordChangeRequest passwordChangeRequest) {
        return userService.changePassword(passwordChangeRequest);
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @Operation(summary = "删除用户", description = "删除用户，需要管理员权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:delete')")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@Parameter(description = "用户ID", example = "1") @PathVariable Long userId) {
        log.info("删除用户请求: {}", userId);
        return userService.deleteUser(userId);
    }

    /**
     * 获取可用角色列表（供用户管理前端下拉选择）
     *
     * @return 角色列表
     */
    @Operation(summary = "获取可用角色列表", description = "获取系统中所有角色，用于用户管理页面的角色下拉选择")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:view')")
    @GetMapping("/roles")
    public ApiResponse<java.util.List<com.carolcoral.apiserver.entity.Role>> getAvailableRoles() {
        return userService.getAvailableRoles();
    }

    /**
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @Operation(summary = "根据ID查询用户", description = "根据用户ID查询用户信息")
    @GetMapping("/{userId}")
    public ApiResponse<User> getUserById(@Parameter(description = "用户ID", example = "1") @PathVariable Long userId) {
        log.info("查询用户请求: {}", userId);
        return userService.getUserById(userId);
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    @Operation(summary = "根据用户名查询用户", description = "根据用户名查询用户信息")
    @GetMapping("/username/{username}")
    public ApiResponse<User> getUserByUsername(
            @Parameter(description = "用户名", example = "admin")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线") String username) {
        log.info("查询用户请求: {}", username);
        return userService.getUserByUsername(username);
    }

    /**
     * 搜索用户（支持用户名或邮箱模糊搜索，所有已认证用户可用）
     *
     * @param keyword 搜索关键词
     * @return 用户列表
     */
    @Operation(summary = "搜索用户", description = "支持用户名或邮箱模糊搜索，所有已认证用户可用")
    @GetMapping("/search")
    public ApiResponse<java.util.List<User>> searchUsers(
            @Parameter(description = "搜索关键词", example = "admin")
            @RequestParam(required = false) String keyword) {
        log.info("搜索用户请求，关键词: {}", keyword);
        return userService.searchUsers(keyword);
    }

    /**
     * 查询所有用户（支持分页和搜索）
     *
     * @param username 用户名（模糊搜索）
     * @param email    邮箱（模糊搜索）
     * @param role     角色
     * @param enabled  启用状态
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 分页结果
     */
    @Operation(summary = "查询所有用户", description = "查询所有用户列表，支持分页和搜索，需要管理员权限")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:view')")
    @GetMapping
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<User>> getAllUsers(
            @Parameter(description = "用户名（模糊搜索）") @RequestParam(required = false) String username,
            @Parameter(description = "邮箱（模糊搜索）") @RequestParam(required = false) String email,
            @Parameter(description = "角色ID（通过roleId匹配自定义角色）") @RequestParam(required = false) Long roleId,
            @Parameter(description = "基础角色枚举（USER/ADMIN）") @RequestParam(required = false) User.UserRole role,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("查询所有用户请求: username={}, email={}, roleId={}, role={}, enabled={}, page={}, size={}",
            username, email, roleId, role, enabled, page, size);
        return userService.searchUsers(username, email, roleId, role, enabled, page, size);
    }

    /**
     * 根据角色查询用户
     *
     * @param role 用户角色
     * @return 用户列表
     */
    @Operation(summary = "根据角色查询用户", description = "根据用户角色查询用户列表")
    @GetMapping("/role/{role}")
    public ApiResponse<java.util.List<User>> getUsersByRole(@Parameter(description = "用户角色", example = "USER") @PathVariable User.UserRole role) {
        log.info("查询用户请求，角色: {}", role);
        return userService.getUsersByRole(role);
    }

    /**
     * 查询启用状态的用户
     *
     * @return 用户列表
     */
    @Operation(summary = "查询启用状态的用户", description = "查询所有启用状态的用户")
    @GetMapping("/enabled")
    public ApiResponse<java.util.List<User>> getEnabledUsers() {
        log.info("查询启用状态用户请求");
        return userService.getEnabledUsers();
    }
}
