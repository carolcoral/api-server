/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 接口响应Repository
 *
 * @author carolcoral
 */
@Tag(name = "接口响应管理", description = "接口响应数据访问接口")
@Repository
public interface MockResponseRepository extends JpaRepository<MockResponse, Long> {

    /**
     * 根据接口ID查询响应列表
     *
     * @param apiId 接口ID
     * @return 响应列表
     */
    @Operation(summary = "根据接口ID查询响应列表")
    List<MockResponse> findByMockApiId(Long apiId);

    /**
     * 根据接口查询响应列表
     *
     * @param mockApi 接口
     * @return 响应列表
     */
    @Operation(summary = "根据接口查询响应列表")
    List<MockResponse> findByMockApi(MockApi mockApi);

    /**
     * 根据接口ID和状态码查询响应
     *
     * @param apiId      接口ID
     * @param statusCode 状态码
     * @return 响应Optional
     */
    @Operation(summary = "根据接口ID和状态码查询响应")
    List<MockResponse> findByMockApiIdAndStatusCode(Long apiId, Integer statusCode);

    /**
     * 根据接口ID和启用状态查询响应列表
     *
     * @param apiId   接口ID
     * @param enabled 是否启用
     * @return 响应列表
     */
    @Operation(summary = "根据接口ID和启用状态查询响应列表")
    List<MockResponse> findByMockApiIdAndEnabled(Long apiId, Boolean enabled);

    /**
     * 根据状态码查询响应列表
     *
     * @param statusCode 状态码
     * @return 响应列表
     */
    @Operation(summary = "根据状态码查询响应列表")
    List<MockResponse> findByStatusCode(Integer statusCode);

    /**
     * 查询权重大于0的响应列表
     *
     * @param apiId 接口ID
     * @return 响应列表
     */
    @Operation(summary = "查询权重大于0的响应列表")
    @Query("SELECT r FROM MockResponse r WHERE r.mockApi.id = :apiId AND r.weight > 0 AND r.enabled = true")
    List<MockResponse> findWeightedResponsesByApiId(@Param("apiId") Long apiId);

    /**
     * 根据接口ID删除响应
     *
     * @param apiId 接口ID
     */
    @Operation(summary = "根据接口ID删除响应")
    void deleteByMockApiId(Long apiId);

    /**
     * 根据接口删除响应
     *
     * @param mockApi 接口
     */
    @Operation(summary = "根据接口删除响应")
    void deleteByMockApi(MockApi mockApi);

    /**
     * 根据状态码删除响应
     *
     * @param statusCode 状态码
     */
    @Operation(summary = "根据状态码删除响应")
    void deleteByStatusCode(Integer statusCode);

    /**
     * 统计接口的响应数量
     *
     * @param apiId 接口ID
     * @return 响应数量
     */
    @Operation(summary = "统计接口的响应数量")
    Long countByMockApiId(Long apiId);
}
