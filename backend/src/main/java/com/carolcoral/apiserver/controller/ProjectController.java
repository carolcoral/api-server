/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ProjectWithRoleDTO;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 项目控制器
 *
 * @author carolcoral
 */
@Tag(name = "项目管理", description = "项目管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/projects")
@Validated
public class ProjectController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectController.class);

    /**
     * 构造器
     */
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    private final ProjectService projectService;

    /**
     * 创建项目
     *
     * @param project 项目信息
     * @param request 请求
     * @return 创建的项目
     */
    @Operation(summary = "创建项目", description = "创建新项目（需要 project:create 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:create')")
    @PostMapping
    public ApiResponse<Project> createProject(@Parameter(description = "项目信息") @Valid @RequestBody Project project,
                                              HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        log.info("创建项目请求，用户: {}", userId);
        return projectService.createProject(project, userId);
    }

    /**
     * 更新项目
     *
     * @param project 项目信息
     * @return 更新的项目
     */
    @Operation(summary = "更新项目", description = "更新项目信息（需要 project:edit 权限或管理员，或项目创建者）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:edit')")
    @PutMapping
    public ApiResponse<Project> updateProject(@Parameter(description = "项目信息") @Valid @RequestBody Project project,
                                       HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        log.info("更新项目请求: {}, 用户: {}", project.getId(), userId);
        return projectService.updateProject(project, userId);
    }

    /**
     * 删除项目
     *
     * @param projectId 项目ID
     * @param request   请求
     * @return 删除结果
     */
    @Operation(summary = "删除项目", description = "删除项目，需要 project:delete 权限或管理员")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:delete')")
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> deleteProject(@Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
                                       HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        User.UserRole userRole = getUserRoleFromRequest(request);
        log.info("删除项目请求: {}, 用户={}, 角色={}", projectId, userId, userRole);
        return projectService.deleteProject(projectId, userId, userRole);
    }

    /**
     * 根据ID查询项目
     *
     * @param projectId 项目ID
     * @return 项目信息
     */
    @Operation(summary = "根据ID查询项目", description = "根据项目ID查询项目信息")
    @GetMapping("/{projectId}")
    public ApiResponse<Project> getProjectById(@Parameter(description = "项目ID", example = "1") @PathVariable Long projectId) {
        log.info("查询项目请求: {}", projectId);
        return projectService.getProjectById(projectId);
    }

    /**
     * 根据项目编码查询项目
     *
     * @param code 项目编码
     * @return 项目信息
     */
    @Operation(summary = "根据项目编码查询项目", description = "根据项目编码查询项目信息")
    @GetMapping("/code/{code}")
    public ApiResponse<Project> getProjectByCode(
            @Parameter(description = "项目编码", example = "ecmall")
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "项目编码只能包含字母、数字、下划线和连字符") String code) {
        log.info("查询项目请求，编码: {}", code);
        return projectService.getProjectByCode(code);
    }

    /**
     * 查询所有项目（支持分页和搜索）
     *
     * @param name    项目名称（模糊搜索）
     * @param code    项目编码（模糊搜索）
     * @param enabled 启用状态
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @param request 请求
     * @return 分页结果
     */
    @Operation(summary = "查询所有项目", description = "查询所有项目列表，支持分页和搜索（需要 project:view 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:view')")
    @GetMapping
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<Project>> getAllProjects(
            @Parameter(description = "项目名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "项目编码（模糊搜索）") @RequestParam(required = false) String code,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        log.info("查询所有项目请求: name={}, code={}, enabled={}, page={}, size={}", name, code, enabled, page, size);
        return projectService.searchProjects(name, code, enabled, page, size);
    }

    /**
     * 查询启用状态的项目
     *
     * @return 项目列表
     */
    @Operation(summary = "查询启用状态的项目", description = "查询所有启用状态的项目")
    @GetMapping("/enabled")
    public ApiResponse<java.util.List<Project>> getEnabledProjects() {
        log.info("查询启用状态项目请求");
        return projectService.getEnabledProjects();
    }

    /**
     * 查询用户有权限访问的项目（带角色信息，支持分页和搜索）
     *
     * @param name    项目名称（模糊搜索）
     * @param code    项目编码（模糊搜索）
     * @param enabled 启用状态
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @param request 请求
     * @return 分页结果（带用户角色）
     */
    @Operation(summary = "查询用户有权限访问的项目（带角色信息）", description = "查询当前用户有权限访问的项目列表，支持分页和搜索")
    @GetMapping("/accessible")
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<ProjectWithRoleDTO>> getAccessibleProjects(
            @Parameter(description = "项目名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "项目编码（模糊搜索）") @RequestParam(required = false) String code,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        User.UserRole userRole = getUserRoleFromRequest(request);
        log.info("查询用户有权限访问的项目请求，用户: {}, 角色: {}, name={}, code={}, enabled={}, page={}, size={}", 
            userId, userRole, name, code, enabled, page, size);
        return projectService.searchAccessibleProjects(userId, userRole, name, code, enabled, page, size);
    }

    /**
     * 获取当前用户可访问的全部项目列表（不分页，用于下拉选择）
     *
     * @param request 请求
     * @return 全部可访问项目列表（带用户角色）
     */
    @Operation(summary = "获取当前用户可访问的全部项目", description = "获取当前用户有权限访问的全部项目列表，不分页，用于下拉选择等场景")
    @GetMapping("/accessible/all")
    public ApiResponse<java.util.List<ProjectWithRoleDTO>> getAllAccessibleProjects(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        User.UserRole userRole = getUserRoleFromRequest(request);
        log.info("查询用户可访问的全部项目请求，用户: {}, 角色: {}", userId, userRole);
        return projectService.getAccessibleProjectsWithRole(userId, userRole);
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