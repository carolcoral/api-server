/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.service.ResponseRequestParamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目数据导入导出服务
 * 支持 Swagger/OpenAPI 格式导出 和 项目级 JSON 格式导入导出
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-07-01
 */
@Service
public class ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;
    private final ResponseRequestParamService responseRequestParamService;

    public ProjectExportService(ProjectRepository projectRepository,
                                MockApiRepository mockApiRepository,
                                ResponseRequestParamService responseRequestParamService) {
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.responseRequestParamService = responseRequestParamService;
    }

    // ==========================================
    // Swagger/OpenAPI 导出
    // ==========================================

    /**
     * 将项目的所有 API 导出为 Swagger 2.0 / OpenAPI 3.0 格式的 JSON 字符串
     *
     * @param projectId 项目ID
     * @param version   OpenAPI 版本 ("2.0" 或 "3.0")
     * @return Swagger JSON 字符串
     */
    public String exportSwagger(Long projectId, String version) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在: " + projectId));

        List<MockApi> apis = mockApiRepository.findByProjectId(projectId);

        if ("2.0".equals(version)) {
            return buildSwagger2(project, apis);
        } else {
            return buildOpenApi3(project, apis);
        }
    }

    /**
     * 构建 Swagger 2.0 格式
     */
    private String buildSwagger2(Project project, List<MockApi> apis) {
        JSONObject swagger = new JSONObject();
        swagger.put("swagger", "2.0");

        // info
        JSONObject info = new JSONObject();
        info.put("title", project.getName());
        info.put("description", project.getDescription() != null ? project.getDescription() : "");
        info.put("version", "1.0.0");
        swagger.put("info", info);

        swagger.put("host", "localhost:8080");
        swagger.put("basePath", "/api/api-server/" + project.getCode());

        // schemes
        JSONArray schemes = new JSONArray();
        schemes.add("http");
        schemes.add("https");
        swagger.put("schemes", schemes);

        // paths
        JSONObject paths = new JSONObject();
        for (MockApi api : apis) {
            if (!api.getEnabled()) continue;
            String swaggerPath = convertToSwaggerPath(api.getPath());

            JSONObject pathObj = paths.containsKey(swaggerPath)
                    ? paths.getJSONObject(swaggerPath) : new JSONObject();

            JSONObject methodObj = buildSwagger2Method(api);
            pathObj.put(api.getMethod().name().toLowerCase(), methodObj);
            paths.put(swaggerPath, pathObj);
        }
        swagger.put("paths", paths);

        // definitions (schemas)
        JSONObject definitions = buildSwaggerDefinitions(apis);
        if (!definitions.isEmpty()) {
            swagger.put("definitions", definitions);
        }

        return JSON.toJSONString(swagger);
    }

    /**
     * 构建 OpenAPI 3.0 格式
     */
    private String buildOpenApi3(Project project, List<MockApi> apis) {
        JSONObject openapi = new JSONObject();
        openapi.put("openapi", "3.0.3");

        // info
        JSONObject info = new JSONObject();
        info.put("title", project.getName());
        info.put("description", project.getDescription() != null ? project.getDescription() : "");
        info.put("version", "1.0.0");
        openapi.put("info", info);

        // servers
        JSONArray servers = new JSONArray();
        JSONObject server = new JSONObject();
        server.put("url", "/api/api-server/" + project.getCode());
        server.put("description", project.getName());
        servers.add(server);
        openapi.put("servers", servers);

        // paths
        JSONObject paths = new JSONObject();
        for (MockApi api : apis) {
            if (!api.getEnabled()) continue;
            String swaggerPath = convertToSwaggerPath(api.getPath());

            JSONObject pathObj = paths.containsKey(swaggerPath)
                    ? paths.getJSONObject(swaggerPath) : new JSONObject();

            JSONObject methodObj = buildOpenApi3Method(api);
            pathObj.put(api.getMethod().name().toLowerCase(), methodObj);
            paths.put(swaggerPath, pathObj);
        }
        openapi.put("paths", paths);

        // components/schemas
        JSONObject schemas = buildOpenApiSchemas(apis);
        if (!schemas.isEmpty()) {
            JSONObject components = new JSONObject();
            components.put("schemas", schemas);
            openapi.put("components", components);
        }

        return JSON.toJSONString(openapi);
    }

    /**
     * 将内部路径转换为 Swagger 路径格式（{param} 保持不变，已经是标准格式）
     */
    private String convertToSwaggerPath(String path) {
        if (path == null || path.isEmpty()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 构建 Swagger 2.0 方法定义
     */
    private JSONObject buildSwagger2Method(MockApi api) {
        JSONObject method = new JSONObject();
        method.put("summary", api.getName());
        method.put("description", api.getDescription() != null ? api.getDescription() : "");
        method.put("operationId", api.getMethod().name().toLowerCase() + "_" + api.getId());

        // tags
        JSONArray tags = new JSONArray();
        tags.add(api.getProject().getName());
        method.put("tags", tags);

        // parameters (from path and query)
        JSONArray parameters = buildSwaggerParameters(api);
        if (!parameters.isEmpty()) {
            method.put("parameters", parameters);
        }

        // responses
        JSONObject responses = new JSONObject();
        for (MockResponse response : api.getResponses()) {
            if (!response.getEnabled()) continue;
            JSONObject respObj = new JSONObject();
            respObj.put("description", response.getConditionDesc() != null
                    ? response.getConditionDesc() : "Response " + response.getStatusCode());

            // schema from response body
            if (response.getResponseBody() != null && !response.getResponseBody().isEmpty()) {
                JSONObject schema = inferSchema(response.getResponseBody());
                if (schema != null) {
                    respObj.put("schema", schema);
                }
                // example
                try {
                    Object example = JSON.parse(response.getResponseBody());
                    respObj.put("examples", buildSwagger2Example(example));
                } catch (Exception ignored) {
                    // not valid JSON, skip example
                }
            }

            responses.put(String.valueOf(response.getStatusCode()), respObj);
        }

        if (responses.isEmpty()) {
            JSONObject defaultResp = new JSONObject();
            defaultResp.put("description", "Default response");
            responses.put("200", defaultResp);
        }
        method.put("responses", responses);

        // consumes/produces
        JSONArray produces = new JSONArray();
        produces.add("application/json");
        method.put("produces", produces);

        return method;
    }

    /**
     * 构建 OpenAPI 3.0 方法定义
     */
    private JSONObject buildOpenApi3Method(MockApi api) {
        JSONObject method = new JSONObject();
        method.put("summary", api.getName());
        method.put("description", api.getDescription() != null ? api.getDescription() : "");
        method.put("operationId", api.getMethod().name().toLowerCase() + "_" + api.getId());

        // tags
        JSONArray tags = new JSONArray();
        tags.add(api.getProject().getName());
        method.put("tags", tags);

        // parameters
        JSONArray parameters = buildOpenApiParameters(api);
        if (!parameters.isEmpty()) {
            method.put("parameters", parameters);
        }

        // requestBody for POST/PUT/PATCH
        if (api.getMethod() == MockApi.HttpMethod.POST
                || api.getMethod() == MockApi.HttpMethod.PUT
                || api.getMethod() == MockApi.HttpMethod.PATCH) {
            JSONObject requestBody = buildOpenApiRequestBody(api);
            if (requestBody != null) {
                method.put("requestBody", requestBody);
            }
        }

        // responses
        JSONObject responses = new JSONObject();
        for (MockResponse response : api.getResponses()) {
            if (!response.getEnabled()) continue;
            JSONObject respObj = new JSONObject();
            respObj.put("description", response.getConditionDesc() != null
                    ? response.getConditionDesc() : "Response " + response.getStatusCode());

            JSONObject content = new JSONObject();
            JSONObject mediaType = new JSONObject();

            // schema from response body
            if (response.getResponseBody() != null && !response.getResponseBody().isEmpty()) {
                JSONObject schema = inferSchema(response.getResponseBody());
                if (schema != null) {
                    mediaType.put("schema", schema);
                }
                // example
                try {
                    Object example = JSON.parse(response.getResponseBody());
                    mediaType.put("example", example);
                } catch (Exception ignored) {
                }
            }

            content.put("application/json", mediaType);
            respObj.put("content", content);
            responses.put(String.valueOf(response.getStatusCode()), respObj);
        }

        if (responses.isEmpty()) {
            JSONObject defaultResp = new JSONObject();
            defaultResp.put("description", "Default response");
            JSONObject content = new JSONObject();
            content.put("application/json", new JSONObject());
            defaultResp.put("content", content);
            responses.put("200", defaultResp);
        }
        method.put("responses", responses);

        return method;
    }

    /**
     * 构建 Swagger 2.0 参数列表
     */
    private JSONArray buildSwaggerParameters(MockApi api) {
        JSONArray parameters = new JSONArray();

        // 提取路径参数
        Set<String> pathParams = extractPathParams(api.getPath());
        for (String paramName : pathParams) {
            JSONObject param = new JSONObject();
            param.put("name", paramName);
            param.put("in", "path");
            param.put("required", true);
            param.put("type", "string");
            parameters.add(param);
        }

        // 从请求参数配置中提取
        try {
            for (MockResponse response : api.getResponses()) {
                var result = responseRequestParamService.getParamsByResponseId(response.getId());
                if (result.getCode() == 200 && result.getData() != null) {
                    for (var paramDTO : result.getData()) {
                        String paramType = paramDTO.getParamType();
                        if ("QUERY".equals(paramType) || "HEADER".equals(paramType)) {
                            JSONObject param = new JSONObject();
                            param.put("name", paramDTO.getParamName());
                            param.put("in", "QUERY".equals(paramType) ? "query" : "header");
                            param.put("required", paramDTO.getRequired());
                            param.put("type", "string");
                            param.put("description", paramDTO.getParamValue());
                            parameters.add(param);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取请求参数失败: {}", e.getMessage());
        }

        return parameters;
    }

    private JSONArray buildOpenApiParameters(MockApi api) {
        return buildSwaggerParameters(api); // 参数格式在 OAS 3.0 中基本相同
    }

    private JSONObject buildOpenApiRequestBody(MockApi api) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("required", true);

        JSONObject content = new JSONObject();
        JSONObject mediaType = new JSONObject();

        // 尝试从第一个响应的请求参数构建 schema
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        try {
            for (MockResponse response : api.getResponses()) {
                var result = responseRequestParamService.getParamsByResponseId(response.getId());
                if (result.getCode() == 200 && result.getData() != null) {
                    for (var paramDTO : result.getData()) {
                        if ("REQUEST_BODY".equals(paramDTO.getParamType())) {
                            JSONObject prop = new JSONObject();
                            prop.put("type", "string");
                            prop.put("description", paramDTO.getParamValue());
                            properties.put(paramDTO.getParamName(), prop);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("构建 requestBody 失败: {}", e.getMessage());
        }

        if (properties.isEmpty()) {
            schema.put("description", "Request body");
        } else {
            schema.put("properties", properties);
        }
        mediaType.put("schema", schema);
        content.put("application/json", mediaType);
        requestBody.put("content", content);

        return requestBody;
    }

    /**
     * 提取路径中的参数名
     */
    private Set<String> extractPathParams(String path) {
        Set<String> params = new LinkedHashSet<>();
        if (path == null) return params;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(path);
        while (m.find()) {
            params.add(m.group(1));
        }
        return params;
    }

    /**
     * 从响应体推断 JSON Schema
     */
    private JSONObject inferSchema(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return null;
        try {
            Object parsed = JSON.parse(responseBody);
            return inferSchemaFromValue(parsed);
        } catch (Exception e) {
            JSONObject schema = new JSONObject();
            schema.put("type", "string");
            return schema;
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject inferSchemaFromValue(Object value) {
        JSONObject schema = new JSONObject();
        if (value instanceof JSONObject) {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject obj = (JSONObject) value;
            for (Map.Entry<String, Object> entry : obj.entrySet()) {
                props.put(entry.getKey(), inferSchemaFromValue(entry.getValue()));
            }
            schema.put("properties", props);
        } else if (value instanceof JSONArray) {
            schema.put("type", "array");
            JSONArray arr = (JSONArray) value;
            if (!arr.isEmpty()) {
                schema.put("items", inferSchemaFromValue(arr.get(0)));
            }
        } else if (value instanceof Integer || value instanceof Long) {
            schema.put("type", "integer");
            schema.put("example", value);
        } else if (value instanceof Double || value instanceof Float) {
            schema.put("type", "number");
            schema.put("example", value);
        } else if (value instanceof Boolean) {
            schema.put("type", "boolean");
            schema.put("example", value);
        } else {
            schema.put("type", "string");
            schema.put("example", String.valueOf(value));
        }
        return schema;
    }

    private JSONObject buildSwagger2Example(Object example) {
        JSONObject examples = new JSONObject();
        examples.put("application/json", example);
        return examples;
    }

    private JSONObject buildSwaggerDefinitions(List<MockApi> apis) {
        JSONObject definitions = new JSONObject();
        int schemaIndex = 0;
        for (MockApi api : apis) {
            for (MockResponse response : api.getResponses()) {
                if (!response.getEnabled()) continue;
                if (response.getResponseBody() != null && !response.getResponseBody().isEmpty()) {
                    try {
                        Object parsed = JSON.parse(response.getResponseBody());
                        if (parsed instanceof JSONObject && !((JSONObject) parsed).isEmpty()) {
                            String schemaName = "Response" + (schemaIndex > 0 ? String.valueOf(schemaIndex) : "");
                            definitions.put(schemaName, inferSchemaFromValue(parsed));
                            schemaIndex++;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return definitions;
    }

    private JSONObject buildOpenApiSchemas(List<MockApi> apis) {
        return buildSwaggerDefinitions(apis); // 格式相同
    }

    // ==========================================
    // 项目级 JSON 导入导出
    // ==========================================

    /**
     * 导出项目数据为 JSON 格式（包含接口名称、地址、请求参数、响应报文等信息）
     *
     * @param projectId 项目ID
     * @return 导出 JSON 字符串
     */
    public String exportProjectData(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在: " + projectId));

        List<MockApi> apis = mockApiRepository.findByProjectId(projectId);

        JSONObject exportData = new JSONObject();
        exportData.put("version", "1.0");
        exportData.put("exportTime", LocalDateTime.now().format(DTF));
        exportData.put("format", "api-server-project-export");

        // 项目信息
        JSONObject projectInfo = new JSONObject();
        projectInfo.put("name", project.getName());
        projectInfo.put("code", project.getCode());
        projectInfo.put("description", project.getDescription() != null ? project.getDescription() : "");
        exportData.put("project", projectInfo);

        // API 列表
        JSONArray apiList = new JSONArray();
        for (MockApi api : apis) {
            JSONObject apiObj = new JSONObject();
            apiObj.put("name", api.getName());
            apiObj.put("path", api.getPath());
            apiObj.put("method", api.getMethod().name());
            apiObj.put("requestType", api.getRequestType().name());
            apiObj.put("description", api.getDescription() != null ? api.getDescription() : "");
            apiObj.put("enabled", api.getEnabled());
            apiObj.put("responseDelay", api.getResponseDelay());
            apiObj.put("enableRandom", api.getEnableRandom());
            apiObj.put("customResponseHandler", api.getCustomResponseHandler());
            apiObj.put("customResponseSource", api.getCustomResponseSource());

            // 响应列表
            JSONArray responseList = new JSONArray();
            for (MockResponse response : api.getResponses()) {
                JSONObject respObj = new JSONObject();
                respObj.put("statusCode", response.getStatusCode());
                respObj.put("contentType", response.getContentType());
                respObj.put("headers", response.getHeaders());
                respObj.put("responseBody", response.getResponseBody());
                respObj.put("weight", response.getWeight());
                respObj.put("condition", response.getCondition());
                respObj.put("conditionDesc", response.getConditionDesc());
                respObj.put("enabled", response.getEnabled());
                respObj.put("isDefault", response.getIsDefault());
                respObj.put("responseDelay", response.getResponseDelay());

                // 请求参数列表
                try {
                    var paramsResult = responseRequestParamService.getParamsByResponseId(response.getId());
                    if (paramsResult.getCode() == 200 && paramsResult.getData() != null) {
                        JSONArray paramList = new JSONArray();
                        for (var paramDTO : paramsResult.getData()) {
                            JSONObject paramObj = new JSONObject();
                            paramObj.put("paramName", paramDTO.getParamName());
                            paramObj.put("paramType", paramDTO.getParamType());
                            paramObj.put("paramValue", paramDTO.getParamValue());
                            paramObj.put("required", paramDTO.getRequired());
                            paramList.add(paramObj);
                        }
                        respObj.put("requestParams", paramList);
                    }
                } catch (Exception e) {
                    log.debug("获取响应参数失败: responseId={}", response.getId());
                }

                responseList.add(respObj);
            }
            apiObj.put("responses", responseList);
            apiList.add(apiObj);
        }
        exportData.put("apis", apiList);

        return JSON.toJSONString(exportData);
    }

    /**
     * 从 JSON 字符串导入项目数据
     *
     * @param projectId 目标项目ID
     * @param jsonData  导入数据 JSON 字符串
     * @param userId    操作用户ID
     * @param mode      导入模式: "merge"（合并） 或 "replace"（替换）
     * @return 导入结果
     */
    @Transactional
    public ImportResult importProjectData(Long projectId, String jsonData, Long userId, String mode) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在: " + projectId));

        JSONObject data;
        try {
            data = JSON.parseObject(jsonData);
        } catch (Exception e) {
            throw new RuntimeException("JSON 数据格式错误: " + e.getMessage());
        }

        // 验证格式
        String format = data.getString("format");
        if (!"api-server-project-export".equals(format)) {
            throw new RuntimeException("不支持的数据格式: " + format);
        }

        ImportResult result = new ImportResult();

        // 如果是替换模式，先删除所有现有 API
        if ("replace".equals(mode)) {
            List<MockApi> existingApis = mockApiRepository.findByProjectId(projectId);
            mockApiRepository.deleteAll(existingApis);
            result.setReplacedCount(existingApis.size());
        }

        // 导入 API
        JSONArray apis = data.getJSONArray("apis");
        if (apis != null) {
            for (int i = 0; i < apis.size(); i++) {
                try {
                    JSONObject apiObj = apis.getJSONObject(i);
                    importApi(apiObj, project, userId, result);
                } catch (Exception e) {
                    log.error("导入 API 失败: {}", e.getMessage());
                    result.addFailed(apis.getJSONObject(i).getString("name"));
                }
            }
        }

        return result;
    }

    /**
     * 从 JSON 文件导入项目数据（自动创建项目）
     * 先解析 JSON 中的 project.code，查找是否存在同名项目
     * 存在则按模式导入，不存在则先创建项目再导入
     */
    public ImportResult importProjectDataAuto(String jsonData, Long userId, String mode) {
        JSONObject data;
        try {
            data = JSON.parseObject(jsonData);
        } catch (Exception e) {
            throw new RuntimeException("JSON 数据格式错误: " + e.getMessage());
        }

        String format = data.getString("format");
        if (!"api-server-project-export".equals(format)) {
            throw new RuntimeException("不支持的数据格式: " + format);
        }

        JSONObject projectInfo = data.getJSONObject("project");
        if (projectInfo == null) {
            throw new RuntimeException("导入数据中缺少项目信息");
        }

        String projectCode = projectInfo.getString("code");
        String projectName = projectInfo.getString("name");
        String projectDescription = projectInfo.getString("description");

        if (projectCode == null || projectCode.isEmpty()) {
            throw new RuntimeException("导入数据中缺少项目编码");
        }

        // 查找是否已存在同名项目
        Project project = projectRepository.findByCode(projectCode).orElse(null);

        if (project == null) {
            // 项目不存在，自动创建
            project = new Project();
            project.setName(projectName != null ? projectName : projectCode);
            project.setCode(projectCode);
            project.setDescription(projectDescription != null ? projectDescription : "");
            project.setEnabled(true);
            project.setCreateUserId(userId);
            project = projectRepository.save(project);
            log.info("自动创建项目: {} (code={})", project.getId(), projectCode);
        }

        // 调用现有导入逻辑
        return importProjectData(project.getId(), jsonData, userId, mode);
    }

    private void importApi(JSONObject apiObj, Project project, Long userId, ImportResult result) {
        MockApi api = new MockApi();
        api.setName(apiObj.getString("name"));
        api.setPath(apiObj.getString("path"));
        api.setMethod(MockApi.HttpMethod.valueOf(apiObj.getString("method")));

        String requestType = apiObj.getString("requestType");
        api.setRequestType(requestType != null ? MockApi.RequestType.valueOf(requestType) : MockApi.RequestType.HTTP);

        api.setDescription(apiObj.getString("description"));
        api.setEnabled(apiObj.getBoolean("enabled") != null ? apiObj.getBoolean("enabled") : true);
        api.setResponseDelay(apiObj.getInteger("responseDelay"));
        api.setEnableRandom(apiObj.getBoolean("enableRandom") != null ? apiObj.getBoolean("enableRandom") : false);
        api.setCustomResponseHandler(apiObj.getString("customResponseHandler"));
        api.setCustomResponseSource(apiObj.getString("customResponseSource"));
        api.setProject(project);
        api.setCreateUserId(userId);

        // 检查是否已存在相同的 path+method
        final String apiPath = api.getPath();
        final MockApi.HttpMethod apiMethodEnum = api.getMethod();
        List<MockApi> existing = mockApiRepository.findByProjectIdAndMethod(project.getId(), apiMethodEnum);
        boolean duplicate = existing.stream().anyMatch(a -> a.getPath().equals(apiPath));
        if (duplicate) {
            result.addSkipped(api.getName());
            return;
        }

        // 先保存 API 以获得 ID
        api = mockApiRepository.save(api);

        // 导入响应
        JSONArray responses = apiObj.getJSONArray("responses");
        if (responses != null) {
            for (int j = 0; j < responses.size(); j++) {
                JSONObject respObj = responses.getJSONObject(j);
                MockResponse response = new MockResponse();
                response.setStatusCode(respObj.getInteger("statusCode"));
                response.setContentType(respObj.getString("contentType") != null
                        ? respObj.getString("contentType") : "application/json");
                response.setHeaders(respObj.getString("headers"));
                response.setResponseBody(respObj.getString("responseBody"));
                response.setWeight(respObj.getInteger("weight") != null ? respObj.getInteger("weight") : 100);
                response.setCondition(respObj.getString("condition"));
                response.setConditionDesc(respObj.getString("conditionDesc"));
                response.setEnabled(respObj.getBoolean("enabled") != null ? respObj.getBoolean("enabled") : true);
                response.setIsDefault(respObj.getBoolean("isDefault") != null ? respObj.getBoolean("isDefault") : false);
                response.setActive(false);
                response.setResponseDelay(respObj.getInteger("responseDelay") != null
                        ? respObj.getInteger("responseDelay") : 0);
                response.setMockApi(api);

                api.getResponses().add(response);
            }
        }

        mockApiRepository.save(api);
        result.addSuccess(api.getName());
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        private int successCount = 0;
        private int skippedCount = 0;
        private int failedCount = 0;
        private int replacedCount = 0;
        private List<String> successList = new ArrayList<>();
        private List<String> skippedList = new ArrayList<>();
        private List<String> failedList = new ArrayList<>();

        public void addSuccess(String name) { successCount++; successList.add(name); }
        public void addSkipped(String name) { skippedCount++; skippedList.add(name); }
        public void addFailed(String name) { failedCount++; failedList.add(name); }

        public int getSuccessCount() { return successCount; }
        public int getSkippedCount() { return skippedCount; }
        public int getFailedCount() { return failedCount; }
        public int getReplacedCount() { return replacedCount; }
        public void setReplacedCount(int replacedCount) { this.replacedCount = replacedCount; }
        public List<String> getSuccessList() { return successList; }
        public List<String> getSkippedList() { return skippedList; }
        public List<String> getFailedList() { return failedList; }
    }
}
