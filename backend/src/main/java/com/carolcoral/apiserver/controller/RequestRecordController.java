/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.repository.ResponseRequestParamRepository;
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

import java.util.*;

/**
 * 请求录制与回放控制器
 * 管理录制回放页面代理请求的查询和回放功能
 * 数据来源于 t_proxy_record 表，与 Mock 调用日志 t_request_log 独立
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
    private final ResponseRequestParamRepository responseRequestParamRepository;

    public RequestRecordController(EntityManager entityManager, ObjectMapper objectMapper,
                                   ProjectRepository projectRepository, MockApiRepository mockApiRepository,
                                   MockResponseRepository mockResponseRepository,
                                   ResponseRequestParamRepository responseRequestParamRepository) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.mockResponseRepository = mockResponseRepository;
        this.responseRequestParamRepository = responseRequestParamRepository;
    }

    /**
     * 分页查询录制日志列表（仅查询代理发起的请求）
     */
    @Operation(summary = "查询录制日志列表", description = "分页查询 t_proxy_record 表，支持按路径、方法筛选")
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRecordLogs(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "请求路径模糊搜索") @RequestParam(required = false) String path,
            @Parameter(description = "请求方法筛选") @RequestParam(required = false) String method) {

        try {
            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM t_proxy_record r WHERE 1=1");
            StringBuilder dataSql = new StringBuilder(
                    "SELECT r.id, r.method, r.path, r.request_time, " +
                    "r.status_code, r.response_time, r.request_ip, r.request_headers, r.query_params, " +
                    "r.request_body, r.response_body, r.response_content_type " +
                    "FROM t_proxy_record r " +
                    "WHERE 1=1");

            Map<String, Object> params = new HashMap<>();

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
     * 回放保存录制记录：根据录制数据自动创建 Mock API 和响应
     */
    @Operation(summary = "回放保存录制请求", description = "根据录制的请求/响应数据自动创建 Mock API 和默认响应，支持自定义名称和路径")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:replay')")
    @PostMapping("/{id}/replay")
    @Transactional
    public ApiResponse<Map<String, Object>> replayRecord(
            @Parameter(description = "录制日志ID") @PathVariable Long id,
            @Parameter(description = "回放保存请求") @RequestBody Map<String, Object> replayRequest) {

        try {
            Long targetProjectId = replayRequest.get("targetProjectId") != null
                    ? ((Number) replayRequest.get("targetProjectId")).longValue() : null;
            String apiName = (String) replayRequest.getOrDefault("apiName", "");
            String apiPath = (String) replayRequest.getOrDefault("apiPath", "");
            String description = (String) replayRequest.getOrDefault("description", "");

            if (targetProjectId == null) {
                return ApiResponse.error("请选择目标项目");
            }

            // 查询录制日志
            String sql = "SELECT r.method, r.path, r.request_body, r.response_body, r.status_code, " +
                    "r.response_content_type, r.request_headers, r.query_params " +
                    "FROM t_proxy_record r WHERE r.id = :id";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("id", id);
            Object[] row = (Object[]) query.getSingleResult();

            if (row == null) {
                return ApiResponse.error("录制记录不存在");
            }

            String method = (String) row[0];
            String originalPath = (String) row[1];
            String requestBody = (String) row[2];
            String responseBody = (String) row[3];
            Integer statusCode = row[4] != null ? ((Number) row[4]).intValue() : 200;
            String contentType = (String) row[5];

            // 自动提取路径：去掉协议和域名端口部分
            if (apiPath == null || apiPath.trim().isEmpty()) {
                apiPath = extractPath(originalPath);
            }
            // 确保 apiPath 不包含查询参数（查询参数单独保存为 ResponseRequestParam）
            {
                int qIdx = apiPath.indexOf('?');
                if (qIdx >= 0) {
                    apiPath = apiPath.substring(0, qIdx);
                }
            }
            if (apiName == null || apiName.trim().isEmpty()) {
                apiName = apiPath;
            }

            // 从原始URL中提取查询参数
            Map<String, String> queryParams = extractQueryParams(originalPath);

            // 查找目标项目
            Optional<Project> projectOpt = projectRepository.findById(targetProjectId);
            if (!projectOpt.isPresent()) {
                return ApiResponse.error("目标项目不存在");
            }
            Project project = projectOpt.get();

            // 检查是否已存在相同 path+method 的接口
            Optional<MockApi> existingApi = mockApiRepository.findByProjectIdAndPathAndMethod(
                    targetProjectId, apiPath, MockApi.HttpMethod.valueOf(method.toUpperCase()));
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

                // 保存查询参数
                saveQueryParams(newResponse, queryParams);

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
            newApi.setName(apiName);
            newApi.setPath(apiPath);
            newApi.setMethod(MockApi.HttpMethod.valueOf(method.toUpperCase()));
            newApi.setDescription(description != null && !description.isEmpty()
                    ? description : "来自录制回放（" + method + " " + originalPath + "）");
            newApi.setEnabled(true);
            newApi.setEnableRandom(false);
            newApi.setCreateUserId(project.getCreateUserId());
            mockApiRepository.save(newApi);

            // 创建响应：有查询参数时只创建带条件匹配的响应，否则创建默认响应
            MockResponse savedResponse;
            if (!queryParams.isEmpty()) {
                // 有查询参数：只创建带条件匹配的响应
                MockResponse conditionalResponse = new MockResponse();
                conditionalResponse.setMockApi(newApi);
                conditionalResponse.setStatusCode(statusCode != null ? statusCode : 200);
                conditionalResponse.setContentType(contentType != null ? contentType : "application/json");
                conditionalResponse.setResponseBody(responseBody);
                conditionalResponse.setEnabled(true);
                conditionalResponse.setActive(true);
                conditionalResponse.setIsDefault(false);
                conditionalResponse.setWeight(1);
                conditionalResponse.setConditionDesc("录制回放 - 匹配查询参数: " + queryParams.keySet());
                mockResponseRepository.save(conditionalResponse);
                saveQueryParams(conditionalResponse, queryParams);
                savedResponse = conditionalResponse;
            } else {
                // 无查询参数：创建默认响应
                MockResponse defaultResponse = new MockResponse();
                defaultResponse.setMockApi(newApi);
                defaultResponse.setStatusCode(statusCode != null ? statusCode : 200);
                defaultResponse.setContentType(contentType != null ? contentType : "application/json");
                defaultResponse.setResponseBody(responseBody);
                defaultResponse.setEnabled(true);
                defaultResponse.setActive(true);
                defaultResponse.setIsDefault(true);
                defaultResponse.setWeight(1);
                defaultResponse.setConditionDesc("来自录制回放");
                mockResponseRepository.save(defaultResponse);
                savedResponse = defaultResponse;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "create");
            result.put("apiId", newApi.getId());
            result.put("apiName", newApi.getName());
            result.put("responseId", savedResponse.getId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("回放录制请求失败: {}", e.getMessage(), e);
            return ApiResponse.error("回放录制请求失败: " + e.getMessage());
        }
    }

    /**
     * 从完整URL中提取路径部分（去掉协议、域名端口和查询参数）
     */
    private String extractPath(String url) {
        if (url == null) return "/";
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            return path != null && !path.isEmpty() ? path : "/";
        } catch (Exception e) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                String withoutProtocol = url.replaceFirst("https?://", "");
                int slashIdx = withoutProtocol.indexOf('/');
                if (slashIdx >= 0) {
                    String pathAndQuery = withoutProtocol.substring(slashIdx);
                    int qIdx = pathAndQuery.indexOf('?');
                    return qIdx >= 0 ? pathAndQuery.substring(0, qIdx) : pathAndQuery;
                }
                return "/";
            }
            // 已经是路径格式
            int qIdx = url.indexOf('?');
            String cleanPath = qIdx >= 0 ? url.substring(0, qIdx) : url;
            return cleanPath.startsWith("/") ? cleanPath : "/" + cleanPath;
        }
    }

    /**
     * 从URL中提取查询参数
     */
    private Map<String, String> extractQueryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        if (url == null) return params;
        try {
            java.net.URI uri = new java.net.URI(url);
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf('=');
                    if (eqIdx > 0) {
                        String key = java.net.URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
                        String value = eqIdx < pair.length() - 1
                                ? java.net.URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8") : "";
                        params.put(key, value);
                    }
                }
            }
            return params;
        } catch (Exception e) {
            // 回退到手动解析
            int qIdx = url.indexOf('?');
            if (qIdx >= 0 && qIdx < url.length() - 1) {
                String query = url.substring(qIdx + 1);
                // 去掉可能的 fragment
                int hashIdx = query.indexOf('#');
                if (hashIdx >= 0) query = query.substring(0, hashIdx);
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf('=');
                    if (eqIdx > 0) {
                        try {
                            String key = java.net.URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
                            String value = eqIdx < pair.length() - 1
                                    ? java.net.URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8") : "";
                            params.put(key, value);
                        } catch (Exception ignored) {}
                    }
                }
            }
            return params;
        }
    }

    /**
     * 将查询参数保存为 ResponseRequestParam
     */
    private void saveQueryParams(MockResponse mockResponse, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            ResponseRequestParam param = new ResponseRequestParam();
            param.setMockResponse(mockResponse);
            param.setParamName(entry.getKey());
            param.setParamType(ResponseRequestParam.ParamType.QUERY);
            param.setParamValue(entry.getValue());
            param.setRequired(true);
            responseRequestParamRepository.save(param);
        }
        log.info("已保存 {} 个查询参数到响应 ID={}", queryParams.size(), mockResponse.getId());
    }

    /**
     * 批量回放：将选中录制记录批量生成 Mock 接口
     */
    @Operation(summary = "批量回放", description = "批量回放多条录制记录到目标项目")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('record-replay:replay')")
    @PostMapping("/batch-replay")
    @Transactional
    public ApiResponse<Map<String, Object>> batchReplay(
            @Parameter(description = "回放请求列表") @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Integer> idList = (List<Integer>) body.get("ids");
        Long targetProjectId = body.get("targetProjectId") != null
                ? ((Number) body.get("targetProjectId")).longValue() : null;

        if (idList == null || idList.isEmpty()) {
            return ApiResponse.error("请选择要回放的记录");
        }
        if (targetProjectId == null) {
            return ApiResponse.error("请选择目标项目");
        }

        int success = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        for (Integer id : idList) {
            try {
                Map<String, Object> replayBody = new LinkedHashMap<>();
                replayBody.put("targetProjectId", targetProjectId);
                replayBody.put("apiName", "");
                replayBody.put("apiPath", "");
                replayBody.put("description", "");
                ApiResponse<Map<String, Object>> result = replayRecord(id.longValue(), replayBody);
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
                    "r.response_content_type " +
                    "FROM t_proxy_record r " +
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
