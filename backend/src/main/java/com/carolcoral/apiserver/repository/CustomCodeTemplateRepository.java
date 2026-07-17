/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.CustomCodeTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 自定义代码模板Repository
 *
 * @author carolcoral
 */
@Tag(name = "自定义代码模板管理", description = "自定义代码模板数据访问接口")
@Repository
public interface CustomCodeTemplateRepository extends JpaRepository<CustomCodeTemplate, Long>, JpaSpecificationExecutor<CustomCodeTemplate> {

    /**
     * 根据项目ID查询模板列表
     *
     * @param projectId 项目ID
     * @return 模板列表
     */
    @Operation(summary = "根据项目ID查询模板列表")
    List<CustomCodeTemplate> findByProjectId(Long projectId);

    /**
     * 根据项目ID和启用状态查询模板列表
     *
     * @param projectId 项目ID
     * @param enabled   是否启用
     * @return 模板列表
     */
    @Operation(summary = "根据项目ID和启用状态查询模板列表")
    List<CustomCodeTemplate> findByProjectIdAndEnabled(Long projectId, Boolean enabled);

    /**
     * 根据创建人ID查询模板列表
     *
     * @param userId 用户ID
     * @return 模板列表
     */
    @Operation(summary = "根据创建人ID查询模板列表")
    List<CustomCodeTemplate> findByCreateUserId(Long userId);

    /**
     * 查询用户有权限访问的项目下的所有模板
     * 包括用户创建的项目和用户作为成员的项目
     *
     * @param userId 用户ID
     * @return 模板列表
     */
    @Operation(summary = "查询用户有权限访问的项目下的所有模板")
    @Query("SELECT t FROM CustomCodeTemplate t " +
            "WHERE t.project.id IN (" +
            "  SELECT p.id FROM Project p " +
            "  WHERE p.createUserId = :userId OR p.id IN (" +
            "    SELECT pm.projectId FROM ProjectMember pm " +
            "    WHERE pm.userId = :userId" +
            "  )" +
            ")")
    List<CustomCodeTemplate> findAccessibleTemplatesByUserId(@Param("userId") Long userId);

    /**
     * 根据项目ID删除模板
     *
     * @param projectId 项目ID
     */
    @Operation(summary = "根据项目ID删除模板")
    void deleteByProjectId(Long projectId);

    /**
     * 查询所有系统默认模板（isSystem=true, project=null）
     *
     * @return 系统模板列表
     */
    @Operation(summary = "查询所有系统默认模板")
    List<CustomCodeTemplate> findByIsSystemTrue();

    /**
     * 查询所有已启用的系统默认模板
     *
     * @return 已启用的系统模板列表
     */
    @Operation(summary = "查询所有已启用的系统默认模板")
    List<CustomCodeTemplate> findByIsSystemTrueAndEnabledTrue();
}
