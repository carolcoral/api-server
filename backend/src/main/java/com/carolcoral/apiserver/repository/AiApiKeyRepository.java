/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI API Key Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiApiKeyRepository extends JpaRepository<AiApiKey, Long> {

    Optional<AiApiKey> findByApiKey(String apiKey);

    List<AiApiKey> findByUserIdAndStatusTrue(Long userId);

    boolean existsByUserIdAndApiKey(Long userId, String apiKey);
}
