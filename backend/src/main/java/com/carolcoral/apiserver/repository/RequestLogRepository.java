/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 请求日志数据访问接口
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /**
     * 统计指定时间范围内的请求数量
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求数量
     */
    long countByRequestTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定项目和指定时间范围内的请求数量
     *
     * @param projectId 项目ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求数量
     */
    long countByProjectIdAndRequestTimeBetween(Long projectId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定接口和指定时间范围内的请求数量
     *
     * @param mockApiId 接口ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 请求数量
     */
    long countByMockApiIdAndRequestTimeBetween(Long mockApiId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计今天的所有自定义接口请求数量
     *
     * @param startTime 今天的开始时间
     * @param endTime 今天的结束时间
     * @return 请求数量
     */
    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.requestTime BETWEEN :startTime AND :endTime")
    long countTodayRequests(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
