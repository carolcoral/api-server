/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.ResponseRequestParamDTO;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.ResponseRequestParamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 响应请求参数服务类
 * 用于管理响应匹配的请求参数
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Service
public class ResponseRequestParamService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResponseRequestParamService.class);

    /**
     * 构造器
     */
    public ResponseRequestParamService(ResponseRequestParamRepository requestParamRepository,
                                   MockResponseService mockResponseService) {
        this.requestParamRepository = requestParamRepository;
        this.mockResponseService = mockResponseService;
    }

    private final ResponseRequestParamRepository requestParamRepository;
    private final MockResponseService mockResponseService;

    /**
     * 获取响应的所有请求参数
     *
     * @param responseId 响应ID
     * @return 请求参数列表
     */
    public ApiResponse<List<ResponseRequestParamDTO>> getParamsByResponseId(Long responseId) {
        try {
            List<ResponseRequestParam> params = requestParamRepository.findAll();
            List<ResponseRequestParamDTO> dtos = params.stream()
                    .filter(param -> param.getMockResponse() != null && param.getMockResponse().getId().equals(responseId))
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            return ApiResponse.success(dtos);
        } catch (Exception e) {
            log.error("获取请求参数列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取请求参数列表失败");
        }
    }

    /**
     * 创建请求参数
     *
     * @param responseId 响应ID
     * @param dto       请求参数DTO
     * @return 创建的请求参数
     */
    @Transactional
    public ApiResponse<ResponseRequestParamDTO> createParam(Long responseId, ResponseRequestParamDTO dto) {
        try {
            // 查找响应
            MockResponse response = mockResponseService.getById(responseId);
            if (response == null) {
                return ApiResponse.error("响应不存在");
            }

            // 如果设置为默认响应，则检查该响应是否已经有请求参数
            if (response.getIsDefault() != null && response.getIsDefault()) {
                return ApiResponse.error("默认响应不能设置请求参数");
            }

            ResponseRequestParam param = new ResponseRequestParam();
            param.setParamName(dto.getParamName());
            param.setParamType(ResponseRequestParam.ParamType.valueOf(dto.getParamType()));
            param.setParamValue(dto.getParamValue());
            param.setRequired(dto.getRequired());
            param.setMockResponse(response);

            ResponseRequestParam saved = requestParamRepository.save(param);
            log.info("创建请求参数成功: {}", saved.getId());
            return ApiResponse.success(convertToDTO(saved));
        } catch (Exception e) {
            log.error("创建请求参数失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建请求参数失败");
        }
    }

    /**
     * 更新请求参数
     *
     * @param id  参数ID
     * @param dto 请求参数DTO
     * @return 更新的请求参数
     */
    @Transactional
    public ApiResponse<ResponseRequestParamDTO> updateParam(Long id, ResponseRequestParamDTO dto) {
        try {
            ResponseRequestParam param = requestParamRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("请求参数不存在"));

            // 检查是否是默认响应的参数
            if (param.getMockResponse() != null &&
                param.getMockResponse().getIsDefault() != null &&
                param.getMockResponse().getIsDefault()) {
                return ApiResponse.error("默认响应的参数不能修改");
            }

            param.setParamName(dto.getParamName());
            if (dto.getParamType() != null) {
                param.setParamType(ResponseRequestParam.ParamType.valueOf(dto.getParamType()));
            }
            param.setParamValue(dto.getParamValue());
            param.setRequired(dto.getRequired());

            ResponseRequestParam updated = requestParamRepository.save(param);
            log.info("更新请求参数成功: {}", updated.getId());
            return ApiResponse.success(convertToDTO(updated));
        } catch (Exception e) {
            log.error("更新请求参数失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新请求参数失败");
        }
    }

    /**
     * 删除请求参数
     *
     * @param id 参数ID
     * @return 删除结果
     */
    @Transactional
    public ApiResponse<Void> deleteParam(Long id) {
        try {
            ResponseRequestParam param = requestParamRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("请求参数不存在"));

            // 检查是否是默认响应的参数
            if (param.getMockResponse() != null &&
                param.getMockResponse().getIsDefault() != null &&
                param.getMockResponse().getIsDefault()) {
                return ApiResponse.error("默认响应的参数不能删除");
            }

            requestParamRepository.deleteById(id);
            log.info("删除请求参数成功: {}", id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除请求参数失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除请求参数失败");
        }
    }

    /**
     * 转换为DTO
     *
     * @param entity 实体
     * @return DTO
     */
    private ResponseRequestParamDTO convertToDTO(ResponseRequestParam entity) {
        ResponseRequestParamDTO dto = new ResponseRequestParamDTO();
        dto.setId(entity.getId());
        dto.setParamName(entity.getParamName());
        dto.setParamType(entity.getParamType().name());
        dto.setParamValue(entity.getParamValue());
        dto.setRequired(entity.getRequired());
        return dto;
    }
}
