/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ProjectMemberDTO;
import com.carolcoral.apiserver.entity.ProjectMember;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.ProjectMemberRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 项目成员服务类
 *
 * @author carolcoral
 */
@Tag(name = "项目成员服务", description = "项目成员业务逻辑处理")
@Service
public class ProjectMemberService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProjectMemberService.class);

    /**
     * 构造器
     */
    public ProjectMemberService(ProjectMemberRepository projectMemberRepository, UserRepository userRepository) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
    }

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    /**
     * 添加项目成员
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param role      成员角色
     * @return 操作结果
     */
    @Operation(summary = "添加项目成员")
    @Transactional
    public ApiResponse<ProjectMember> addProjectMember(@Parameter(description = "项目ID") Long projectId,
                                                     @Parameter(description = "用户ID") Long userId,
                                                     @Parameter(description = "成员角色") ProjectMember.MemberRole role) {
        try {
            // 检查用户是否已是项目成员
            Optional<ProjectMember> existingMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
            if (existingMember.isPresent()) {
                return ApiResponse.error("用户已是项目成员");
            }

            ProjectMember projectMember = new ProjectMember();
            projectMember.setProjectId(projectId);
            projectMember.setUserId(userId);
            projectMember.setRole(role);

            ProjectMember savedMember = projectMemberRepository.save(projectMember);
            log.info("添加项目成员成功: 项目={}, 用户={}, 角色={}", projectId, userId, role);

            // 返回分离后的对象，避免懒加载问题
            ProjectMember result = new ProjectMember();
            result.setId(savedMember.getId());
            result.setProjectId(savedMember.getProjectId());
            result.setUserId(savedMember.getUserId());
            result.setRole(savedMember.getRole());
            result.setCreateTime(savedMember.getCreateTime());
            result.setUpdateTime(savedMember.getUpdateTime());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("添加项目成员失败: {}", e.getMessage(), e);
            return ApiResponse.error("添加项目成员失败，请稍后重试");
        }
    }

    /**
     * 更新项目成员角色
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @param role      新角色
     * @return 操作结果
     */
    @Operation(summary = "更新项目成员角色")
    @Transactional
    public ApiResponse<ProjectMember> updateMemberRole(@Parameter(description = "项目ID") Long projectId,
                                                  @Parameter(description = "用户ID") Long userId,
                                                  @Parameter(description = "新角色") ProjectMember.MemberRole role) {
        try {
            Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
            if (!memberOpt.isPresent()) {
                return ApiResponse.error("项目成员不存在");
            }

            ProjectMember member = memberOpt.get();
            member.setRole(role);

            ProjectMember updatedMember = projectMemberRepository.save(member);
            log.info("更新项目成员角色成功: 项目={}, 用户={}, 角色={}", projectId, userId, role);

            // 返回分离后的对象，避免懒加载问题
            ProjectMember result = new ProjectMember();
            result.setId(updatedMember.getId());
            result.setProjectId(updatedMember.getProjectId());
            result.setUserId(updatedMember.getUserId());
            result.setRole(updatedMember.getRole());
            result.setCreateTime(updatedMember.getCreateTime());
            result.setUpdateTime(updatedMember.getUpdateTime());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("更新项目成员角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新项目成员角色失败，请稍后重试");
        }
    }

    /**
     * 移除项目成员
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 操作结果
     */
    @Operation(summary = "移除项目成员")
    @Transactional
    public ApiResponse<Void> removeProjectMember(@Parameter(description = "项目ID") Long projectId,
                                                  @Parameter(description = "用户ID") Long userId) {
        try {
            Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
            if (!memberOpt.isPresent()) {
                return ApiResponse.error("项目成员不存在");
            }

            ProjectMember member = memberOpt.get();

            projectMemberRepository.delete(member);
            log.info("移除项目成员成功: 项目={}, 用户={}", projectId, userId);
            return ApiResponse.success();

        } catch (Exception e) {
            log.error("移除项目成员失败: {}", e.getMessage(), e);
            return ApiResponse.error("移除项目成员失败，请稍后重试");
        }
    }

    /**
     * 查询项目成员列表
     *
     * @param projectId 项目ID
     * @return 成员列表
     */
    @Operation(summary = "查询项目成员列表")
    public ApiResponse<List<ProjectMemberDTO>> getProjectMembers(@Parameter(description = "项目ID") Long projectId) {
        try {
            List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);

            // 获取所有用户ID
            List<Long> userIds = members.stream()
                    .map(ProjectMember::getUserId)
                    .distinct()
                    .toList();

            // 批量查询用户信息
            Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                    userRepository.findAllById(userIds).stream()
                            .collect(Collectors.toMap(User::getId, u -> u));

            // 构建DTO列表
            List<ProjectMemberDTO> result = members.stream().map(member -> {
                User user = userMap.get(member.getUserId());
                return ProjectMemberDTO.builder()
                        .id(member.getId())
                        .projectId(member.getProjectId())
                        .userId(member.getUserId())
                        .username(user != null ? user.getUsername() : null)
                        .email(user != null ? user.getEmail() : null)
                        .role(member.getRole())
                        .createTime(member.getCreateTime())
                        .updateTime(member.getUpdateTime())
                        .build();
            }).toList();

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询项目成员列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询项目成员列表失败，请稍后重试");
        }
    }

    /**
     * 查询用户在项目中的角色
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 角色
     */
    @Operation(summary = "查询用户在项目中的角色")
    public ApiResponse<ProjectMember.MemberRole> getUserRole(@Parameter(description = "项目ID") Long projectId,
                                                        @Parameter(description = "用户ID") Long userId) {
        try {
            Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
            if (!memberOpt.isPresent()) {
                return ApiResponse.error("用户不是项目成员");
            }
            return ApiResponse.success(memberOpt.get().getRole());
        } catch (Exception e) {
            log.error("查询用户角色失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询用户角色失败，请稍后重试");
        }
    }

    /**
     * 判断用户是否是项目管理员
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 是否是管理员
     */
    @Operation(summary = "判断用户是否是项目管理员")
    public boolean isProjectAdmin(Long projectId, Long userId) {
        return projectMemberRepository
                .findByProjectIdAndUserIdAndRole(projectId, userId, ProjectMember.MemberRole.ADMIN)
                .isPresent();
    }
}
