/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.entity.MockApi;
import com.carolcoral.mockserver.entity.MockResponse;
import com.carolcoral.mockserver.entity.Project;
import com.carolcoral.mockserver.repository.MockApiRepository;
import com.carolcoral.mockserver.repository.MockResponseRepository;
import com.carolcoral.mockserver.repository.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 请求录制与回放控制器
 * 管理 Mock 请求的录制、查询和回放功能
 *
 * @author carolcoral
 */
@Tag(name = "请求录制与回放", description = "录制真实请求并自动生成 Mock 配置，支持回放对比")
@RestController
@RequestMapping("/api/request-records")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:view')")
public class RequestRecordController {
    private static final Logger log = LoggerFactory.getLogger(RequestRecordController.class);

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;
    private final MockResponseRepository mockResponseRepository;

    public RequestRecordController(EntityManager entityManager, ObjectMapper objectMapper,
                                   ProjectRepository projectRepository, MockApiRepository mockApiRepository,
                                   MockResponseRepository mockResponseRepository) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.mockResponseRepository = mockResponseRepository;
    }

    /**
     * 分页查询录制日志列表
     */
    @Operation(summary = "查询录制日志列表", description = "分页查询含完整请求/响应数据的日志，支持按项目、路径、方法筛选")
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRecordLogs(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "项目ID筛选") @RequestParam(required = false) Long projectId,
            @Parameter(description = "请求路径模糊搜索") @RequestParam(required = false) String path,
            @Parameter(description = "请求方法筛选") @RequestParam(required = false) String method) {

        try {
            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM t_request_log r WHERE r.project_id IS NOT NULL");
            StringBuilder dataSql = new StringBuilder(
                    "SELECT r.id, r.mock_api_id, r.project_id, r.method, r.path, r.request_time, " +
                    "r.status_code, r.response_time, r.request_ip, r.request_headers, r.query_params, " +
                    "r.request_body, r.response_body, r.response_content_type, " +
                    "p.name as project_name, a.name as api_name " +
                    "FROM t_request_log r " +
                    "LEFT JOIN t_project p ON r.project_id = p.id " +
                    "LEFT JOIN t_mock_api a ON r.mock_api_id = a.id " +
                    "WHERE r.project_id IS NOT NULL");

            Map<String, Object> params = new HashMap<>();

            if (projectId != null) {
                countSql.append(" AND r.project_id = :projectId");
                dataSql.append(" AND r.project_id = :projectId");
                params.put("projectId", projectId);
            }
            if (path != null && !path.isEmpty()) {
                countSql.append(" AND r.path LIKE :path");
                dataSql.append(" AND r.path LIKE :path");
                params.put("path", "%" + path + "%");
            }
            if (method != null && !method.isEmpty()) {
                countSql.append(" AND r.method = :method");
                dataSql.append(" AND r.method = :method");
                params.put("method", method.toUpperCase());
            }

            dataSql.append(" ORDER BY r.id DESC");

            Query countQuery = entityManager.createNativeQuery(countSql.toString());
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }
            long total = ((Number) countQuery.getSingleResult()).longValue();

            Query dataQuery = entityManager.createNativeQuery(dataSql.toString());
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                dataQuery.setParameter(entry.getKey(), entry.getValue());
            }
            dataQuery.setFirstResult((page - 1) * size);
            dataQuery.setMaxResults(size);

            List<Object[]> rows = dataQuery.getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row[0] != null ? ((Number) row[0]).longValue() : null);
                item.put("mockApiId", row[1] != null ? ((Number) row[1]).longValue() : null);
                item.put("projectId", row[2] != null ? ((Number) row[2]).longValue() : null);
                item.put("method", row[3]);
                item.put("path", row[4]);
                item.put("requestTime", row[5] != null ? String.valueOf(row[5]) : null);
                item.put("statusCode", row[6]);
                item.put("responseTime", row[7] != null ? ((Number) row[7]).longValue() : null);
                item.put("requestIp", row[8]);
                item.put("requestHeaders", parseJsonSafely(row[9]));
                item.put("queryParams", parseJsonSafely(row[10]));
                item.put("requestBody", parseJsonSafely(row[11]));
                item.put("responseBody", parseJsonSafely(row[12]));
                item.put("responseContentType", row[13]);
                item.put("projectName", row[14]);
                item.put("apiName", row[15]);
                list.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("list", list);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询录制日志列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询录制日志列表失败");
        }
    }

    /**
     * 回放单条录制记录：根据录制数据自动创建 Mock API 和响应
     */
    @Operation(summary = "回放录制请求", description = "根据录制的请求/响应数据自动创建 Mock API 和默认响应")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:replay')")
    @PostMapping("/{id}/replay")
    @Transactional
    public ApiResponse<Map<String, Object>> replayRecord(
            @Parameter(description = "录制日志ID") @PathVariable Long id,
            @Parameter(description = "目标项目ID") @RequestParam Long targetProjectId) {

        try {
            // 查询录制日志
            String sql = "SELECT r.method, r.path, r.request_body, r.response_body, r.status_code, " +
                    "r.response_content_type, r.request_headers, r.query_params " +
                    "FROM t_request_log r WHERE r.id = :id";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("id", id);
            Object[] row = (Object[]) query.getSingleResult();

            if (row == null) {
                return ApiResponse.error("录制记录不存在");
            }

            String method = (String) row[0];
            String path = (String) row[1];
            String requestBody = (String) row[2];
            String responseBody = (String) row[3];
            Integer statusCode = row[4] != null ? ((Number) row[4]).intValue() : 200;
            String contentType = (String) row[5];

            // 查找目标项目
            Optional<Project> projectOpt = projectRepository.findById(targetProjectId);
            if (!projectOpt.isPresent()) {
                return ApiResponse.error("目标项目不存在");
            }
            Project project = projectOpt.get();

            // 检查是否已存在相同 path+method 的接口
            Optional<MockApi> existingApi = mockApiRepository.findByProjectIdAndPathAndMethod(
                    targetProjectId, path, MockApi.HttpMethod.valueOf(method.toUpperCase()));
            if (existingApi.isPresent()) {
                // 已存在则追加响应
                MockApi api = existingApi.get();
                MockResponse newResponse = new MockResponse();
                newResponse.setMockApi(api);
                newResponse.setStatusCode(statusCode != null ? statusCode : 200);
                newResponse.setContentType(contentType != null ? contentType : "application/json");
                newResponse.setResponseBody(responseBody);
                newResponse.setEnabled(true);
                newResponse.setActive(true);
                newResponse.setIsDefault(false);
                newResponse.setWeight(1);
                newResponse.setConditionDesc("来自录制回放 - 请求体: " +
                        (requestBody != null && requestBody.length() > 50
                                ? requestBody.substring(0, 50) + "..." : requestBody));
                mockResponseRepository.save(newResponse);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("action", "append");
                result.put("apiId", api.getId());
                result.put("apiName", api.getName());
                result.put("responseId", newResponse.getId());
                return ApiResponse.success(result);
            }

            // 创建新接口
            MockApi newApi = new MockApi();
            newApi.setProject(project);
            newApi.setName(path);
            newApi.setPath(path);
            newApi.setMethod(MockApi.HttpMethod.valueOf(method.toUpperCase()));
            newApi.setDescription("来自录制回放（" + method + " " + path + "）");
            newApi.setEnabled(true);
            newApi.setEnableRandom(false);
            newApi.setCreateUserId(project.getCreateUserId());
            mockApiRepository.save(newApi);

            // 创建默认响应
            MockResponse newResponse = new MockResponse();
            newResponse.setMockApi(newApi);
            newResponse.setStatusCode(statusCode != null ? statusCode : 200);
            newResponse.setContentType(contentType != null ? contentType : "application/json");
            newResponse.setResponseBody(responseBody);
            newResponse.setEnabled(true);
            newResponse.setActive(true);
            newResponse.setIsDefault(true);
            newResponse.setWeight(1);
            newResponse.setConditionDesc("来自录制回放");
            mockResponseRepository.save(newResponse);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "create");
            result.put("apiId", newApi.getId());
            result.put("apiName", newApi.getName());
            result.put("responseId", newResponse.getId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("回放录制请求失败: {}", e.getMessage(), e);
            return ApiResponse.error("回放录制请求失败: " + e.getMessage());
        }
    }

    /**
     * 批量回放：将选中录制记录批量生成 Mock 接口
     */
    @Operation(summary = "批量回放", description = "批量回放多条录制记录到目标项目")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:replay')")
    @PostMapping("/batch-replay")
    @Transactional
    public ApiResponse<Map<String, Object>> batchReplay(
            @Parameter(description = "录制日志ID列表") @RequestBody List<Long> ids,
            @Parameter(description = "目标项目ID") @RequestParam Long targetProjectId) {

        int success = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        for (Long id : ids) {
            try {
                ApiResponse<Map<String, Object>> result = replayRecord(id, targetProjectId);
                if (result.getCode() == 200) {
                    success++;
                } else {
                    skip++;
                    errors.add("ID " + id + ": " + result.getMessage());
                }
            } catch (Exception e) {
                skip++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("skip", skip);
        result.put("errors", errors);
        return ApiResponse.success(result);
    }

    /**
     * 查看录制请求详情
     */
    @Operation(summary = "查看录制详情", description = "查看单条录制记录的完整请求和响应数据")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getRecordDetail(@Parameter(description = "录制日志ID") @PathVariable Long id) {
        try {
            String sql = "SELECT r.id, r.method, r.path, r.request_time, r.status_code, r.response_time, " +
                    "r.request_ip, r.request_headers, r.query_params, r.request_body, r.response_body, " +
                    "r.response_content_type, p.name as project_name, a.name as api_name, r.project_id " +
                    "FROM t_request_log r " +
                    "LEFT JOIN t_project p ON r.project_id = p.id " +
                    "LEFT JOIN t_mock_api a ON r.mock_api_id = a.id " +
                    "WHERE r.id = :id";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("id", id);
            Object[] row = (Object[]) query.getSingleResult();

            if (row == null) {
                return ApiResponse.error("录制记录不存在");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0] != null ? ((Number) row[0]).longValue() : null);
            item.put("method", row[1]);
            item.put("path", row[2]);
            item.put("requestTime", row[3] != null ? String.valueOf(row[3]) : null);
            item.put("statusCode", row[4]);
            item.put("responseTime", row[5] != null ? ((Number) row[5]).longValue() : null);
            item.put("requestIp", row[6]);
            item.put("requestHeaders", parseJsonSafely(row[7]));
            item.put("queryParams", parseJsonSafely(row[8]));
            item.put("requestBody", parseJsonSafely(row[9]));
            item.put("responseBody", parseJsonSafely(row[10]));
            item.put("responseContentType", row[11]);
            item.put("projectName", row[12]);
            item.put("apiName", row[13]);
            item.put("projectId", row[14] != null ? ((Number) row[14]).longValue() : null);

            return ApiResponse.success(item);
        } catch (Exception e) {
            log.error("查看录制详情失败: {}", e.getMessage(), e);
            return ApiResponse.error("查看录制详情失败");
        }
    }

    private Object parseJsonSafely(Object raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw.toString(), Object.class);
        } catch (Exception e) {
            return raw.toString();
        }
    }
}
