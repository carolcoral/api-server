/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.VerificationCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 邮箱验证码Repository
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮箱验证码Repository", description = "邮箱验证码数据访问接口")
@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /**
     * 根据邮箱、验证码和类型查找未使用且未过期的记录
     *
     * @param email 邮箱
     * @param code 验证码
     * @param type 类型
     * @return 验证码Optional
     */
    @Operation(summary = "查找有效验证码")
    Optional<VerificationCode> findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreateTimeDesc(
            String email, String code, String type);

    /**
     * 使指定邮箱的旧验证码失效
     *
     * @param email 邮箱
     * @param type 类型
     * @return 受影响的记录列表
     */
    @Operation(summary = "查找邮箱的未使用验证码")
    List<VerificationCode> findByEmailAndTypeAndUsedFalse(String email, String type);
}
