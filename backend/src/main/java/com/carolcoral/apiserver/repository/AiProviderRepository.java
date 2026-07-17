/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 服务商 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    Optional<AiProvider> findByCode(String code);

    List<AiProvider> findByStatusTrue();
}
