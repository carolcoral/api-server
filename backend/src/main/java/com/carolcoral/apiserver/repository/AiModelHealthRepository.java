/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiModelHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 模型健康检查记录 Repository
 *
 * @author carolcoral
 */
@Repository
public interface AiModelHealthRepository extends JpaRepository<AiModelHealth, Long> {

    List<AiModelHealth> findByModelIdOrderByCheckTimeDesc(Long modelId);
}
