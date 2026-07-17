/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockApi.HttpMethod;
import com.carolcoral.apiserver.entity.MockApi.RequestType;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.MockResponseRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.repository.ResponseRequestParamRepository;
import com.carolcoral.apiserver.util.CacheUtil;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SwaggerImportService {

    private static final Logger log = LoggerFactory.getLogger(SwaggerImportService.class);

    private final MockApiRepository mockApiRepository;
    private final ProjectRepository projectRepository;
    private final MockApiService mockApiService;
    private final MockResponseRepository mockResponseRepository;
    private final ResponseRequestParamRepository requestParamRepository;
    private final CacheUtil cacheUtil;

    public SwaggerImportService(MockApiRepository mockApiRepository,
                                 ProjectRepository projectRepository,
                                 MockApiService mockApiService,
                                 MockResponseRepository mockResponseRepository,
                                 ResponseRequestParamRepository requestParamRepository,
                                 CacheUtil cacheUtil) {
        this.mockApiRepository = mockApiRepository;
        this.projectRepository = projectRepository;
        this.mockApiService = mockApiService;
        this.mockResponseRepository = mockResponseRepository;
        this.requestParamRepository = requestParamRepository;
        this.cacheUtil = cacheUtil;
    }

    /**
     * 导入结果 DTO
     */
    public static class ImportResult {
        public int total;
        public int success;
        public int failed;
        public int skipped;
        public List<ImportError> errors = new ArrayList<>();
        public List<ConflictItem> conflicts = new ArrayList<>();

        public static class ImportError {
            public String path;
            public String method;
            public String reason;
        }

        /**
         * 统一冲突项：记录接口所有维度（名称、描述、请求方式、响应报文、请求参数）的变更
         */
        public static class ConflictItem {
            /** 已有接口的 ID */
            public Long existingApiId;
            /** 接口路径 */
            public String path;
            /** 当前接口的请求方式 */
            public String method;
            /** 变更详情列表 */
            public List<ChangeDetail> changes = new ArrayList<>();

            /**
             * 单个变更详情
             */
            public static class ChangeDetail {
                /** 变更字段标识：name, description, method, responseBody, requestParams */
                public String field;
                /** 变更字段中文名 */
                public String fieldName;
                /** 已有值（旧值） */
                public String existingValue;
                /** 导入值（新值） */
                public String newValue;
            }
        }
    }

    /**
     * 冲突解决请求 DTO
     */
    public static class ResolveConflictRequest {
        /** 已有接口 ID */
        public Long existingApiId;
        /** 新接口名称（有变更时传） */
        public String newName;
        /** 新接口描述（有变更时传） */
        public String newDescription;
        /** 新请求方式（有变更时传） */
        public String newMethod;
        /** 新响应报文（有变更时传） */
        public String newResponseBody;
        /** 新请求参数 JSON（有变更时传），格式: [{"paramName":"...","paramType":"...","required":true},...] */
        public String newRequestParamsJson;
    }

    /**
     * 变更字段常量
     */
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_RESPONSE_BODY = "responseBody";
    private static final String FIELD_REQUEST_PARAMS = "requestParams";

    /**
     * 从 Swagger JSON 文件流导入
     */
    @Transactional
    public ImportResult importFromStream(InputStream inputStream, Long projectId, Long userId) {
        try {
            String content = new String(inputStream.readAllBytes());
            return importFromJson(content, projectId, userId);
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Swagger URL 导入
     */
    @Transactional
    public ImportResult importFromUrl(String url, Long projectId, Long userId) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("获取 Swagger 文档失败，HTTP 状态码: " + response.statusCode());
            }

            return importFromJson(response.body(), projectId, userId);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("获取 Swagger 文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JSON 字符串解析并导入
     */
    private ImportResult importFromJson(String jsonContent, Long projectId, Long userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            throw new RuntimeException("项目不存在");
        }

        SwaggerParseResult parseResult = new OpenAPIParser().readContents(jsonContent, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();

        if (openAPI == null) {
            throw new RuntimeException("无法解析 Swagger/OpenAPI 文档，请确认文件格式正确");
        }

        if (parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()) {
            log.warn("Swagger 解析警告: {}", parseResult.getMessages());
        }

        ImportResult result = new ImportResult();

        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            result.total = 0;
            return result;
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            processPathItem(path, pathItem, openAPI, projectId, userId, result);
        });

        return result;
    }

    private void processPathItem(String path, PathItem pathItem, OpenAPI openAPI,
                                  Long projectId, Long userId, ImportResult result) {
        Map<PathItem.HttpMethod, Operation> operations = new LinkedHashMap<>();
        if (pathItem.getGet() != null) operations.put(PathItem.HttpMethod.GET, pathItem.getGet());
        if (pathItem.getPost() != null) operations.put(PathItem.HttpMethod.POST, pathItem.getPost());
        if (pathItem.getPut() != null) operations.put(PathItem.HttpMethod.PUT, pathItem.getPut());
        if (pathItem.getDelete() != null) operations.put(PathItem.HttpMethod.DELETE, pathItem.getDelete());
        if (pathItem.getPatch() != null) operations.put(PathItem.HttpMethod.PATCH, pathItem.getPatch());
        if (pathItem.getHead() != null) operations.put(PathItem.HttpMethod.HEAD, pathItem.getHead());
        if (pathItem.getOptions() != null) operations.put(PathItem.HttpMethod.OPTIONS, pathItem.getOptions());
        if (pathItem.getTrace() != null) operations.put(PathItem.HttpMethod.TRACE, pathItem.getTrace());

        for (Map.Entry<PathItem.HttpMethod, Operation> entry : operations.entrySet()) {
            PathItem.HttpMethod swaggerMethod = entry.getKey();
            Operation operation = entry.getValue();

            HttpMethod mockMethod = mapHttpMethod(swaggerMethod);
            if (mockMethod == null) continue;

            result.total++;

            try {
                String apiName = generateApiName(operation, path, mockMethod);
                String newResponseBody = buildDefaultResponse(operation, openAPI);
                String desc = operation.getSummary();
                if (desc == null || desc.isBlank()) {
                    desc = operation.getDescription();
                }
                if (desc != null && desc.length() > 500) {
                    desc = desc.substring(0, 497) + "...";
                }
                // 构建新导入的请求参数列表
                List<Map<String, Object>> newParams = extractRequestParams(operation);
                String newParamsJson = JSON.toJSONString(newParams);

                // 仅在当前项目内按 path+method 精确匹配，避免跨项目检测
                Optional<MockApi> existingOpt = mockApiRepository.findByProjectIdAndPathAndMethod(
                        projectId, path, mockMethod);

                if (existingOpt.isPresent()) {
                    MockApi existingApi = existingOpt.get();
                    // 防御性校验：确保查到的接口确实属于当前项目
                    if (existingApi.getProject() == null || !projectId.equals(existingApi.getProject().getId())) {
                        log.warn("导入时发现跨项目接口记录，已忽略: projectId={}, apiId={}, path={}, method={}",
                                projectId, existingApi.getId(), path, mockMethod);
                        result.failed++;
                        ImportResult.ImportError err = new ImportResult.ImportError();
                        err.path = path;
                        err.method = swaggerMethod.name();
                        err.reason = "接口归属项目异常";
                        result.errors.add(err);
                        continue;
                    }

                    // 获取已有接口的现有数据
                    String existingDesc = existingApi.getDescription();
                    String existingResponseBody = getExistingDefaultResponseBody(existingApi.getId());
                    String existingParamsJson = getExistingRequestParamsJson(existingApi.getId());
                    String existingMethodName = existingApi.getMethod().name();

                    // 统一对比所有维度
                    List<ImportResult.ConflictItem.ChangeDetail> changes = buildChangeDetails(
                            existingApi.getName(), apiName,
                            existingDesc != null ? existingDesc : "", desc != null ? desc : "",
                            existingMethodName, mockMethod.name(),
                            existingResponseBody, newResponseBody,
                            existingParamsJson, newParamsJson);

                    if (changes.isEmpty()) {
                        // 所有维度均无变化，跳过
                        result.skipped++;
                        continue;
                    }

                    // 有变化，记录冲突
                    ImportResult.ConflictItem conflict = new ImportResult.ConflictItem();
                    conflict.existingApiId = existingApi.getId();
                    conflict.path = path;
                    conflict.method = existingApi.getMethod().name();
                    conflict.changes = changes;
                    result.conflicts.add(conflict);
                    continue;
                }

                // 不存在冲突，创建新接口
                MockApi mockApi = new MockApi();
                mockApi.setName(apiName);
                mockApi.setPath(path);
                mockApi.setMethod(mockMethod);
                mockApi.setRequestType(RequestType.HTTP);
                mockApi.setEnabled(true);
                mockApi.setResponseDelay(0);
                mockApi.setEnableRandom(false);
                mockApi.setDescription(desc);
                mockApi.setCreateTime(LocalDateTime.now());
                mockApi.setUpdateTime(LocalDateTime.now());
                mockApi.setCreateUserId(userId);

                Project project = new Project();
                project.setId(projectId);
                mockApi.setProject(project);

                MockResponse mockResponse = new MockResponse();
                mockResponse.setStatusCode(200);
                mockResponse.setContentType("application/json");
                mockResponse.setResponseBody(newResponseBody);
                mockResponse.setIsDefault(true);
                mockResponse.setEnabled(true);
                mockResponse.setActive(true);
                mockResponse.setWeight(1);
                mockResponse.setConditionDesc("默认响应");
                mockResponse.setMockApi(mockApi);
                mockResponse.setCreateTime(LocalDateTime.now());
                mockResponse.setUpdateTime(LocalDateTime.now());

                List<MockResponse> responses = new ArrayList<>();
                responses.add(mockResponse);
                mockApi.setResponses(responses);

                mockApi = mockApiRepository.save(mockApi);

                // 保存请求参数
                if (!newParams.isEmpty()) {
                    saveRequestParams(newParams, mockResponse);
                }

                result.success++;

            } catch (Exception e) {
                log.error("导入接口失败: {} {}", swaggerMethod, path, e);
                result.failed++;
                ImportResult.ImportError err = new ImportResult.ImportError();
                err.path = path;
                err.method = swaggerMethod.name();
                err.reason = e.getMessage();
                result.errors.add(err);
            }
        }
    }

    /**
     * 统一对比所有维度，构建变更详情列表
     */
    private List<ImportResult.ConflictItem.ChangeDetail> buildChangeDetails(
            String existingName, String newName,
            String existingDesc, String newDesc,
            String existingMethod, String newMethod,
            String existingResponseBody, String newResponseBody,
            String existingParamsJson, String newParamsJson) {

        List<ImportResult.ConflictItem.ChangeDetail> changes = new ArrayList<>();

        // 1. 接口名称
        if (!Objects.equals(existingName, newName) && newName != null && !newName.isBlank()) {
            ImportResult.ConflictItem.ChangeDetail cd = new ImportResult.ConflictItem.ChangeDetail();
            cd.field = FIELD_NAME;
            cd.fieldName = "接口名称";
            cd.existingValue = existingName;
            cd.newValue = newName;
            changes.add(cd);
        }

        // 2. 接口描述（null 和空串视为相同）
        String normExistingDesc = existingDesc != null ? existingDesc : "";
        String normNewDesc = newDesc != null ? newDesc : "";
        if (!Objects.equals(normExistingDesc, normNewDesc) && !normNewDesc.isBlank()) {
            ImportResult.ConflictItem.ChangeDetail cd = new ImportResult.ConflictItem.ChangeDetail();
            cd.field = FIELD_DESCRIPTION;
            cd.fieldName = "接口描述";
            cd.existingValue = normExistingDesc.isBlank() ? "(空)" : normExistingDesc;
            cd.newValue = normNewDesc;
            changes.add(cd);
        }

        // 3. 请求方式
        if (!Objects.equals(existingMethod, newMethod)) {
            ImportResult.ConflictItem.ChangeDetail cd = new ImportResult.ConflictItem.ChangeDetail();
            cd.field = FIELD_METHOD;
            cd.fieldName = "请求方式";
            cd.existingValue = existingMethod;
            cd.newValue = newMethod;
            changes.add(cd);
        }

        // 4. 响应报文
        if (!isJsonEqual(existingResponseBody, newResponseBody)) {
            ImportResult.ConflictItem.ChangeDetail cd = new ImportResult.ConflictItem.ChangeDetail();
            cd.field = FIELD_RESPONSE_BODY;
            cd.fieldName = "响应报文";
            cd.existingValue = existingResponseBody != null ? existingResponseBody : "{}";
            cd.newValue = newResponseBody != null ? newResponseBody : "{}";
            changes.add(cd);
        }

        // 5. 请求参数
        if (!isJsonEqual(existingParamsJson, newParamsJson) && newParamsJson != null && !"[]".equals(newParamsJson)) {
            ImportResult.ConflictItem.ChangeDetail cd = new ImportResult.ConflictItem.ChangeDetail();
            cd.field = FIELD_REQUEST_PARAMS;
            cd.fieldName = "请求参数";
            cd.existingValue = existingParamsJson;
            cd.newValue = newParamsJson;
            changes.add(cd);
        }

        return changes;
    }

    /**
     * 从 Swagger Operation 中提取请求参数
     */
    private List<Map<String, Object>> extractRequestParams(Operation operation) {
        List<Map<String, Object>> params = new ArrayList<>();

        // 提取 path/query/header 参数
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("paramName", param.getName());
                p.put("paramType", mapParamType(param.getIn()));
                p.put("required", param.getRequired() != null ? param.getRequired() : false);
                // 提取示例值
                if (param.getExample() != null) {
                    p.put("paramValue", String.valueOf(param.getExample()));
                } else if (param.getSchema() != null && param.getSchema().getExample() != null) {
                    p.put("paramValue", String.valueOf(param.getSchema().getExample()));
                } else {
                    p.put("paramValue", "");
                }
                params.add(p);
            }
        }

        // 提取 requestBody 参数（JSON body 中的字段）
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            Content content = requestBody.getContent();
            MediaType jsonMedia = content.get("application/json");
            if (jsonMedia == null) {
                jsonMedia = content.get("*/*");
            }
            if (jsonMedia != null && jsonMedia.getSchema() != null) {
                Schema<?> schema = jsonMedia.getSchema();
                if (schema.getProperties() != null) {
                    schema.getProperties().forEach((propName, propSchema) -> {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("paramName", propName);
                        p.put("paramType", "REQUEST_BODY");
                        // 检查 required 列表
                        boolean isRequired = schema.getRequired() != null && schema.getRequired().contains(propName);
                        p.put("required", isRequired);
                        p.put("paramValue", "");
                        params.add(p);
                    });
                }
            }
        }

        return params;
    }

    /**
     * 映射 Swagger 参数位置到 ParamType
     */
    private String mapParamType(String in) {
        if (in == null) return "REQUEST_BODY";
        return switch (in.toLowerCase()) {
            case "path" -> "PATH";
            case "query" -> "QUERY";
            case "header" -> "HEADER";
            default -> "REQUEST_BODY";
        };
    }

    /**
     * 获取已有接口的请求参数 JSON
     */
    private String getExistingRequestParamsJson(Long apiId) {
        List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
        if (responses == null || responses.isEmpty()) return "[]";

        MockResponse defaultResp = null;
        for (MockResponse r : responses) {
            if (Boolean.TRUE.equals(r.getIsDefault())) {
                defaultResp = r;
                break;
            }
        }
        if (defaultResp == null) {
            defaultResp = responses.get(0);
        }

        List<ResponseRequestParam> params = requestParamRepository.findByMockResponseId(defaultResp.getId());
        if (params == null || params.isEmpty()) return "[]";

        List<Map<String, Object>> paramList = new ArrayList<>();
        for (ResponseRequestParam p : params) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paramName", p.getParamName());
            m.put("paramType", p.getParamType().name());
            m.put("required", p.getRequired());
            m.put("paramValue", p.getParamValue() != null ? p.getParamValue() : "");
            paramList.add(m);
        }
        return JSON.toJSONString(paramList);
    }

    /**
     * 保存请求参数
     */
    private void saveRequestParams(List<Map<String, Object>> params, MockResponse mockResponse) {
        for (Map<String, Object> paramMap : params) {
            ResponseRequestParam rp = new ResponseRequestParam();
            rp.setParamName((String) paramMap.get("paramName"));
            rp.setParamType(ResponseRequestParam.ParamType.valueOf((String) paramMap.get("paramType")));
            rp.setRequired((Boolean) paramMap.getOrDefault("required", false));
            rp.setParamValue((String) paramMap.getOrDefault("paramValue", ""));
            rp.setMockResponse(mockResponse);
            requestParamRepository.save(rp);
        }
    }

    /**
     * 映射 Swagger HttpMethod 到 MockApi HttpMethod
     */
    private HttpMethod mapHttpMethod(PathItem.HttpMethod swaggerMethod) {
        return switch (swaggerMethod) {
            case GET -> HttpMethod.GET;
            case POST -> HttpMethod.POST;
            case PUT -> HttpMethod.PUT;
            case DELETE -> HttpMethod.DELETE;
            case PATCH -> HttpMethod.PATCH;
            default -> null;
        };
    }

    /**
     * 生成 API 名称
     */
    private String generateApiName(Operation operation, String path, HttpMethod method) {
        String name = operation.getSummary();
        if (name != null && !name.isBlank()) {
            return name.length() > 100 ? name.substring(0, 97) + "..." : name;
        }
        String operationId = operation.getOperationId();
        if (operationId != null && !operationId.isBlank()) {
            return operationId.length() > 100 ? operationId.substring(0, 97) + "..." : operationId;
        }
        return method.name() + " " + path;
    }

    /**
     * 根据 Swagger 定义构建默认的 JSON 响应示例
     */
    private String buildDefaultResponse(Operation operation, OpenAPI openAPI) {
        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) return "{}";

        ApiResponse successResponse = responses.get("200");
        if (successResponse == null) successResponse = responses.get("default");
        if (successResponse == null) {
            String firstKey = responses.keySet().iterator().next();
            successResponse = responses.get(firstKey);
        }
        if (successResponse == null) return "{}";

        Content content = successResponse.getContent();
        if (content == null) return "{}";

        MediaType mediaType = content.get("application/json");
        if (mediaType == null) mediaType = content.get("*/*");
        if (mediaType == null && !content.isEmpty()) {
            String firstType = content.keySet().iterator().next();
            mediaType = content.get(firstType);
        }
        if (mediaType == null || mediaType.getSchema() == null) return "{}";

        Schema<?> schema = mediaType.getSchema();
        if (schema.getExample() != null) {
            Object example = schema.getExample();
            if (example instanceof String) return (String) example;
            return JSON.toJSONString(example);
        }
        return generateExampleFromSchema(schema, openAPI);
    }

    private String generateExampleFromSchema(Schema<?> schema, OpenAPI openAPI) {
        return generateExampleFromSchema(schema, openAPI, new HashSet<>(), 0);
    }

    private String generateExampleFromSchema(Schema<?> schema, OpenAPI openAPI,
                                              Set<String> visitedRefs, int depth) {
        if (schema == null) return "{}";
        if (depth > 20) return "{}";

        String ref = schema.get$ref();
        if (ref != null) {
            if (visitedRefs.contains(ref)) return "{}";
            visitedRefs.add(ref);
            Schema<?> resolved = resolveRef(ref, openAPI);
            if (resolved != null) {
                return generateExampleFromSchema(resolved, openAPI, visitedRefs, depth + 1);
            }
            return "{}";
        }

        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            String itemExample = generateExampleFromSchema(schema.getItems(), openAPI, visitedRefs, depth + 1);
            return "[" + itemExample + "]";
        }

        if ("object".equals(schema.getType()) || schema.getProperties() != null) {
            return generateObjectExample(schema, openAPI, visitedRefs, depth);
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return JSON.toJSONString(schema.getEnum().get(0));
        }

        return getPrimitiveExample(schema.getType(), schema.getFormat());
    }

    private String generateObjectExample(Schema<?> schema, OpenAPI openAPI,
                                          Set<String> visitedRefs, int depth) {
        Map<String, Object> example = new LinkedHashMap<>();
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((propName, propSchema) -> {
                Object value = generateExampleValue(propSchema, openAPI, visitedRefs, depth + 1);
                example.put(propName, value);
            });
        }
        return example.isEmpty() ? "{}" : JSON.toJSONString(example);
    }

    private Object generateExampleValue(Schema<?> propSchema, OpenAPI openAPI,
                                         Set<String> visitedRefs, int depth) {
        if (propSchema == null) return "";
        if (depth > 20) return "";

        String ref = propSchema.get$ref();
        if (ref != null) {
            if (visitedRefs.contains(ref)) return "{}";
            visitedRefs.add(ref);
            Schema<?> resolved = resolveRef(ref, openAPI);
            if (resolved != null) {
                return generateExampleValue(resolved, openAPI, visitedRefs, depth + 1);
            }
            return "{}";
        }

        String type = propSchema.getType();

        if ("array".equals(type) && propSchema.getItems() != null) {
            List<Object> arr = new ArrayList<>();
            arr.add(generateExampleValue(propSchema.getItems(), openAPI, visitedRefs, depth + 1));
            return arr;
        }

        if ("object".equals(type) || propSchema.getProperties() != null) {
            Map<String, Object> obj = new LinkedHashMap<>();
            if (propSchema.getProperties() != null) {
                propSchema.getProperties().forEach((k, v) -> {
                    obj.put(k, generateExampleValue(v, openAPI, visitedRefs, depth + 1));
                });
            }
            return obj.isEmpty() ? "{}" : obj;
        }

        if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) {
            return propSchema.getEnum().get(0);
        }

        return getPrimitiveExampleValue(propSchema.getType(), propSchema.getFormat());
    }

    private Object getPrimitiveExampleValue(String type, String format) {
        if (type == null) return "";
        return switch (type) {
            case "integer", "number" -> {
                if ("int64".equals(format) || "long".equals(format)) yield 1000001L;
                if ("float".equals(format) || "double".equals(format)) yield 3.14;
                yield 1;
            }
            case "boolean" -> true;
            case "string" -> {
                if ("date".equals(format)) yield "2024-01-01";
                if ("date-time".equals(format)) yield "2024-01-01T00:00:00Z";
                if ("email".equals(format)) yield "user@example.com";
                if ("uri".equals(format) || "url".equals(format)) yield "https://example.com";
                yield "string";
            }
            default -> "";
        };
    }

    private String getPrimitiveExample(String type, String format) {
        Object val = getPrimitiveExampleValue(type, format);
        if (val instanceof String) return "\"" + val + "\"";
        return String.valueOf(val);
    }

    private Schema<?> resolveRef(String ref, OpenAPI openAPI) {
        if (ref == null || openAPI == null) return null;
        if (ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                return openAPI.getComponents().getSchemas().get(schemaName);
            }
        }
        return null;
    }

    private String getExistingDefaultResponseBody(Long apiId) {
        List<MockResponse> responses = mockResponseRepository.findByMockApiId(apiId);
        if (responses != null && !responses.isEmpty()) {
            for (MockResponse r : responses) {
                if (Boolean.TRUE.equals(r.getIsDefault())) {
                    return r.getResponseBody();
                }
            }
            return responses.get(0).getResponseBody();
        }
        return "{}";
    }

    private boolean isJsonEqual(String json1, String json2) {
        if (json1 == null && json2 == null) return true;
        if (json1 == null || json2 == null) return false;
        try {
            Object obj1 = JSON.parse(json1.trim());
            Object obj2 = JSON.parse(json2.trim());
            return Objects.equals(obj1, obj2);
        } catch (JSONException e) {
            return json1.trim().equals(json2.trim());
        }
    }

    /**
     * 解决导入冲突：用新导入的数据覆盖已有接口信息
     */
    @Transactional
    public int resolveConflicts(Long projectId, List<ResolveConflictRequest> requests) {
        int resolved = 0;
        for (ResolveConflictRequest req : requests) {
            Optional<MockApi> existingOpt = mockApiRepository.findById(req.existingApiId);
            if (existingOpt.isEmpty()) {
                log.warn("冲突解决失败，接口不存在: {}", req.existingApiId);
                continue;
            }
            MockApi existingApi = existingOpt.get();

            if (!existingApi.getProject().getId().equals(projectId)) {
                log.warn("冲突解决失败，接口不属于当前项目: {}", req.existingApiId);
                continue;
            }

            // 更新接口名称
            if (req.newName != null && !req.newName.isBlank()) {
                existingApi.setName(req.newName);
            }

            // 更新接口描述
            if (req.newDescription != null && !req.newDescription.isBlank()) {
                existingApi.setDescription(req.newDescription);
            }

            // 更新请求方式
            if (req.newMethod != null && !req.newMethod.isBlank()) {
                try {
                    HttpMethod newHttpMethod = HttpMethod.valueOf(req.newMethod);
                    existingApi.setMethod(newHttpMethod);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的请求方式: {}", req.newMethod);
                }
            }

            existingApi.setUpdateTime(LocalDateTime.now());
            mockApiRepository.save(existingApi);

            // 获取默认响应
            List<MockResponse> responses = mockResponseRepository.findByMockApiId(existingApi.getId());
            MockResponse defaultResp = null;
            if (responses != null) {
                for (MockResponse r : responses) {
                    if (Boolean.TRUE.equals(r.getIsDefault())) {
                        defaultResp = r;
                        break;
                    }
                }
                if (defaultResp == null) {
                    defaultResp = responses.get(0);
                }
            }

            // 更新响应报文
            if (req.newResponseBody != null && !req.newResponseBody.isBlank() && defaultResp != null) {
                defaultResp.setResponseBody(req.newResponseBody);
                defaultResp.setUpdateTime(LocalDateTime.now());
                mockResponseRepository.save(defaultResp);
            }

            // 更新请求参数
            if (req.newRequestParamsJson != null && !req.newRequestParamsJson.isBlank() && defaultResp != null) {
                // 删除旧的请求参数
                requestParamRepository.deleteByMockResponseId(defaultResp.getId());
                // 解析并保存新的请求参数
                try {
                    List<Map<String, Object>> newParams = JSON.parseArray(req.newRequestParamsJson)
                            .toJavaList((Class<Map<String, Object>>) (Class<?>) Map.class);
                    for (Map<String, Object> paramMap : newParams) {
                        ResponseRequestParam rp = new ResponseRequestParam();
                        rp.setParamName((String) paramMap.get("paramName"));
                        rp.setParamType(ResponseRequestParam.ParamType.valueOf((String) paramMap.get("paramType")));
                        rp.setRequired((Boolean) paramMap.getOrDefault("required", false));
                        rp.setParamValue((String) paramMap.getOrDefault("paramValue", ""));
                        rp.setMockResponse(defaultResp);
                        requestParamRepository.save(rp);
                    }
                } catch (Exception e) {
                    log.warn("解析请求参数 JSON 失败: {}", req.newRequestParamsJson, e);
                }
            }

            // 更新缓存
            cacheUtil.cacheApi(existingApi);
            List<MockResponse> updatedResponses = mockResponseRepository.findByMockApiId(existingApi.getId());
            cacheUtil.cacheApiResponses(existingApi.getId(), updatedResponses);

            resolved++;
            log.info("冲突解决成功: apiId={}, path={}, method={}", existingApi.getId(), existingApi.getPath(), existingApi.getMethod());
        }
        return resolved;
    }
}
