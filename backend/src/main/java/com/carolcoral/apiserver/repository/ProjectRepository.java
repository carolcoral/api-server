/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 项目Repository
 *
 * @author carolcoral
 */
@Tag(name = "项目管理", description = "项目数据访问接口")
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    /**
     * 根据项目编码查找项目
     *
     * @param code 项目编码
     * @return 项目Optional
     */
    @Operation(summary = "根据项目编码查找项目")
    Optional<Project> findByCode(String code);

    /**
     * 根据项目名称模糊查询
     *
     * @param name 项目名称（模糊）
     * @return 项目列表
     */
    @Operation(summary = "根据项目名称模糊查询")
    List<Project> findByNameLike(String name);

    /**
     * 根据启用状态查询项目
     *
     * @param enabled 是否启用
     * @return 项目列表
     */
    @Operation(summary = "根据启用状态查询项目")
    List<Project> findByEnabled(Boolean enabled);

    /**
     * 根据创建人查询项目
     *
     * @param userId 用户ID
     * @return 项目列表
     */
    @Operation(summary = "根据创建人查询项目")
    List<Project> findByCreateUserId(Long userId);

    /**
     * 根据用户ID查询用户参与的项目
     *
     * @param userId 用户ID
     * @return 项目列表
     */
    @Operation(summary = "根据用户ID查询用户参与的项目")
    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.id IN (" +
            "  SELECT pm.projectId FROM ProjectMember pm " +
            "  WHERE pm.userId = :userId" +
            ")")
    List<Project> findProjectsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户有权限访问的项目
     * 仅包括用户在项目成员表中的项目（项目管理员或成员用户角色）
     *
     * @param userId 用户ID
     * @return 项目列表
     */
    @Operation(summary = "查询用户有权限访问的项目")
    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.id IN (" +
            "  SELECT pm.projectId FROM ProjectMember pm " +
            "  WHERE pm.userId = :userId" +
            ")")
    List<Project> findAccessibleProjectsByUserId(@Param("userId") Long userId);

    /**
     * 判断项目编码是否存在
     *
     * @param code 项目编码
     * @return 是否存在
     */
    @Operation(summary = "判断项目编码是否存在")
    boolean existsByCode(String code);

    /**
     * 根据项目编码删除项目
     *
     * @param code 项目编码
     */
    @Operation(summary = "根据项目编码删除项目")
    void deleteByCode(String code);
}
