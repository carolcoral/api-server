/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.dto.MockRequest;
import com.carolcoral.mockserver.dto.MockResponseDTO;
import com.carolcoral.mockserver.entity.MockApi;
import com.carolcoral.mockserver.entity.MockResponse;
import com.carolcoral.mockserver.entity.Project;
import com.carolcoral.mockserver.entity.ResponseRequestParam;
import com.carolcoral.mockserver.plugin.TransformerRegistry;
import com.carolcoral.mockserver.repository.MockApiRepository;
import com.carolcoral.mockserver.util.CacheUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock服务类 - 核心服务
 *
 * @author carolcoral
 */
@Tag(name = "Mock服务", description = "Mock请求处理核心服务")
@Service
public class MockService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockService.class);

    /**
     * 构造器
     */
    public MockService(CacheUtil cacheUtil, ObjectMapper objectMapper, RequestLogService requestLogService,
                      ResponseRequestParamService responseRequestParamService, MockApiRepository mockApiRepository,
                      TransformerRegistry transformerRegistry, SystemConfigService systemConfigService,
                      MockTemplateEngine mockTemplateEngine,
                      MockMetricsService mockMetricsService) {
        this.cacheUtil = cacheUtil;
        this.objectMapper = objectMapper;
        this.requestLogService = requestLogService;
        this.responseRequestParamService = responseRequestParamService;
        this.mockApiRepository = mockApiRepository;
        this.transformerRegistry = transformerRegistry;
        this.systemConfigService = systemConfigService;
        this.mockTemplateEngine = mockTemplateEngine;
        this.mockMetricsService = mockMetricsService;
    }

    private final CacheUtil cacheUtil;
    private final ObjectMapper objectMapper;
    private final RequestLogService requestLogService;
    private final ResponseRequestParamService responseRequestParamService;
    private final MockApiRepository mockApiRepository;
    private final MockMetricsService mockMetricsService;
    private final TransformerRegistry transformerRegistry;
    private final SystemConfigService systemConfigService;
    private final MockTemplateEngine mockTemplateEngine;

    /** 自定义接口响应缓存 key=apiId, value=缓存条目 */
    private final java.util.concurrent.ConcurrentHashMap<Long, CachedCustomResponse> customResponseCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 自定义响应缓存条目
     */
    private static class CachedCustomResponse {
        final MockResponseDTO response;
        final long expireAt; // 过期时间戳（毫秒）

        CachedCustomResponse(MockResponseDTO response, long expireAt) {
            this.response = response;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 处理Mock请求
     *
     * @param mockRequest Mock请求
     * @return Mock响应
     */
    @Operation(summary = "处理Mock请求")
    public MockResponseDTO handleMockRequest(@Parameter(description = "Mock请求") MockRequest mockRequest) {
        return handleMockRequest(mockRequest, null);
    }

    /**
     * 处理Mock请求（带请求日志）
     *
     * @param mockRequest Mock请求
     * @param request HTTP请求对象（可选，用于记录日志）
     * @return Mock响应
     */
    @Operation(summary = "处理Mock请求（带请求日志）")
    public MockResponseDTO handleMockRequest(@Parameter(description = "Mock请求") MockRequest mockRequest, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        MockApi matchedApi = null;
        Integer statusCode = 200;
        try {
            log.info("处理Mock请求: {} {} 项目: {}", mockRequest.getMethod(), mockRequest.getPath(), mockRequest.getProjectCode());

            // 查找项目
            Optional<Project> projectOpt = cacheUtil.getProjectFromCache(mockRequest.getProjectCode());
            if (!projectOpt.isPresent()) {
                statusCode = 404;
                return createErrorResponse(404, "项目不存在: " + mockRequest.getProjectCode());
            }

            Project project = projectOpt.get();
            if (!project.getEnabled()) {
                statusCode = 503;
                return createErrorResponse(503, "项目已禁用");
            }

            // 查找接口
            Optional<MockApi> apiOpt = findMockApi(project.getId(), mockRequest.getPath(), mockRequest.getMethod());
            if (!apiOpt.isPresent()) {
                statusCode = 404;
                return createErrorResponse(404, "接口不存在: " + mockRequest.getPath());
            }

            MockApi mockApi = apiOpt.get();
            matchedApi = mockApi;

            // 根据接口模板路径提取 RESTful 路径参数
            Map<String, String> pathParams = extractPathParams(mockRequest.getPath(), mockApi.getPath());
            mockRequest.setPathParams(pathParams);
            log.info("提取路径参数: 路径={}, 模板={}, 参数={}", mockRequest.getPath(), mockApi.getPath(), pathParams);

            if (!mockApi.getEnabled()) {
                statusCode = 503;
                return createErrorResponse(503, "接口已禁用");
            }

            // 获取接口响应列表
            List<MockResponse> responses = cacheUtil.getApiResponsesFromCache(mockApi.getId());
            if (responses == null || responses.isEmpty()) {
                statusCode = 500;
                return createErrorResponse(500, "接口未配置响应");
            }

            // 过滤启用状态的响应
            List<MockResponse> enabledResponses = new ArrayList<>();
            for (MockResponse response : responses) {
                if (response.getEnabled() != null && response.getEnabled()) {
                    enabledResponses.add(response);
                }
            }

            if (enabledResponses.isEmpty()) {
                statusCode = 500;
                return createErrorResponse(500, "接口未配置启用状态的响应");
            }

            // 根据条件匹配响应（优先级最高）
            MockResponseDTO responseDTO = null;
            MockResponse matchedResponse = matchResponseByCondition(enabledResponses, mockRequest);
            if (matchedResponse != null) {
                responseDTO = buildMockResponse(mockApi, matchedResponse);
            }

            // 如果启用了随机返回，从所有启用的响应中按权重随机选择
            if (responseDTO == null && mockApi.getEnableRandom() != null && mockApi.getEnableRandom()) {
                List<MockResponse> randomCandidates = new ArrayList<>(enabledResponses);
                if (!randomCandidates.isEmpty()) {
                    MockResponse randomResponse = selectRandomResponse(randomCandidates);
                    if (randomResponse != null) {
                        log.info("启用随机返回，从{}个候选响应中按权重随机选择: 状态码={}",
                                randomCandidates.size(), randomResponse.getStatusCode());
                        responseDTO = buildMockResponse(mockApi, randomResponse);
                    }
                }
            }

            // 优先返回默认响应（未启用随机返回时作为兜底）
            if (responseDTO == null) {
                MockResponse defaultResponse = enabledResponses.stream()
                        .filter(r -> r.getIsDefault() != null && r.getIsDefault())
                        .findFirst()
                        .orElse(null);
                if (defaultResponse != null) {
                    log.info("返回默认响应: 状态码={}", defaultResponse.getStatusCode());
                    responseDTO = buildMockResponse(mockApi, defaultResponse);
                }
            }

            // 返回激活的响应
            if (responseDTO == null) {
                MockResponse activeResponse = enabledResponses.stream()
                        .filter(r -> r.getActive() != null && r.getActive())
                        .findFirst()
                        .orElse(null);
                if (activeResponse != null) {
                    log.info("返回激活响应: 状态码={}", activeResponse.getStatusCode());
                    responseDTO = buildMockResponse(mockApi, activeResponse);
                }
            }

            // 最后返回第一个响应（兜底）
            if (responseDTO == null) {
                MockResponse fallbackResponse = enabledResponses.get(0);
                responseDTO = buildMockResponse(mockApi, fallbackResponse);
            }

            // ========== 自定义响应转换（插件化处理） ==========
            // 在基础响应流程完成后，执行自定义转换器
            // 此步骤不会干扰原有的响应匹配、延迟、随机返回等功能
            // 优先级：动态源码编译 > Spring Bean类名引用
            if (mockApi.getCustomResponseSource() != null && !mockApi.getCustomResponseSource().trim().isEmpty()) {
                // 获取缓存配置
                int cacheSeconds = getCustomResponseCacheSeconds();
                if (cacheSeconds > 0) {
                    // 检查缓存
                    CachedCustomResponse cached = customResponseCache.get(mockApi.getId());
                    if (cached != null && !cached.isExpired()) {
                        log.info("使用缓存自定义响应: apiId={}, 剩余有效时间={}ms",
                                mockApi.getId(), cached.expireAt - System.currentTimeMillis());
                        statusCode = cached.response.getStatusCode();
                        return cached.response;
                    }
                }

                // 使用动态编译模式：编译页面提交的Java源码并执行
                log.info("使用动态源码编译模式: apiId={}", mockApi.getId());
                responseDTO = transformerRegistry.transformWithSource(
                        mockApi.getId(),
                        mockApi.getCustomResponseSource(),
                        responseDTO,
                        mockRequest,
                        mockApi.getName(),
                        mockApi.getPath()
                );

                // 缓存转换结果
                if (cacheSeconds > 0 && responseDTO != null) {
                    long expireAt = System.currentTimeMillis() + cacheSeconds * 1000L;
                    customResponseCache.put(mockApi.getId(), new CachedCustomResponse(responseDTO, expireAt));
                    log.info("缓存自定义响应: apiId={}, 缓存时间={}秒", mockApi.getId(), cacheSeconds);
                }
            } else if (mockApi.getCustomResponseHandler() != null && !mockApi.getCustomResponseHandler().trim().isEmpty()) {
                // 使用Spring Bean引用模式
                responseDTO = transformerRegistry.transform(
                        mockApi.getCustomResponseHandler(),
                        responseDTO,
                        mockRequest,
                        mockApi.getName(),
                        mockApi.getPath()
                );
            }

            statusCode = responseDTO.getStatusCode();
            return responseDTO;

        } catch (Exception e) {
            log.error("处理Mock请求失败", e);
            statusCode = 500;
            return createErrorResponse(500, "服务器内部错误");
        } finally {
            // 异步记录请求日志
            if (request != null && matchedApi != null) {
                long responseTime = System.currentTimeMillis() - startTime;
                Long userId = null;
                // TODO: 从请求中获取用户ID
                requestLogService.logRequestAsync(matchedApi, request, statusCode, responseTime, userId);

                // 记录 Prometheus 指标
                try {
                    String projectCode = matchedApi.getProject() != null ? matchedApi.getProject().getCode() : "unknown";
                    mockMetricsService.recordRequest(
                            request.getMethod(), statusCode, responseTime, projectCode);
                } catch (Exception e) {
                    log.debug("记录 Prometheus 指标失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 获取自定义接口响应缓存时间（秒）
     *
     * @return 缓存秒数，0 表示不缓存
     */
    private int getCustomResponseCacheSeconds() {
        try {
            String val = systemConfigService.getConfig("customResponseCacheSeconds");
            if (val != null && !val.trim().isEmpty()) {
                return Integer.parseInt(val.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("解析customResponseCacheSeconds配置失败", e);
        }
        return 600; // 默认 600 秒
    }

    /**
     * 清除指定接口的自定义响应缓存
     *
     * @param apiId 接口ID
     */
    public void evictCustomResponseCache(Long apiId) {
        CachedCustomResponse removed = customResponseCache.remove(apiId);
        if (removed != null) {
            log.info("清除自定义响应缓存: apiId={}", apiId);
        }
    }

    /**
     * 清除所有自定义响应缓存
     * <p>
     * 在系统配置中缓存时间被设置为 0 时调用，确保所有已缓存的响应被立即清除，
     * 后续请求将重新执行自定义代码计算最新响应。
     * </p>
     */
    public void clearCustomResponseCache() {
        int size = customResponseCache.size();
        customResponseCache.clear();
        log.info("已清除全部自定义响应缓存，共 {} 条", size);
    }

    /**
     * 查找Mock接口
     *
     * @param projectId 项目ID
     * @param path      接口路径
     * @param method    请求方法
     * @return 接口Optional
     */
    private Optional<MockApi> findMockApi(Long projectId, String path, String method) {
        try {
            MockApi.HttpMethod httpMethod = MockApi.HttpMethod.valueOf(method.toUpperCase());

            // 先尝试精确匹配
            Optional<MockApi> apiOpt = cacheUtil.getApiFromCache(projectId, path, httpMethod);
            if (apiOpt.isPresent()) {
                return apiOpt;
            }

            // 精确匹配失败，尝试 RESTful 路径模板匹配
            List<MockApi> apis = mockApiRepository.findByProjectIdAndMethod(projectId, httpMethod);
            for (MockApi api : apis) {
                if (isPathMatch(path, api.getPath())) {
                    log.debug("RESTful 路径模板匹配: {} -> {}", path, api.getPath());
                    return Optional.of(api);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("查找接口失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从请求路径中提取 RESTful 路径参数
     *
     * @param requestPath  请求路径，如 /user/admin
     * @param templatePath 模板路径，如 /user/{id}
     * @return 路径参数映射
     */
    private Map<String, String> extractPathParams(String requestPath, String templatePath) {
        Map<String, String> pathParams = new HashMap<>();

        // 分离路径和查询字符串
        String requestPathOnly = requestPath.split("\\?")[0];
        String templatePathOnly = templatePath.split("\\?")[0];

        // 分割路径为段
        String[] requestParts = requestPathOnly.split("/");
        String[] templateParts = templatePathOnly.split("/");

        // 如果段数不匹配，返回空
        if (requestParts.length != templateParts.length) {
            return pathParams;
        }

        // 逐段匹配并提取参数
        for (int i = 0; i < templateParts.length; i++) {
            String templatePart = templateParts[i];
            String requestPart = requestParts[i];

            // 如果模板段是占位符 {param}，则提取值
            if (templatePart.startsWith("{") && templatePart.endsWith("}")) {
                String paramName = templatePart.substring(1, templatePart.length() - 1);
                pathParams.put(paramName, requestPart);
            }
        }

        return pathParams;
    }

    /**
     * 判断请求路径是否匹配模板路径（支持 RESTful 风格）
     *
     * @param requestPath 请求路径，如 /user/admin 或 /user?name={name}
     * @param templatePath 模板路径，如 /user/{id} 或 /user?name={name}
     * @return 是否匹配
     */
    private boolean isPathMatch(String requestPath, String templatePath) {
        // 分离路径和查询字符串
        String requestPathOnly = requestPath.split("\\?")[0];
        String templatePathOnly = templatePath.split("\\?")[0];

        // 分割路径为段
        String[] requestParts = requestPathOnly.split("/");
        String[] templateParts = templatePathOnly.split("/");

        // 路径段数量必须相同
        if (requestParts.length != templateParts.length) {
            return false;
        }

        // 逐段匹配路径部分
        for (int i = 0; i < templateParts.length; i++) {
            String templatePart = templateParts[i];
            String requestPart = requestParts[i];

            // 如果模板段是占位符 {param}，则跳过值检查
            if (templatePart.startsWith("{") && templatePart.endsWith("}")) {
                continue;
            }

            // 非占位符段必须完全匹配
            if (!templatePart.equals(requestPart)) {
                return false;
            }
        }

        // 检查查询参数部分
        String requestQuery = requestPath.contains("?") ? requestPath.substring(requestPath.indexOf("?")) : "";
        String templateQuery = templatePath.contains("?") ? templatePath.substring(templatePath.indexOf("?")) : "";

        // 如果都有查询参数或都没有查询参数，则匹配成功
        // 查询参数的具体值匹配在后续的请求参数匹配逻辑中处理
        return true;
    }

    /**
     * 根据条件匹配响应
     *
     * @param responses   响应列表
     * @param mockRequest Mock请求
     * @return 匹配的响应
     */
    private MockResponse matchResponseByCondition(List<MockResponse> responses, MockRequest mockRequest) {
        for (MockResponse response : responses) {
            // 跳过默认响应，默认响应应该在其他逻辑中处理
            if (response.getIsDefault() != null && response.getIsDefault()) {
                continue;
            }

            boolean hasCondition = response.getCondition() != null && !response.getCondition().isEmpty();
            boolean hasParams = hasRequestParams(response);

            // 只有当响应明确配置了条件（condition 表达式或请求参数）时才进行匹配
            // 没有配置任何条件的响应不应该在此处被匹配，应交给后续的随机/激活/默认逻辑处理
            if (!hasCondition && !hasParams) {
                continue;
            }

            // 检查条件表达式
            boolean conditionMatched = false;
            if (hasCondition) {
                try {
                    if (evaluateCondition(response.getCondition(), mockRequest)) {
                        conditionMatched = true;
                    }
                } catch (Exception e) {
                    log.warn("条件表达式评估失败: {}", e.getMessage());
                }
            } else {
                // 没有条件表达式但有请求参数，视为条件通过
                conditionMatched = true;
            }

            // 条件通过后，检查请求参数
            if (conditionMatched) {
                if (matchRequestParams(response, mockRequest)) {
                    log.info("匹配到响应: 响应ID={}, 条件={}", response.getId(), response.getCondition());
                    return response;
                }
            }
        }
        return null;
    }

    /**
     * 检查响应是否配置了请求参数
     */
    private boolean hasRequestParams(MockResponse response) {
        try {
            var result = responseRequestParamService.getParamsByResponseId(response.getId());
            return result.getCode() == 200 && result.getData() != null && !result.getData().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据请求参数匹配响应
     *
     * @param response    响应
     * @param mockRequest Mock请求
     * @return 是否匹配
     */
    private boolean matchRequestParams(MockResponse response, MockRequest mockRequest) {
        try {
            var result = responseRequestParamService.getParamsByResponseId(response.getId());
            if (result.getCode() != 200 || result.getData() == null || result.getData().isEmpty()) {
                log.debug("响应 {} 没有配置请求参数，直接匹配", response.getId());
                return true;
            }

            var params = result.getData();
            log.debug("响应 {} 有 {} 个请求参数", response.getId(), params.size());

            // 记录是否有参数在实际请求中找到了值
            boolean hasActualValueMatch = false;

            for (var param : params) {
                if (!matchRequestParam(param, mockRequest)) {
                    log.debug("响应 {} 参数匹配失败", response.getId());
                    return false;
                }
                // 检查该参数在请求中是否实际存在
                if (paramValueExistsInRequest(param, mockRequest)) {
                    hasActualValueMatch = true;
                }
            }

            // 所有参数都通过了（可能只是因为非必填且无值），
            // 但如果没有任一参数在实际请求中有值，则不匹配
            if (!hasActualValueMatch) {
                log.debug("响应 {} 所有参数均未在请求中找到实际值，不匹配", response.getId());
                return false;
            }

            log.debug("响应 {} 所有参数匹配成功", response.getId());
            return true;
        } catch (Exception e) {
            log.warn("请求参数匹配失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查参数值在请求中是否实际存在
     */
    private boolean paramValueExistsInRequest(com.carolcoral.mockserver.dto.ResponseRequestParamDTO param,
                                               MockRequest mockRequest) {
        try {
            ResponseRequestParam.ParamType paramType = ResponseRequestParam.ParamType.valueOf(param.getParamType());
            String paramName = param.getParamName();

            switch (paramType) {
                case PATH:
                    return mockRequest.getPathParams() != null
                            && mockRequest.getPathParams().containsKey(paramName);
                case QUERY:
                    return mockRequest.getParams() != null
                            && mockRequest.getParams().containsKey(paramName);
                case HEADER:
                    return mockRequest.getHeaders() != null
                            && mockRequest.getHeaders().containsKey(paramName);
                case FILE:
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 匹配单个请求参数
     *
     * @param param       请求参数DTO
     * @param mockRequest Mock请求
     * @return 是否匹配
     */
    private boolean matchRequestParam(com.carolcoral.mockserver.dto.ResponseRequestParamDTO param,
                                   MockRequest mockRequest) {
        try {
            ResponseRequestParam.ParamType paramType = ResponseRequestParam.ParamType.valueOf(param.getParamType());
            String expectedValue = param.getParamValue();
            String paramName = param.getParamName();

            log.debug("匹配请求参数: 名称={}, 类型={}, 期望值={}", paramName, paramType, expectedValue);

            Object actualValue = null;

            switch (paramType) {
                case PATH:
                    // 从路径中获取值（RESTful风格）
                    actualValue = mockRequest.getPathParams() != null ? mockRequest.getPathParams().get(paramName) : null;
                    break;

                case QUERY:
                    // 从请求参数中获取值
                    if (mockRequest.getParams() != null) {
                        actualValue = mockRequest.getParams().get(paramName);
                    }
                    break;

                case REQUEST_BODY:
                    // 从请求体中获取值
                    if (mockRequest.getBody() != null) {
                        try {
                            String jsonBody = objectMapper.writeValueAsString(mockRequest.getBody());
                            actualValue = JsonPath.read(jsonBody, "$." + paramName);
                        } catch (Exception e) {
                            log.debug("从请求体获取值失败: {}", e.getMessage());
                        }
                    }
                    break;

                case HEADER:
                    // 从请求头中获取值
                    if (mockRequest.getHeaders() != null) {
                        actualValue = mockRequest.getHeaders().get(paramName);
                    }
                    break;

                case FILE:
                    // 文件类型直接返回true，不需要匹配值
                    log.debug("文件类型参数，跳过匹配");
                    return true;
            }

            // 如果参数是必填的，但是没有匹配到值，返回false
            if (param.getRequired() && actualValue == null) {
                return false;
            }

            // 如果参数不是必填的，且没有值，跳过这个参数
            if (!param.getRequired() && actualValue == null) {
                return true;
            }

            // 比较值
            if (expectedValue != null && actualValue != null) {
                // 特殊处理"通用"值，表示匹配任意值
                if ("通用".equals(expectedValue) || "*".equals(expectedValue)) {
                    log.debug("参数值匹配（通用模式）: 参数={}, 实际值={}", paramName, actualValue);
                    return true;
                }
                boolean matched = actualValue.toString().equals(expectedValue);
                log.debug("参数值匹配: 参数={}, 期望值={}, 实际值={}, 匹配={}", paramName, expectedValue, actualValue, matched);
                return matched;
            }

            return false;
        } catch (Exception e) {
            log.warn("请求参数匹配失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从路径中提取RESTful参数
     *
     * @param path      路径
     * @param paramName 参数名
     * @return 参数值
     */
    private String extractPathParam(String path, String paramName) {
        // 假设路径格式为: /api/users/{userId}/posts/{postId}
        // 提取 {userId} 或 {postId} 中的值
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.startsWith("{") && part.endsWith("}")) {
                String name = part.substring(1, part.length() - 1);
                if (name.equals(paramName)) {
                    // 这里需要根据实际请求路径来提取值
                    // 暂时返回null，需要从实际请求中获取
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 评估条件表达式
     *
     * @param condition   条件表达式
     * @param mockRequest Mock请求
     * @return 是否匹配
     */
    private boolean evaluateCondition(String condition, MockRequest mockRequest) {
        try {
            // 支持简单的JSONPath表达式
            // 例如: $.userId == '123' 或 $.status == 'active'
            
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                if (parts.length == 2) {
                    String jsonPath = parts[0].trim();
                    String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");
                    
                    Object actualValue = null;
                    
                    // 从请求体中获取值
                    if (mockRequest.getBody() != null) {
                        try {
                            String jsonBody = objectMapper.writeValueAsString(mockRequest.getBody());
                            actualValue = JsonPath.read(jsonBody, jsonPath);
                        } catch (Exception e) {
                            log.debug("从请求体获取值失败: {}", e.getMessage());
                        }
                    }
                    
                    // 从请求参数中获取值
                    if (actualValue == null && mockRequest.getParams() != null) {
                        String paramName = jsonPath.replace("$.", "");
                        actualValue = mockRequest.getParams().get(paramName);
                    }
                    
                    // 比较值
                    if (actualValue != null) {
                        return actualValue.toString().equals(expectedValue);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("条件评估失败: {}", e.getMessage());
        }
        
        return false;
    }

    /**
     * 根据权重随机选择响应
     *
     * @param responses 响应列表
     * @return 随机选择的响应
     */
    private MockResponse selectRandomResponse(List<MockResponse> responses) {
        int totalWeight = 0;
        for (MockResponse response : responses) {
            if (response.getWeight() != null && response.getWeight() > 0) {
                totalWeight += response.getWeight();
            }
        }

        if (totalWeight <= 0) {
            // 如果所有权重的总和为0，则等概率随机选择
            int randomIndex = ThreadLocalRandom.current().nextInt(responses.size());
            return responses.get(randomIndex);
        }

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (MockResponse response : responses) {
            int weight = response.getWeight() != null ? response.getWeight() : 0;
            if (weight > 0) {
                currentWeight += weight;
                if (randomWeight < currentWeight) {
                    return response;
                }
            }
        }

        // 如果没有找到匹配的，返回第一个
        return responses.get(0);
    }

    /**
     * 构建Mock响应
     *
     * @param mockApi  Mock接口
     * @param response 接口响应
     * @return Mock响应DTO
     */
    private MockResponseDTO buildMockResponse(MockApi mockApi, MockResponse response) {
        MockResponseDTO.MockResponseDTOBuilder builder = MockResponseDTO.builder()
                .statusCode(response.getStatusCode())
                .body(parseResponseBody(response.getResponseBody()));

        // 设置响应头
        if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
            try {
                Map<String, String> headers = objectMapper.readValue(response.getHeaders(), new TypeReference<Map<String, String>>() {});
                builder.headers(headers);
            } catch (Exception e) {
                log.warn("解析响应头失败: {}", e.getMessage());
            }
        }

        // 设置响应延迟（优先使用响应级别的延迟，如果没有则使用接口级别的延迟）
        Integer delay = null;
        if (response.getResponseDelay() != null && response.getResponseDelay() > 0) {
            delay = response.getResponseDelay();
        } else if (mockApi.getResponseDelay() != null && mockApi.getResponseDelay() > 0) {
            delay = mockApi.getResponseDelay();
        }

        if (delay != null && delay > 0) {
            builder.delay(delay);
        }

        MockResponseDTO mockResponseDTO = builder.build();
        log.info("构建Mock响应: 状态码={}, 延迟={}ms", mockResponseDTO.getStatusCode(), mockResponseDTO.getDelay());
        return mockResponseDTO;
    }

    /**
     * 解析响应体，自动处理模板占位符
     *
     * @param responseBody 响应体字符串
     * @return 解析后的响应体
     */
    private Object parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "";
        }

        responseBody = responseBody.trim();

        // 检查是否包含模板占位符 {{xxx}}
        if (responseBody.contains("{{")) {
            // JSON 模板：递归处理 JSON 中的占位符
            if ((responseBody.startsWith("{") && responseBody.endsWith("}")) ||
                    (responseBody.startsWith("[") && responseBody.endsWith("]"))) {
                try {
                    String processed = mockTemplateEngine.processJson(responseBody);
                    return objectMapper.readValue(processed, Object.class);
                } catch (Exception e) {
                    log.debug("JSON模板处理失败，回退到纯文本处理: {}", e.getMessage());
                }
            }
            // 纯文本模板
            String processed = mockTemplateEngine.process(responseBody);
            return processed;
        }

        // 尝试解析为JSON对象或数组
        if ((responseBody.startsWith("{") && responseBody.endsWith("}")) ||
                (responseBody.startsWith("[") && responseBody.endsWith("]"))) {
            try {
                return objectMapper.readValue(responseBody, Object.class);
            } catch (Exception e) {
                log.debug("解析JSON响应体失败，返回原始字符串: {}", e.getMessage());
            }
        }

        // 返回原始字符串
        return responseBody;
    }

    /**
     * 创建错误响应
     *
     * @param statusCode 状态码
     * @param message    错误消息
     * @return Mock响应DTO
     */
    private MockResponseDTO createErrorResponse(Integer statusCode, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("code", statusCode);
        errorBody.put("message", message);
        errorBody.put("timestamp", System.currentTimeMillis());

        return MockResponseDTO.builder()
                .statusCode(statusCode)
                .body(errorBody)
                .build();
    }
}
