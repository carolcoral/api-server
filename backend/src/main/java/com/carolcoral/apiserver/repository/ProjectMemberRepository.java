/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.ProjectMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 项目成员Repository
 *
 * @author carolcoral
 */
@Tag(name = "项目成员管理", description = "项目成员数据访问接口")
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /**
     * 根据项目ID查询成员列表
     *
     * @param projectId 项目ID
     * @return 成员列表
     */
    @Operation(summary = "根据项目ID查询成员列表")
    List<ProjectMember> findByProjectId(Long projectId);

    /**
     * 根据用户ID查询成员列表
     *
     * @param userId 用户ID
     * @return 成员列表
     */
    @Operation(summary = "根据用户ID查询成员列表")
    List<ProjectMember> findByUserId(Long userId);

    /**
     * 根据项目ID和用户ID查询成员
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 成员Optional
     */
    @Operation(summary = "根据项目ID和用户ID查询成员")
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    /**
     * 根据项目ID和角色查询成员列表
     *
     * @param projectId 项目ID
     * @param role      成员角色
     * @return 成员列表
     */
    @Operation(summary = "根据项目ID和角色查询成员列表")
    List<ProjectMember> findByProjectIdAndRole(Long projectId, ProjectMember.MemberRole role);

    /**
     * 删除指定项目的所有成员
     *
     * @param projectId 项目ID
     */
    @Operation(summary = "删除指定项目的所有成员")
    void deleteByProjectId(Long projectId);

    /**
     * 删除指定项目的指定角色成员
     *
     * @param projectId 项目ID
     * @param role      成员角色
     */
    @Operation(summary = "删除指定项目的指定角色成员")
    @Query("DELETE FROM ProjectMember pm WHERE pm.projectId = :projectId AND pm.role = :role")
    void deleteByProjectIdAndRole(@Param("projectId") Long projectId, @Param("role") ProjectMember.MemberRole role);

    /**
     * 判断用户是否是项目的创建者
     *
     * @param projectId 项目ID
     * @param userId    用户ID
     * @return 创建者Optional
     */
    @Operation(summary = "判断用户是否是项目的创建者")
    Optional<ProjectMember> findByProjectIdAndUserIdAndRole(Long projectId, Long userId, ProjectMember.MemberRole role);
}
