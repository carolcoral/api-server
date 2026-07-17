/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 订阅 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiSubscriptionRepository extends JpaRepository<AiSubscription, Long> {

    List<AiSubscription> findByUserIdAndStatusTrue(Long userId);

    List<AiSubscription> findByUserIdAndStatusTrueAndFallbackEnabledTrue(Long userId);

    List<AiSubscription> findByModelId(Long modelId);

    long countByModelIdAndStatusTrue(Long modelId);

    boolean existsByUserIdAndModelId(Long userId, Long modelId);

    List<AiSubscription> findByStatusTrue();
}
