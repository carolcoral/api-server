/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 模型 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    List<AiModel> findByProviderId(Long providerId);

    List<AiModel> findByProviderIdAndStatusTrue(Long providerId);

    Optional<AiModel> findByProviderIdAndModelName(Long providerId, String modelName);

    List<AiModel> findByHealthStatusNotAndStatusTrue(String healthStatus);

    List<AiModel> findByStatusTrue();

    @org.springframework.data.jpa.repository.Query("SELECT m FROM AiModel m JOIN FETCH m.provider WHERE m.status = true")
    List<AiModel> findByStatusTrueWithProvider();

    long countByStatusTrue();
}
