/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ProjectMemberDTO;
import com.carolcoral.apiserver.entity.ProjectMember;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

/**
 * 项目成员控制器
 *
 * @author carolcoral
 */
@Tag(name = "项目成员管理", description = "项目成员管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/project-members")
public class ProjectMemberController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectMemberController.class);

    /**
     * 构造器
     */
    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    private final ProjectMemberService projectMemberService;

    /**
     * 添加项目成员
     * 系统管理员和项目管理员（创建者/管理员）可操作
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param role      成员角色
     * @param request   请求
     * @return 操作结果
     */
    @Operation(summary = "添加项目成员", description = "为项目添加成员，需要系统管理员或项目管理员权限")
    @PostMapping("/{projectId}/users/{userId}")
    public ApiResponse<ProjectMember> addProjectMember(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
            @Parameter(description = "用户ID", example = "2") @PathVariable Long userId,
            @Parameter(description = "成员角色", example = "ADMIN") @RequestParam(defaultValue = "MEMBER") String role,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        User.UserRole currentUserRole = getUserRoleFromRequest(request);

        // 权限检查：系统管理员或项目管理员
        if (!hasProjectManagementPermission(projectId, currentUserId, currentUserRole)) {
            log.warn("用户{}没有权限添加项目成员", currentUserId);
            return ApiResponse.error("没有权限执行此操作");
        }

        log.info("添加项目成员请求: 项目={}, 用户={}, 角色={}", projectId, userId, role);

        try {
            ProjectMember.MemberRole memberRole = ProjectMember.MemberRole.valueOf(role);
            return projectMemberService.addProjectMember(projectId, userId, memberRole);
        } catch (IllegalArgumentException e) {
            log.error("无效的角色类型: {}", role);
            return ApiResponse.error("无效的角色类型");
        }
    }

    /**
     * 更新项目成员角色
     * 系统管理员和项目管理员（创建者/管理员）可操作
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param role      新角色
     * @param request   请求
     * @return 操作结果
     */
    @Operation(summary = "更新项目成员角色", description = "更新项目成员的角色，需要系统管理员或项目管理员权限")
    @PutMapping("/{projectId}/users/{userId}")
    public ApiResponse<ProjectMember> updateMemberRole(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
            @Parameter(description = "用户ID", example = "2") @PathVariable Long userId,
            @Parameter(description = "新角色", example = "ADMIN") @RequestParam String role,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        User.UserRole currentUserRole = getUserRoleFromRequest(request);

        // 权限检查：系统管理员或项目管理员
        if (!hasProjectManagementPermission(projectId, currentUserId, currentUserRole)) {
            log.warn("用户{}没有权限更新项目成员角色", currentUserId);
            return ApiResponse.error("没有权限执行此操作");
        }

        log.info("更新项目成员角色请求: 项目={}, 用户={}, 角色={}", projectId, userId, role);

        try {
            ProjectMember.MemberRole memberRole = ProjectMember.MemberRole.valueOf(role);
            return projectMemberService.updateMemberRole(projectId, userId, memberRole);
        } catch (IllegalArgumentException e) {
            log.error("无效的角色类型: {}", role);
            return ApiResponse.error("无效的角色类型");
        }
    }

    /**
     * 移除项目成员
     * 系统管理员和项目管理员（创建者/管理员）可操作
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param request   请求
     * @return 操作结果
     */
    @Operation(summary = "移除项目成员", description = "从项目中移除成员，需要系统管理员或项目管理员权限")
    @DeleteMapping("/{projectId}/users/{userId}")
    public ApiResponse<Void> removeProjectMember(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
            @Parameter(description = "用户ID", example = "2") @PathVariable Long userId,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        User.UserRole currentUserRole = getUserRoleFromRequest(request);

        // 权限检查：系统管理员或项目管理员
        if (!hasProjectManagementPermission(projectId, currentUserId, currentUserRole)) {
            log.warn("用户{}没有权限移除项目成员", currentUserId);
            return ApiResponse.error("没有权限执行此操作");
        }

        log.info("移除项目成员请求: 项目={}, 用户={}", projectId, userId);
        return projectMemberService.removeProjectMember(projectId, userId);
    }

    /**
     * 查询项目成员列表
     * 项目成员可以查看
     *
     * @param projectId 项目ID
     * @return 成员列表
     */
    @Operation(summary = "查询项目成员列表", description = "查询指定项目的所有成员")
    @GetMapping("/{projectId}")
    public ApiResponse<java.util.List<ProjectMemberDTO>> getProjectMembers(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId) {
        log.info("查询项目成员列表请求: 项目={}", projectId);
        return projectMemberService.getProjectMembers(projectId);
    }

    /**
     * 查询用户在项目中的角色
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 角色
     */
    @Operation(summary = "查询用户在项目中的角色", description = "查询指定用户在项目中的角色")
    @GetMapping("/{projectId}/users/{userId}/role")
    public ApiResponse<ProjectMember.MemberRole> getUserRole(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
            @Parameter(description = "用户ID", example = "2") @PathVariable Long userId) {
        log.info("查询用户角色请求: 项目={}, 用户={}", projectId, userId);
        return projectMemberService.getUserRole(projectId, userId);
    }

    /**
     * 判断用户是否具有项目管理权限
     *
     * @param projectId       项目ID
     * @param currentUserId   当前用户ID
     * @param currentUserRole 当前用户角色
     * @return 是否有权限
     */
    private boolean hasProjectManagementPermission(Long projectId, Long currentUserId, User.UserRole currentUserRole) {
        // 系统管理员拥有所有权限
        if (currentUserRole == User.UserRole.ADMIN) {
            return true;
        }

        // 检查是否是项目管理员（创建者或管理员角色）
        return projectMemberService.isProjectAdmin(projectId, currentUserId);
    }

    /**
     * 从请求中获取用户ID
     *
     * @param request 请求
     * @return 用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        return userIdAttr != null ? (Long) userIdAttr : 0L;
    }

    /**
     * 从请求中获取用户角色
     *
     * @param request 请求
     * @return 用户角色
     */
    private User.UserRole getUserRoleFromRequest(HttpServletRequest request) {
        Object roleAttr = request.getAttribute("userRole");
        return roleAttr != null ? (User.UserRole) roleAttr : User.UserRole.USER;
    }
}
