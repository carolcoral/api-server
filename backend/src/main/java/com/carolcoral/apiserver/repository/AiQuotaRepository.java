/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 额度 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiQuotaRepository extends JpaRepository<AiQuota, Long> {

    List<AiQuota> findByUserIdAndStatusTrue(Long userId);

    List<AiQuota> findBySubscriptionIdAndStatusTrue(Long subscriptionId);
}
