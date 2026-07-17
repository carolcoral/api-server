/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.Project;
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
 * 自定义接口Repository
 *
 * @author carolcoral
 */
@Tag(name = "接口管理", description = "自定义接口数据访问接口")
@Repository
public interface MockApiRepository extends JpaRepository<MockApi, Long>, JpaSpecificationExecutor<MockApi> {

    /**
     * 根据项目ID查询接口列表
     *
     * @param projectId 项目ID
     * @return 接口列表
     */
    @Operation(summary = "根据项目ID查询接口列表")
    List<MockApi> findByProjectId(Long projectId);

    /**
     * 根据项目查询接口列表
     *
     * @param project 项目
     * @return 接口列表
     */
    @Operation(summary = "根据项目查询接口列表")
    List<MockApi> findByProject(Project project);

    /**
     * 根据接口路径和请求方法查询接口
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 接口Optional
     */
    @Operation(summary = "根据接口路径和请求方法查询接口")
    Optional<MockApi> findByPathAndMethod(String path, MockApi.HttpMethod method);

    /**
     * 根据接口路径查询接口
     *
     * @param path 接口路径
     * @return 接口Optional
     */
    @Operation(summary = "根据接口路径查询接口")
    Optional<MockApi> findByPath(String path);

    /**
     * 根据接口名称模糊查询
     *
     * @param name 接口名称（模糊）
     * @return 接口列表
     */
    @Operation(summary = "根据接口名称模糊查询")
    List<MockApi> findByNameLike(String name);

    /**
     * 根据启用状态查询接口
     *
     * @param enabled 是否启用
     * @return 接口列表
     */
    @Operation(summary = "根据启用状态查询接口")
    List<MockApi> findByEnabled(Boolean enabled);

    /**
     * 根据请求类型查询接口
     *
     * @param requestType 请求类型
     * @return 接口列表
     */
    @Operation(summary = "根据请求类型查询接口")
    List<MockApi> findByRequestType(MockApi.RequestType requestType);

    /**
     * 根据项目ID和启用状态查询接口
     *
     * @param projectId 项目ID
     * @param enabled   是否启用
     * @return 接口列表
     */
    @Operation(summary = "根据项目ID和启用状态查询接口")
    List<MockApi> findByProjectIdAndEnabled(Long projectId, Boolean enabled);

    /**
     * 根据创建人查询接口
     *
     * @param userId 用户ID
     * @return 接口列表
     */
    @Operation(summary = "根据创建人查询接口")
    List<MockApi> findByCreateUserId(Long userId);

    /**
     * 根据项目编码和接口路径查询接口
     *
     * @param projectCode 项目编码
     * @param path        接口路径
     * @return 接口Optional
     */
    @Operation(summary = "根据项目编码和接口路径查询接口")
    @Query("SELECT a FROM MockApi a JOIN a.project p WHERE p.code = :projectCode AND a.path = :path")
    Optional<MockApi> findByProjectCodeAndPath(@Param("projectCode") String projectCode, @Param("path") String path);

    /**
     * 根据项目编码、接口路径和请求方法查询接口
     *
     * @param projectCode 项目编码
     * @param path        接口路径
     * @param method      请求方法
     * @return 接口Optional
     */
    @Operation(summary = "根据项目编码、接口路径和请求方法查询接口")
    @Query("SELECT a FROM MockApi a JOIN a.project p WHERE p.code = :projectCode AND a.path = :path AND a.method = :method")
    Optional<MockApi> findByProjectCodeAndPathAndMethod(@Param("projectCode") String projectCode,
                                                        @Param("path") String path,
                                                        @Param("method") MockApi.HttpMethod method);

    /**
     * 根据项目ID、接口路径和请求方法查询接口
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @param method    请求方法
     * @return 接口Optional
     */
    @Operation(summary = "根据项目ID、接口路径和请求方法查询接口")
    @Query("SELECT a FROM MockApi a WHERE a.project.id = :projectId AND a.path = :path AND a.method = :method")
    Optional<MockApi> findByProjectIdAndPathAndMethod(@Param("projectId") Long projectId,
                                                     @Param("path") String path,
                                                     @Param("method") MockApi.HttpMethod method);

    /**
     * 根据项目ID和接口路径查询接口（用于检测同一路径下不同请求方式的接口）
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @return 接口Optional
     */
    @Operation(summary = "根据项目ID和接口路径查询接口")
    @Query("SELECT a FROM MockApi a WHERE a.project.id = :projectId AND a.path = :path")
    Optional<MockApi> findByProjectIdAndPath(@Param("projectId") Long projectId,
                                              @Param("path") String path);

    /**
     * 判断接口路径是否存在
     *
     * @param path 接口路径
     * @return 是否存在
     */
    @Operation(summary = "判断接口路径是否存在")
    boolean existsByPath(String path);

    /**
     * 判断接口路径和请求方法是否存在
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 是否存在
     */
    @Operation(summary = "判断接口路径和请求方法是否存在")
    boolean existsByPathAndMethod(String path, MockApi.HttpMethod method);

    /**
     * 判断指定项目下接口路径和请求方法是否已存在
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @param method    请求方法
     * @return 是否存在
     */
    @Operation(summary = "判断指定项目下接口路径和请求方法是否已存在")
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM MockApi a WHERE a.project.id = :projectId AND a.path = :path AND a.method = :method")
    boolean existsByProjectIdAndPathAndMethod(@Param("projectId") Long projectId,
                                              @Param("path") String path,
                                              @Param("method") MockApi.HttpMethod method);

    /**
     * 根据项目ID删除接口
     *
     * @param projectId 项目ID
     */
    @Operation(summary = "根据项目ID删除接口")
    void deleteByProjectId(Long projectId);

    /**
     * 根据接口路径删除接口
     *
     * @param path 接口路径
     */
    @Operation(summary = "根据接口路径删除接口")
    void deleteByPath(String path);

    /**
     * 根据项目ID和请求方法查询接口列表
     *
     * @param projectId 项目ID
     * @param method     请求方法
     * @return 接口列表
     */
    @Operation(summary = "根据项目ID和请求方法查询接口列表")
    List<MockApi> findByProjectIdAndMethod(Long projectId, MockApi.HttpMethod method);
}
