/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ResponseRequestParamDTO;
import com.carolcoral.apiserver.service.ResponseRequestParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 响应请求参数Controller
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Tag(name = "响应请求参数管理", description = "响应请求参数管理接口")
@RestController
@RequestMapping("/api/responses/{responseId}/params")
public class ResponseRequestParamController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResponseRequestParamController.class);

    /**
     * 构造器
     */
    @Autowired
    public ResponseRequestParamController(ResponseRequestParamService responseRequestParamService) {
        this.responseRequestParamService = responseRequestParamService;
    }

    private final ResponseRequestParamService responseRequestParamService;

    /**
     * 获取响应的所有请求参数
     *
     * @param responseId 响应ID
     * @return 请求参数列表
     */
    @GetMapping
    @Operation(summary = "获取响应的所有请求参数")
    public ApiResponse<List<ResponseRequestParamDTO>> getParamsByResponseId(
            @Parameter(description = "响应ID", example = "1") @PathVariable Long responseId) {
        return responseRequestParamService.getParamsByResponseId(responseId);
    }

    /**
     * 创建请求参数
     *
     * @param responseId 响应ID
     * @param dto       请求参数DTO
     * @return 创建的请求参数
     */
    @PostMapping
    @Operation(summary = "创建请求参数")
    public ApiResponse<ResponseRequestParamDTO> createParam(
            @Parameter(description = "响应ID", example = "1") @PathVariable Long responseId,
            @RequestBody ResponseRequestParamDTO dto) {
        return responseRequestParamService.createParam(responseId, dto);
    }

    /**
     * 更新请求参数
     *
     * @param id  参数ID
     * @param dto 请求参数DTO
     * @return 更新的请求参数
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新请求参数")
    public ApiResponse<ResponseRequestParamDTO> updateParam(
            @Parameter(description = "参数ID", example = "1") @PathVariable Long id,
            @RequestBody ResponseRequestParamDTO dto) {
        return responseRequestParamService.updateParam(id, dto);
    }

    /**
     * 删除请求参数
     *
     * @param id 参数ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除请求参数")
    public ApiResponse<Void> deleteParam(
            @Parameter(description = "参数ID", example = "1") @PathVariable Long id) {
        return responseRequestParamService.deleteParam(id);
    }
}
