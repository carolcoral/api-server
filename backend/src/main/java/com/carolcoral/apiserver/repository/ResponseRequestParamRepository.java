/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.ResponseRequestParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 响应请求参数Repository接口
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Repository
public interface ResponseRequestParamRepository extends JpaRepository<ResponseRequestParam, Long> {

    /**
     * 根据响应ID查询请求参数列表
     *
     * @param responseId 响应ID
     * @return 请求参数列表
     */
    List<ResponseRequestParam> findByMockResponseId(Long responseId);

    /**
     * 根据响应ID删除请求参数
     *
     * @param responseId 响应ID
     */
    void deleteByMockResponseId(Long responseId);
}
