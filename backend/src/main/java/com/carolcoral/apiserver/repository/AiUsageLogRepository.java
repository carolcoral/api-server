/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * AI 调用日志 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    Page<AiUsageLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<AiUsageLog> findByUserIdAndModelIdOrderByCreateTimeDesc(Long userId, Long modelId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiUsageLog l WHERE l.user.id = :userId AND l.createTime BETWEEN :start AND :end")
    Long sumTokensByUserIdAndTimeBetween(@Param("userId") Long userId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM AiUsageLog l WHERE l.model.id = :modelId AND l.createTime BETWEEN :start AND :end")
    Long sumTokensByModelIdAndTimeBetween(@Param("modelId") Long modelId,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    long countByUserId(Long userId);

    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);
}
