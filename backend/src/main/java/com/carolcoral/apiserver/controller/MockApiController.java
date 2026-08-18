/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.plugin.TransformerRegistry;
import com.carolcoral.apiserver.service.ExportResult;
import com.carolcoral.apiserver.service.MockApiMarkdownService;
import com.carolcoral.apiserver.service.MockApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 自定义接口控制器
 *
 * @author carolcoral
 */
@Tag(name = "接口管理", description = "自定义接口管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/mock-apis")
public class MockApiController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockApiController.class);

    /**
     * 构造器
     */
    public MockApiController(MockApiService mockApiService, TransformerRegistry transformerRegistry,
                             MockApiMarkdownService mockApiMarkdownService, ObjectMapper objectMapper) {
        this.mockApiService = mockApiService;
        this.transformerRegistry = transformerRegistry;
        this.mockApiMarkdownService = mockApiMarkdownService;
        this.objectMapper = objectMapper;
    }

    private final MockApiService mockApiService;
    private final TransformerRegistry transformerRegistry;
    private final MockApiMarkdownService mockApiMarkdownService;
    private final ObjectMapper objectMapper;

    /**
     * 创建接口
     *
     * @param mockApi 接口信息
     * @return 创建的接口
     */
    @Operation(summary = "创建接口", description = "创建新的自定义接口（需要 api:create 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('api:create')")
    @PostMapping
    public ApiResponse<MockApi> createMockApi(@Parameter(description = "接口信息") @Valid @RequestBody MockApi mockApi) {
        log.info("创建接口请求: {}", mockApi.getName());
        return mockApiService.createMockApi(mockApi, 1L); // TODO: 从认证信息中获取用户ID
    }

    /**
     * 更新接口
     *
     * @param mockApi 接口信息
     * @return 更新的接口
     */
    @Operation(summary = "更新接口", description = "更新自定义接口信息（需要 api:edit 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('api:edit')")
    @PutMapping
    public ApiResponse<MockApi> updateMockApi(@Parameter(description = "接口信息") @Valid @RequestBody MockApi mockApi) {
        log.info("更新接口请求: {}", mockApi.getId());
        return mockApiService.updateMockApi(mockApi);
    }

    /**
     * 删除接口
     *
     * @param apiId 接口ID
     * @return 删除结果
     */
    @Operation(summary = "删除接口", description = "删除自定义接口（需要 api:delete 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('api:delete')")
    @DeleteMapping("/{apiId}")
    public ApiResponse<Void> deleteMockApi(@Parameter(description = "接口ID", example = "1") @PathVariable Long apiId) {
        log.info("删除接口请求: {}", apiId);
        return mockApiService.deleteMockApi(apiId);
    }

    /**
     * 根据ID查询接口
     *
     * @param apiId 接口ID
     * @return 接口信息
     */
    @Operation(summary = "根据ID查询接口", description = "根据接口ID查询接口信息")
    @GetMapping("/{apiId}")
    public ApiResponse<MockApi> getMockApiById(@Parameter(description = "接口ID", example = "1") @PathVariable Long apiId) {
        log.info("查询接口请求: {}", apiId);
        return mockApiService.getMockApiById(apiId);
    }

    /**
     * 根据接口路径和请求方法查询接口
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 接口信息
     */
    @Operation(summary = "根据接口路径和请求方法查询接口", description = "根据接口路径和请求方法查询接口信息")
    @GetMapping("/path")
    public ApiResponse<MockApi> getMockApiByPathAndMethod(
            @Parameter(description = "接口路径", example = "/api/user") @RequestParam String path,
            @Parameter(description = "请求方法", example = "GET") @RequestParam MockApi.HttpMethod method) {
        log.info("查询接口请求: {} {}", method, path);
        return mockApiService.getMockApiByPathAndMethod(path, method);
    }

    /**
     * 根据项目ID查询接口列表（支持分页和搜索）
     *
     * @param projectId 项目ID
     * @param name      接口名称（模糊搜索）
     * @param path      接口路径（模糊搜索）
     * @param method    请求方法
     * @param enabled   启用状态
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 分页结果
     */
    @Operation(summary = "根据项目ID查询接口列表", description = "根据项目ID查询该项目下的所有接口，支持分页和搜索")
    @GetMapping("/project/{projectId}")
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<MockApi>> getMockApisByProjectId(
            @Parameter(description = "项目ID", example = "1") @PathVariable Long projectId,
            @Parameter(description = "接口名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "接口路径（模糊搜索）") @RequestParam(required = false) String path,
            @Parameter(description = "请求方法") @RequestParam(required = false) MockApi.HttpMethod method,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("查询项目接口列表请求: projectId={}, name={}, path={}, method={}, enabled={}, page={}, size={}",
            projectId, name, path, method, enabled, page, size);
        return mockApiService.searchMockApisByProject(projectId, name, path, method, enabled, page, size);
    }

    /**
     * 查询所有接口（支持分页和搜索）
     *
     * @param name      接口名称（模糊搜索）
     * @param path      接口路径（模糊搜索）
     * @param method    请求方法
     * @param projectId 项目ID
     * @param enabled   启用状态
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 分页结果
     */
    @Operation(summary = "查询所有接口", description = "查询所有自定义接口列表，支持分页和搜索")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('api:view')")
    @GetMapping
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<MockApi>> getAllMockApis(
            @Parameter(description = "接口名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "接口路径（模糊搜索）") @RequestParam(required = false) String path,
            @Parameter(description = "请求方法") @RequestParam(required = false) MockApi.HttpMethod method,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        log.info("查询所有接口请求: name={}, path={}, method={}, projectId={}, enabled={}, page={}, size={}",
            name, path, method, projectId, enabled, page, size);
        return mockApiService.searchMockApis(name, path, method, projectId, enabled, page, size);
    }

    /**
     * 导出接口为 Markdown 文档
     * <p>
     * 支持多选接口导出，可通过 aiEnhance 手动控制是否进行 AI 增强；
     * 无论是否启用 AI 增强，生成内容均严格遵循规定的 Markdown 格式约束。
     *
     * @param body { "apiIds": [1, 2, 3], "aiEnhance": true }
     * @return Markdown 文件流
     */
    @Operation(summary = "导出接口为Markdown文档", description = "将选中的多个接口导出为 Markdown 文档，支持手动控制是否进行 AI 增强")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('api:view')")
    @PostMapping("/export-markdown")
    public ResponseEntity<byte[]> exportMarkdown(
            @Parameter(description = "导出请求体，包含 apiIds 接口ID列表和 aiEnhance 是否AI增强") @RequestBody java.util.Map<String, Object> body) {
        log.info("导出接口 Markdown 请求: {}", body);
        try {
            @SuppressWarnings("unchecked")
            List<Number> apiIdNumbers = (List<Number>) body.get("apiIds");
            boolean aiEnhance = Boolean.TRUE.equals(body.get("aiEnhance"));

            if (apiIdNumbers == null || apiIdNumbers.isEmpty()) {
                throw new IllegalArgumentException("请至少选择一个接口");
            }
            List<Long> apiIds = new java.util.ArrayList<>();
            for (Number n : apiIdNumbers) {
                apiIds.add(n.longValue());
            }

            ExportResult result = mockApiMarkdownService.exportMarkdown(apiIds, aiEnhance);
            byte[] bytes = result.getMarkdown().getBytes(StandardCharsets.UTF_8);

            String filename = aiEnhance ? "api-docs-ai-enhanced.md" : "api-docs.md";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(filename, StandardCharsets.UTF_8));

            // 将导出过程中产生的警告（如 AI 增强调用异常、输出不合规）通过响应头传递，
            // 前端可据此弹出友好提示；下载文件的 blob 流程保持不变
            if (!result.getWarnings().isEmpty()) {
                try {
                    String warningsJson = objectMapper.writeValueAsString(result.getWarnings());
                    headers.add("X-Export-Warnings", URLEncoder.encode(warningsJson, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.warn("序列化导出警告信息失败: {}", e.getMessage());
                }
            }

            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (IllegalArgumentException e) {
            log.warn("导出接口 Markdown 参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("导出接口 Markdown 失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查询启用状态的接口
     *
     * @return 接口列表
     */
    @Operation(summary = "查询启用状态的接口", description = "查询所有启用状态的自定义接口")
    @GetMapping("/enabled")
    public ApiResponse<java.util.List<MockApi>> getEnabledMockApis() {
        log.info("查询启用状态接口请求");
        return mockApiService.getEnabledMockApis();
    }

    /**
     * 切换接口状态
     *
     * @param apiId 接口ID
     * @return 操作结果
     */
    @Operation(summary = "切换接口状态", description = "切换接口的启用/禁用状态")
    @PutMapping("/{apiId}/toggle")
    public ApiResponse<MockApi> toggleApiStatus(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId) {
        log.info("切换接口状态请求: {}", apiId);
        return mockApiService.toggleApiStatus(apiId);
    }

    /**
     * 添加接口响应
     *
     * @param apiId        接口ID
     * @param mockResponse 响应信息
     * @return 添加的响应
     */
    @Operation(summary = "添加接口响应", description = "为接口添加响应配置")
    @PostMapping("/{apiId}/responses")
    public ApiResponse<MockResponse> addApiResponse(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId,
            @Parameter(description = "响应信息") @Valid @RequestBody MockResponse mockResponse) {
        log.info("添加接口响应请求: 接口={}", apiId);
        return mockApiService.addApiResponse(apiId, mockResponse);
    }

    /**
     * 更新接口响应
     *
     * @param mockResponse 响应信息
     * @return 更新的响应
     */
    @Operation(summary = "更新接口响应", description = "更新接口响应配置")
    @PutMapping("/responses")
    public ApiResponse<MockResponse> updateApiResponse(
            @Parameter(description = "响应信息") @Valid @RequestBody MockResponse mockResponse) {
        log.info("更新接口响应请求: {}", mockResponse.getId());
        return mockApiService.updateApiResponse(mockResponse);
    }

    /**
     * 删除接口响应
     *
     * @param responseId 响应ID
     * @return 删除结果
     */
    @Operation(summary = "删除接口响应", description = "删除接口响应配置")
    @DeleteMapping("/responses/{responseId}")
    public ApiResponse<Void> deleteApiResponse(
            @Parameter(description = "响应ID", example = "1") @PathVariable Long responseId) {
        log.info("删除接口响应请求: {}", responseId);
        return mockApiService.deleteApiResponse(responseId);
    }

    /**
     * 设置接口的激活响应
     *
     * @param apiId      接口ID
     * @param responseId 响应ID
     * @return 操作结果
     */
    @Operation(summary = "设置接口的激活响应", description = "设置接口的当前激活响应")
    @PostMapping("/{apiId}/responses/{responseId}/active")
    public ApiResponse<Void> setActiveResponse(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId,
            @Parameter(description = "响应ID", example = "1") @PathVariable Long responseId) {
        log.info("设置激活响应请求: 接口={}, 响应={}", apiId, responseId);
        return mockApiService.setActiveResponse(apiId, responseId);
    }

    /**
     * 获取接口的所有响应
     *
     * @param apiId 接口ID
     * @return 响应列表
     */
    @Operation(summary = "获取接口的所有响应", description = "获取指定接口的所有响应配置")
    @GetMapping("/{apiId}/responses")
    public ApiResponse<java.util.List<MockResponse>> getApiResponses(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId) {
        log.info("获取接口响应列表请求: 接口={}", apiId);
        return mockApiService.getApiResponses(apiId);
    }

    /**
     * 获取所有可用的自定义响应转换器
     *
     * @return 转换器名称和描述的映射
     */
    @Operation(summary = "获取所有自定义响应转换器", description = "获取系统中所有已注册的自定义响应转换器列表，用于接口配置时选择")
    @GetMapping("/transformers")
    public ApiResponse<java.util.Map<String, String>> getAvailableTransformers() {
        log.info("获取可用转换器列表请求");
        try {
            java.util.Map<String, String> transformers = transformerRegistry.getAllTransformers();
            return ApiResponse.success(transformers);
        } catch (Exception e) {
            log.error("获取转换器列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取转换器列表失败");
        }
    }

    /**
     * 获取接口的自定义响应处理器源码
     *
     * @param apiId 接口ID
     * @return 源码内容
     */
    @Operation(summary = "获取接口的自定义响应处理器源码", description = "获取指定接口已保存的自定义响应处理器Java源码")
    @GetMapping("/{apiId}/custom-response-source")
    public ApiResponse<String> getCustomResponseSource(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId) {
        log.info("获取自定义响应处理器源码: apiId={}", apiId);
        try {
            var result = mockApiService.getMockApiById(apiId);
            if (result.getCode() != 200 || result.getData() == null) {
                return ApiResponse.error("接口不存在");
            }
            String source = result.getData().getCustomResponseSource();
            return ApiResponse.success(source != null ? source : "");
        } catch (Exception e) {
            log.error("获取自定义响应处理器源码失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取源码失败");
        }
    }

    /**
     * 验证自定义响应处理器源码是否可以编译通过
     *
     * @param apiId      接口ID
     * @param sourceCode Java源码
     * @return 验证结果
     */
    @Operation(summary = "验证自定义响应处理器源码", description = "编译验证用户提交的Java源码是否能正常编译，不保存到数据库")
    @PostMapping("/{apiId}/custom-response-source/validate")
    public ApiResponse<String> validateCustomResponseSource(
            @Parameter(description = "接口ID", example = "1") @PathVariable Long apiId,
            @Parameter(description = "Java源码") @RequestBody java.util.Map<String, String> body) {
        String sourceCode = body.get("sourceCode");
        log.info("验证自定义响应处理器源码: apiId={}", apiId);
        try {
            if (sourceCode == null || sourceCode.trim().isEmpty()) {
                return ApiResponse.error("源码不能为空");
            }
            String error = transformerRegistry.validateSourceCode(apiId, sourceCode);
            if (error == null) {
                return ApiResponse.success("编译验证通过");
            } else {
                return ApiResponse.error(error);
            }
        } catch (Exception e) {
            log.error("验证源码失败: {}", e.getMessage(), e);
            return ApiResponse.error("验证失败: " + e.getMessage());
        }
    }

    /**
     * 获取动态编译器的调试信息
     * 用于排查编译失败问题
     */
    @Operation(summary = "获取动态编译器调试信息", description = "获取编译器状态和classpath信息，用于排查编译问题")
    @GetMapping("/compiler-debug")
    public ApiResponse<java.util.Map<String, Object>> getCompilerDebugInfo() {
        java.util.Map<String, Object> debugInfo = new java.util.HashMap<>();
        try {
            // 获取系统属性
            debugInfo.put("javaHome", System.getProperty("java.home"));
            debugInfo.put("userDir", System.getProperty("user.dir"));
            debugInfo.put("javaClassPath", System.getProperty("java.class.path"));

            // 检查 target/classes 是否存在
            String userDir = System.getProperty("user.dir");
            java.io.File targetClasses = new java.io.File(userDir, "target/classes");
            debugInfo.put("targetClassesExists", targetClasses.exists());
            debugInfo.put("targetClassesPath", targetClasses.getAbsolutePath());

            // 强制触发 DynamicCompiler 类加载（通过调用一个静态方法）
            // 这样可以确保 static 字段被正确初始化
            try {
                // 先检查编译器是否可用
                boolean compilerAvailable = com.carolcoral.apiserver.plugin.DynamicCompiler.isCompilerAvailable();
                debugInfo.put("compilerAvailable", compilerAvailable);

                // 获取 classpath（强制类加载）
                String classpath = com.carolcoral.apiserver.plugin.DynamicCompiler.getCompilationClasspath();
                debugInfo.put("dynamicCompilerClasspath", classpath);
                debugInfo.put("dynamicCompilerClasspathLength", classpath != null ? classpath.length() : 0);

                // 检查 classpath 是否包含关键路径
                if (classpath != null) {
                    debugInfo.put("containsTargetClasses", classpath.contains("target/classes"));
                    debugInfo.put("containsDot", classpath.contains("."));
                    // 提取前几个路径
                    String[] paths = classpath.split(File.pathSeparator);
                    debugInfo.put("classpathPathsCount", paths.length);
                    if (paths.length > 0) {
                        debugInfo.put("firstClasspathEntry", paths[0]);
                    }
                }
            } catch (Exception e) {
                debugInfo.put("classpathError", e.getMessage());
                log.error("获取动态编译器信息失败: {}", e.getMessage(), e);
            }

            return ApiResponse.success(debugInfo);
        } catch (Exception e) {
            log.error("获取调试信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取调试信息失败: " + e.getMessage());
        }
    }
}
