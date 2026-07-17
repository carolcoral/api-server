/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiCallLog;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 调用日志 Repository
 *
 * @author carolcoral
 * @since 2026-06-25
 */
@Tag(name = "AI调用日志", description = "AI调用日志数据访问接口")
@Repository
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    /**
     * 按用户和时间范围统计调用次数（用于按日/月/年统计）
     */
    @Query(value = "SELECT u.username, COUNT(*) as cnt " +
            "FROM t_ai_call_log a JOIN t_user u ON a.user_id = u.id " +
            "WHERE a.call_time >= :startTime AND a.call_time < :endTime " +
            "GROUP BY u.username ORDER BY cnt DESC",
            nativeQuery = true)
    List<Object[]> countByUserAndTimeRange(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按天统计某用户的 AI 调用次数
     */
    @Query(value = "SELECT DATE(a.call_time) as dt, COUNT(*) as cnt " +
            "FROM t_ai_call_log a WHERE a.user_id = :userId " +
            "AND a.call_time >= :startTime AND a.call_time < :endTime " +
            "GROUP BY dt ORDER BY dt ASC",
            nativeQuery = true)
    List<Object[]> countByUserPerDay(@Param("userId") Long userId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内的总调用次数
     */
    @Query(value = "SELECT COUNT(*) FROM t_ai_call_log a WHERE a.call_time >= :startTime AND a.call_time < :endTime",
            nativeQuery = true)
    long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);
}
