/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.carolcoral.mockserver.entity.AiConfig;
import com.carolcoral.mockserver.entity.CustomCodeTemplate;
import com.carolcoral.mockserver.entity.EmailTemplate;
import com.carolcoral.mockserver.repository.CustomCodeTemplateRepository;
import com.carolcoral.mockserver.repository.EmailTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 服务 - 封装 OpenAI 协议兼容的 LLM API 调用
 *
 * @author carolcoral
 * @since 2026-06-23
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private CustomCodeTemplateRepository codeTemplateRepository;

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService() {
        // 使用 getRestTemplate() 方法获取配置了超时的 RestTemplate
    }

    /**
     * 获取配置了超时的 RestTemplate（读取用户配置的超时时间，默认 15 分钟）
     */
    private RestTemplate getRestTemplate() {
        int readTimeoutSeconds = 900; // 默认 15 分钟
        try {
            AiConfig config = aiConfigService.getEnabledConfig();
            if (config != null && config.getTimeout() != null && config.getTimeout() > 0) {
                readTimeoutSeconds = config.getTimeout();
                log.debug("AI RestTemplate 使用用户配置的超时: {}秒", readTimeoutSeconds);
            }
        } catch (Exception e) {
            log.warn("读取 AI 超时配置失败，使用默认 15 分钟: {}", e.getMessage());
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());
        RestTemplate rt = new RestTemplate(factory);
        // 禁止 DefaultResponseErrorHandler 在非2xx时抛异常，让调用方自行处理响应
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                // no-op: 不抛出异常，由 callAiApi() 统一处理
            }
        });
        return rt;
    }

    /**
     * 生成 Mock 响应数据
     *
     * @param apiMethod   接口请求方法 (GET/POST/PUT/DELETE/PATCH)
     * @param apiPath     接口路径
     * @param apiName     接口名称
     * @param description 接口描述
     * @param count       生成数量（默认3）
     * @return AI 生成的响应数据列表
     */
    public List<Map<String, Object>> generateMockResponse(
            String apiMethod, String apiPath, String apiName, String description, Integer count) {

        AiConfig config = aiConfigService.getEnabledConfig();
        if (config == null) {
            throw new RuntimeException("未启用任何 AI 服务商，请先在 AI 设置中配置并启用一个服务商");
        }

        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new RuntimeException("AI 服务商 API 地址未配置");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new RuntimeException("AI 服务商 API Key 未配置");
        }

        int generateCount = (count != null && count > 0 && count <= 5) ? count : 3;

        String prompt = buildPrompt(apiMethod, apiPath, apiName, description, generateCount);

        try {
            String responseJson = callAiApi(config, prompt);
            return parseResponse(responseJson, generateCount);
        } catch (Exception e) {
            log.error("AI 生成 Mock 响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 AI 生成接口描述
     */
    public String generateApiDescription(String apiMethod, String apiPath, String apiName) {
        AiConfig config = aiConfigService.getEnabledConfig();
        if (config == null) {
            throw new RuntimeException("未启用任何 AI 服务商");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new RuntimeException("AI 服务商 API 地址未配置");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new RuntimeException("AI 服务商 API Key 未配置");
        }

        String prompt = String.format(
                "请为以下 API 接口生成一段简洁的中文描述（50字以内），仅返回描述文本，不要包含任何其他内容。\n\n" +
                "接口名称：%s\n请求方法：%s\n接口路径：%s",
                apiName != null ? apiName : "未命名接口",
                apiMethod,
                apiPath
        );

        try {
            String responseJson = callAiApi(config, prompt);
            return extractTextContent(responseJson);
        } catch (Exception e) {
            log.error("AI 生成接口描述失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 生成描述失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 AI 生成接口描述（流式 SSE）
     */
    public java.io.BufferedReader generateApiDescriptionStream(String apiMethod, String apiPath, String apiName) throws Exception {
        String prompt = String.format(
                "请为以下 API 接口生成一段简洁的中文描述（50字以内），仅返回描述文本，不要包含任何其他内容。\n\n" +
                "接口名称：%s\n请求方法：%s\n接口路径：%s",
                apiName != null ? apiName : "未命名接口",
                apiMethod,
                apiPath
        );
        return callAiApiStream(prompt, "你是一个专业的 API 文档助手，只返回简洁的描述文本，不返回任何额外内容。");
    }

    /**
     * 调用 AI 生成邮件模板
     *
     * @param templateType 模板类型 (REGISTER/RESET_PASSWORD/PASSWORD_CHANGED)
     * @param templateName 模板名称
     * @param existingSubject 已有主题（可为null，用于参考）
     * @param existingContent 已有内容（可为null，用于参考）
     * @return 包含 subject 和 content 的 Map
     */
    public Map<String, String> generateEmailTemplate(String templateType, String templateName,
                                                       String existingSubject, String existingContent,
                                                       String customPrompt) {
        AiConfig config = aiConfigService.getEnabledConfig();
        if (config == null) {
            throw new RuntimeException("未启用任何 AI 服务商");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new RuntimeException("AI 服务商 API 地址未配置");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new RuntimeException("AI 服务商 API Key 未配置");
        }

        String promptStr = buildEmailTemplatePrompt(templateType, templateName, existingSubject, existingContent, customPrompt);
        log.debug("AI 邮件模板 Prompt: {}", promptStr);

        try {
            String responseJson = callAiApi(config, promptStr);
            log.info("AI 邮件模板 API 响应长度: {}", responseJson != null ? responseJson.length() : 0);

            String content = extractTextContent(responseJson);
            log.info("AI 邮件模板原始响应内容: {}", content);

            // 清理可能的 markdown 标记（更健壮的清理逻辑）
            content = cleanMarkdownJson(content);
            log.info("AI 邮件模板清理后内容: {}", content);

            JsonNode root = objectMapper.readTree(content);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("subject", root.has("subject") ? root.get("subject").asText() : "");
            result.put("content", root.has("content") ? root.get("content").asText() : "");
            log.info("AI 邮件模板生成成功: subject长度={}, content长度={}",
                    result.get("subject") != null ? result.get("subject").length() : 0,
                    result.get("content") != null ? result.get("content").length() : 0);
            return result;
        } catch (Exception e) {
            log.error("AI 生成邮件模板失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 生成邮件模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 AI 生成邮件模板（流式 SSE）
     */
    public java.io.BufferedReader generateEmailTemplateStream(String templateType, String templateName,
                                                               String existingSubject, String existingContent,
                                                               String customPrompt) throws Exception {
        String prompt = buildEmailTemplatePrompt(templateType, templateName, existingSubject, existingContent, customPrompt);
        return callAiApiStream(prompt, "你是一个专业的邮件模板编写助手，只返回严格的 JSON 格式数据，不返回任何额外内容。");
    }

    /**
     * 构建邮件模板生成提示词（非流式和流式共用）
     * 自动查询同类型已有模板作为风格参考
     */
    private String buildEmailTemplatePrompt(String templateType, String templateName,
                                            String existingSubject, String existingContent,
                                            String customPrompt) {
        String typeLabel;
        switch (templateType) {
            case "REGISTER": typeLabel = "注册验证"; break;
            case "RESET_PASSWORD": typeLabel = "重置密码"; break;
            case "PASSWORD_CHANGED": typeLabel = "密码已修改"; break;
            default: typeLabel = templateType;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的邮件模板编写助手。请为以下场景生成一封邮件模板。\n\n");
        prompt.append("邮件场景：").append(typeLabel).append("\n");
        if (templateName != null && !templateName.isBlank()) {
            prompt.append("模板名称：").append(templateName).append("\n");
        }
        prompt.append("\n");
        prompt.append("要求：\n");
        prompt.append("1. 邮件主题简洁明了，包含系统名称【Mock Server】前缀\n");
        prompt.append("2. 邮件内容使用 HTML 格式，风格专业友好\n");
        prompt.append("3. 内容中可以使用以下占位符变量（按场景选择合适的使用）：\n");
        prompt.append("   - {{username}} - 用户名\n");
        prompt.append("   - {{email}} - 用户邮箱\n");
        prompt.append("   - {{time}} - 发送时间\n");
        prompt.append("   - {{siteUrl}} - 系统地址\n");
        prompt.append("   - {{code}} - 验证码\n");
        prompt.append("   - {{password}} / {{newPassword}} - 新密码\n");
        prompt.append("4. 根据场景选择最合适的占位符（注册场景用 {{code}}，密码场景用 {{password}} 或 {{newPassword}}）\n");
        prompt.append("5. 返回严格的 JSON 格式：{\"subject\":\"邮件主题\",\"content\":\"HTML邮件内容\"}\n");
        prompt.append("6. 不要包含 markdown 代码块标记，不要包含任何解释文字\n");

        if (existingSubject != null && !existingSubject.isBlank()) {
            prompt.append("\n参考主题：").append(existingSubject).append("\n");
        }
        if (existingContent != null && !existingContent.isBlank()) {
            prompt.append("参考内容：").append(existingContent).append("\n");
        }

        // 查询同类型已有模板作为风格参考（排除当前正在编辑的模板）
        try {
            List<EmailTemplate> existingTemplates = emailTemplateRepository.findByType(templateType);
            if (existingTemplates != null && !existingTemplates.isEmpty()) {
                // 过滤掉当前正在编辑的模板（如果提供了 existingSubject/existingContent）
                List<EmailTemplate> referenceTemplates = existingTemplates.stream()
                        .filter(t -> {
                            if (existingSubject != null && existingSubject.equals(t.getSubject())) return false;
                            if (existingContent != null && existingContent.equals(t.getContent())) return false;
                            return true;
                        })
                        .collect(java.util.stream.Collectors.toList());

                if (!referenceTemplates.isEmpty()) {
                    prompt.append("\n【同类型已有模板参考】\n");
                    prompt.append("以下是系统中已有的同类型（").append(typeLabel).append("）邮件模板，请参考其风格、结构和排版方式：\n\n");
                    int count = 0;
                    for (EmailTemplate t : referenceTemplates) {
                        if (count >= 2) break; // 最多参考2个，避免 prompt 过长
                        prompt.append("--- 参考模板 ").append(count + 1).append("：").append(t.getName()).append(" ---\n");
                        prompt.append("主题：").append(t.getSubject()).append("\n");
                        String refContent = t.getContent();
                        if (refContent != null && refContent.length() > 1500) {
                            refContent = refContent.substring(0, 1500) + "\n...(内容过长，已截断)";
                        }
                        prompt.append("内容：").append(refContent).append("\n\n");
                        count++;
                    }
                    prompt.append("请参考以上模板的风格和结构，生成风格一致但内容不同的新模板。\n");
                }
            }
        } catch (Exception e) {
            log.warn("查询同类型邮件模板参考失败，跳过: {}", e.getMessage());
        }

        // 用户自定义提示词（附加约束）
        if (customPrompt != null && !customPrompt.isBlank()) {
            prompt.append("\n【用户额外要求】请特别遵循以下指示：\n");
            prompt.append(customPrompt.trim()).append("\n");
        }

        prompt.append("\n请在参考基础上改进优化，生成更专业的邮件模板。");
        return prompt.toString();
    }

    /** 转换器类型到系统默认模板名称的映射 */
    private static final Map<String, String> TRANSFORMER_TEMPLATE_NAMES = new LinkedHashMap<>();
    static {
        TRANSFORMER_TEMPLATE_NAMES.put("response_wrapping", "【系统】标准响应包装器");
        TRANSFORMER_TEMPLATE_NAMES.put("data_masking", "【系统】数据脱敏处理器");
        TRANSFORMER_TEMPLATE_NAMES.put("field_transform", "【系统】字段转换器");
        TRANSFORMER_TEMPLATE_NAMES.put("conditional_response", "【系统】条件响应处理器");
        TRANSFORMER_TEMPLATE_NAMES.put("logging", "【系统】日志记录器");
        TRANSFORMER_TEMPLATE_NAMES.put("http_forward", "【系统】HttpClient请求转发器");
    }

    /**
     * AI 生成代码模板（CustomResponseTransformer Java 源码）
     *
     * @param apiMethod      接口请求方法 (GET/POST/PUT/DELETE/PATCH)
     * @param apiPath        接口路径
     * @param apiName        接口名称
     * @param description    接口描述（可选）
     * @param transformerType 转换器类型（如 response_wrapping, data_masking, field_transform, conditional_response, logging）
     * @param existingSourceCode 已有源码（可为null，用于AI参考优化）
     * @return 生成的 Java 源代码字符串
     */
    public String generateCodeTemplate(String apiMethod, String apiPath, String apiName,
                                        String description, String transformerType, String existingSourceCode) {
        AiConfig config = aiConfigService.getEnabledConfig();
        if (config == null) {
            throw new RuntimeException("未启用任何 AI 服务商");
        }
        if (config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new RuntimeException("AI 服务商 API 地址未配置");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new RuntimeException("AI 服务商 API Key 未配置");
        }

        String effectiveType = (transformerType != null && !transformerType.isBlank()) ? transformerType : "response_wrapping";
        String promptStr = buildCodeTemplatePrompt(apiMethod, apiPath, apiName, description, transformerType, existingSourceCode);
        log.debug("AI 代码模板 Prompt: {}", promptStr);

        try {
            String responseJson = callAiApi(config, promptStr);
            log.info("AI 代码模板 API 响应长度: {}", responseJson != null ? responseJson.length() : 0);

            String content = extractTextContent(responseJson);
            log.info("AI 代码模板原始响应长度: {}", content != null ? content.length() : 0);

            // 清理 markdown 代码块标记
            content = cleanCodeBlock(content);
            log.info("AI 代码模板清理后长度: {}", content != null ? content.length() : 0);

            // 自动补充缺失的必要 import
            content = fixImports(content, effectiveType);
            log.info("AI 代码模板补充 import 后长度: {}", content != null ? content.length() : 0);

            // 自动修复类型转换问题（getParams() 返回 Map<String, Object>，直接调用 String 方法会编译失败）
            content = fixTypeConversions(content);
            log.info("AI 代码模板类型修复后长度: {}", content != null ? content.length() : 0);

            return content;
        } catch (Exception e) {
            log.error("AI 生成代码模板失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI 生成代码模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * AI 生成代码模板（流式 SSE）
     */
    public java.io.BufferedReader generateCodeTemplateStream(String apiMethod, String apiPath, String apiName,
                                                               String description, String transformerType,
                                                               String existingSourceCode) throws Exception {
        String prompt = buildCodeTemplatePrompt(apiMethod, apiPath, apiName, description, transformerType, existingSourceCode);
        return callAiApiStream(prompt, "你是一个专业的 Java 代码生成助手，只返回纯 Java 源代码，不包含 markdown 标记或解释文字。");
    }

    /**
     * 构建代码模板生成提示词（非流式和流式共用）
     */
    private String buildCodeTemplatePrompt(String apiMethod, String apiPath, String apiName,
                                           String description, String transformerType, String existingSourceCode) {
        String effectiveType = (transformerType != null && !transformerType.isBlank()) ? transformerType : "response_wrapping";

        String typeLabel;
        switch (effectiveType) {
            case "response_wrapping": typeLabel = "响应包装 - 将原始响应包装为统一格式 {code, message, data, timestamp}"; break;
            case "data_masking": typeLabel = "数据脱敏 - 对响应中的手机号、邮箱、身份证等敏感字段进行脱敏处理"; break;
            case "field_transform": typeLabel = "字段转换 - 对响应字段进行重命名、类型转换、值映射等操作"; break;
            case "conditional_response": typeLabel = "条件响应 - 根据请求参数返回不同的响应数据"; break;
            case "logging": typeLabel = "日志记录 - 记录请求和响应的详细信息"; break;
            case "http_forward": typeLabel = "请求转发 - 使用HttpClient将请求转发到真实后端服务"; break;
            default: typeLabel = effectiveType;
        }

        // 查找对应类型的系统默认模板作为参考
        String systemTemplateCode = null;
        String systemTemplateName = TRANSFORMER_TEMPLATE_NAMES.get(effectiveType);
        if (systemTemplateName != null) {
            List<CustomCodeTemplate> systemTemplates = codeTemplateRepository.findByIsSystemTrue();
            for (CustomCodeTemplate t : systemTemplates) {
                if (systemTemplateName.equals(t.getName()) && t.getSourceCode() != null) {
                    systemTemplateCode = t.getSourceCode();
                    break;
                }
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的 Java 代码生成助手。请为以下 API 接口生成一个 CustomResponseTransformer 实现类。\n\n");
        prompt.append("接口信息：\n");
        prompt.append("- 接口名称：").append(apiName != null ? apiName : "未命名接口").append("\n");
        prompt.append("- 请求方法：").append(apiMethod).append("\n");
        prompt.append("- 接口路径：").append(apiPath).append("\n");
        if (description != null && !description.isBlank()) {
            prompt.append("- 接口描述：").append(description).append("\n");
        }
        prompt.append("\n转换器类型：").append(typeLabel).append("\n\n");

        prompt.append("接口契约（必须严格遵守）：\n");
        prompt.append("```java\n");
        prompt.append("public interface CustomResponseTransformer {\n");
        prompt.append("    MockResponseDTO transform(MockResponseDTO mockResponse, MockRequest mockRequest, String apiName, String apiPath);\n");
        prompt.append("    String getDescription();\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("可用 DTO 字段：\n");
        prompt.append("- MockResponseDTO: getStatusCode()(int), getHeaders()(Map), getBody()(Object), getDelay()(Long)\n");
        prompt.append("- MockRequest: getPath()(String), getMethod()(String), getHeaders()(Map), getParams()(Map), getBody()(String), getProjectCode()(String), getPathParams()(Map)\n");
        prompt.append("- MockResponseDTO.builder() 支持链式构建: .statusCode(), .headers(), .body(), .delay(), .build()\n\n");

        if (systemTemplateCode != null && !systemTemplateCode.isBlank()) {
            prompt.append("【系统默认参考模板 - ").append(typeLabel).append("】\n");
            prompt.append("以下是该类型转换器的系统默认实现，请参考其代码结构、import语句、命名规范和实现模式：\n");
            prompt.append("```java\n").append(systemTemplateCode).append("\n```\n\n");
            prompt.append("请基于以上系统默认模板的风格和结构，根据接口信息生成适配的代码。\n");
        }

        prompt.append("代码要求：\n");
        prompt.append("1. 必须实现 CustomResponseTransformer 接口的两个方法\n");
        prompt.append("2. 【重要】必须在文件顶部包含所有需要的 import 语句\n");
        prompt.append("3. 类名使用驼峰命名，需与接口场景相关\n");
        prompt.append("4. transform() 方法中编写核心处理逻辑，必须返回有效的 MockResponseDTO 对象\n");
        prompt.append("5. getDescription() 返回简短的中文描述\n");
        prompt.append("6. 包含完整的 Javadoc 注释\n");
        prompt.append("7. 使用 MockResponseDTO.builder() 构建返回对象\n");
        prompt.append("8. mockRequest.getParams() 返回 Map<String, Object>，get() 返回 Object，调用 String 方法前必须 .toString()\n\n");

        prompt.append("根据转换器类型，实现相应逻辑：\n");
        prompt.append("- response_wrapping: 将 body 包装为 {code, message, data, timestamp}\n");
        prompt.append("- data_masking: 对 JSON 响应中的 phone、email、idCard 等字段进行脱敏\n");
        prompt.append("- field_transform: 对字段名进行驼峰/下划线转换，或值映射\n");
        prompt.append("- conditional_response: 根据 mockRequest.getParams() 或 getHeaders() 返回不同响应\n");
        prompt.append("- logging: 使用 System.out.println 记录请求路径、方法、参数和响应信息\n");
        prompt.append("- http_forward: 使用 java.net.http.HttpClient 将请求转发到真实后端服务\n\n");

        if (existingSourceCode != null && !existingSourceCode.isBlank()) {
            prompt.append("参考现有代码（请在此基础上改进优化）：\n");
            prompt.append("```java\n").append(existingSourceCode).append("\n```\n\n");
            prompt.append("请在参考基础上改进优化，生成更完善的代码模板。\n");
        }

        prompt.append("重要：只返回纯 Java 源代码，不要包含 markdown 代码块标记（```java 或 ```），不要包含任何解释文字。");
        return prompt.toString();
    }

    /**
     * 清理 AI 响应中的 Java 代码块标记
     */
    private String cleanCodeBlock(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        content = content.trim();

        // 移除开头的 markdown 代码块标记
        if (content.startsWith("```java")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        // 移除结尾的 markdown 代码块标记
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    /**
     * 自动补充 AI 生成代码中缺失的必要 import 语句
     * <p>
     * 检查源码中使用的类和包，自动添加缺失的 import。
     * 只补充安全的 import（已在白名单中的包）。
     * </p>
     */
    private String fixImports(String sourceCode, String transformerType) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return sourceCode;
        }

        // 必要的基础 import（始终需要）
        List<String> requiredImports = new ArrayList<>();
        requiredImports.add("import com.carolcoral.mockserver.dto.MockRequest;");
        requiredImports.add("import com.carolcoral.mockserver.dto.MockResponseDTO;");
        requiredImports.add("import com.carolcoral.mockserver.plugin.CustomResponseTransformer;");
        requiredImports.add("import java.util.*;");

        // 检查源码中是否使用了 fastjson
        if (sourceCode.contains("JSON.") || sourceCode.contains("JSONObject") || sourceCode.contains("JSONArray")) {
            if (!sourceCode.contains("import com.alibaba.fastjson.JSON;")) {
                requiredImports.add("import com.alibaba.fastjson.JSON;");
            }
            if (sourceCode.contains("JSONObject") && !sourceCode.contains("import com.alibaba.fastjson.JSONObject;")) {
                requiredImports.add("import com.alibaba.fastjson.JSONObject;");
            }
            if (sourceCode.contains("JSONArray") && !sourceCode.contains("import com.alibaba.fastjson.JSONArray;")) {
                requiredImports.add("import com.alibaba.fastjson.JSONArray;");
            }
        }

        // 检查是否需要 LinkedHashMap（不检查 java.util.* 因为已经包含）
        // LinkedHashMap 在 java.util.* 中，不需要单独 import

        // http_forward 类型需要的额外 import
        if ("http_forward".equals(transformerType)) {
            if (sourceCode.contains("URI") && !sourceCode.contains("import java.net.URI;")) {
                requiredImports.add("import java.net.URI;");
            }
            if (sourceCode.contains("HttpClient") && !sourceCode.contains("import java.net.http.HttpClient;")) {
                requiredImports.add("import java.net.http.HttpClient;");
            }
            if (sourceCode.contains("HttpRequest") && !sourceCode.contains("import java.net.http.HttpRequest;")) {
                requiredImports.add("import java.net.http.HttpRequest;");
            }
            if (sourceCode.contains("HttpResponse") && !sourceCode.contains("import java.net.http.HttpResponse;")) {
                requiredImports.add("import java.net.http.HttpResponse;");
            }
            if (sourceCode.contains("Duration") && !sourceCode.contains("import java.time.Duration;")) {
                requiredImports.add("import java.time.Duration;");
            }
        }

        // 收集源码中已有的 import 行
        List<String> existingImports = new ArrayList<>();
        StringBuilder beforeImports = new StringBuilder();
        StringBuilder afterImports = new StringBuilder();
        boolean inImportSection = false;
        boolean importSectionEnded = false;

        for (String line : sourceCode.split("\n")) {
            String trimmed = line.trim();
            if (!importSectionEnded && (trimmed.startsWith("import ") || trimmed.startsWith("//"))) {
                inImportSection = true;
                if (trimmed.startsWith("import ")) {
                    existingImports.add(trimmed);
                }
            } else if (inImportSection && !trimmed.startsWith("import ") && !trimmed.isEmpty() && !trimmed.startsWith("//")) {
                // import 段结束，后续内容进入 afterImports
                inImportSection = false;
                importSectionEnded = true;
            }

            if (importSectionEnded) {
                if (afterImports.length() > 0) afterImports.append("\n");
                afterImports.append(line);
            } else if (!inImportSection) {
                if (beforeImports.length() > 0) beforeImports.append("\n");
                beforeImports.append(line);
            }
        }

        // 如果源码中没有 import 段（beforeImports 包含所有内容），需要找到 package 和类定义之间插入
        if (existingImports.isEmpty()) {
            // 找到 package 语句后、类/注释开始前的位置插入 import
            StringBuilder result = new StringBuilder();
            boolean importsInserted = false;
            for (String line : sourceCode.split("\n")) {
                result.append(line).append("\n");
                String trimmed = line.trim();
                // 在 package 语句后插入 import
                if (!importsInserted && trimmed.startsWith("package ")) {
                    // package 语句后就是插入 import 的最佳位置
                    // 继续，等遇到非空非注释行时插入
                }
                if (!importsInserted && !trimmed.startsWith("package ") && !trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("import ")) {
                    // 找到第一个非 package 非注释非 import 行之前插入
                    // 在 result 中回退一行，插入 imports
                    result.setLength(result.length() - line.length() - 1);
                    for (String imp : requiredImports) {
                        result.append(imp).append("\n");
                    }
                    result.append("\n");
                    result.append(line).append("\n");
                    importsInserted = true;
                }
            }
            if (!importsInserted) {
                // 没有找到合适位置，在开头插入
                for (String imp : requiredImports) {
                    result.insert(0, imp + "\n");
                }
                result.insert(0, "\n");
            }
            return result.toString().trim();
        }

        // 有现有 import，补充缺失的
        StringBuilder result = new StringBuilder();
        result.append(beforeImports).append("\n");

        // 输出所有需要的 import（跳过已存在的）
        for (String imp : requiredImports) {
            boolean alreadyExists = existingImports.stream()
                    .anyMatch(e -> e.equals(imp) || e.startsWith(imp.substring(0, imp.indexOf(';'))));
            if (!alreadyExists) {
                result.append(imp).append("\n");
                log.info("AI 代码模板 - 自动补充 import: {}", imp);
            }
        }
        // 输出已有的 import
        for (String imp : existingImports) {
            result.append(imp).append("\n");
        }
        result.append("\n");
        result.append(afterImports);

        return result.toString().trim();
    }

    /**
     * 自动修复 AI 生成代码中的类型转换问题。
     * MockRequest.getParams() 返回 Map&lt;String, Object&gt;，AI 经常直接对 value 调用 String 方法（如 trim()）或
     * 将 value 直接传给 JSON.parseObject()，导致编译失败。
     * 此方法自动插入 toString() 包装。
     */
    private String fixTypeConversions(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return sourceCode;
        }

        String result = sourceCode;

        // 核心正则：匹配 getParams().get("...") 后直接跟 String 专有方法调用（而不是 .toString()）
        // 组1: getParams().get("key") 整体
        // 组2: 后面跟的方法名（如 trim, split, substring 等）
        // 修复为: getParams().get("key").toString().方法名(...)
        String getParamsGet = "(getParams\\s*\\(\\s*\\)\\s*\\.\\s*get\\s*\\(\\s*\"[^\"]*\"\\s*\\))";

        // 模式1: .trim() — 最常见
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*trim\\s*\\(\\s*\\)",
            "$1.toString().trim()"
        );

        // 模式2: .split(...)
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*split\\s*\\(",
            "$1.toString().split("
        );

        // 模式3: .substring(...)
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*substring\\s*\\(",
            "$1.toString().substring("
        );

        // 模式4: .replace / .replaceAll / .replaceFirst
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*replace(All|First)?\\s*\\(",
            "$1.toString().replace$2("
        );

        // 模式5: .toLowerCase() / .toUpperCase()
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*to(Lower|Upper)Case\\s*\\(\\s*\\)",
            "$1.toString().to$2Case()"
        );

        // 模式6: .contains(...) / .startsWith(...) / .endsWith(...) / .indexOf(...) / .lastIndexOf(...)
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*(contains|startsWith|endsWith|indexOf|lastIndexOf)\\s*\\(",
            "$1.toString().$2("
        );

        // 模式7: .charAt(...)
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*charAt\\s*\\(",
            "$1.toString().charAt("
        );

        // 模式8: .matches(...)
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*matches\\s*\\(",
            "$1.toString().matches("
        );

        // 模式9: .isEmpty() / .isBlank()
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*is(Empty|Blank)\\s*\\(\\s*\\)",
            "$1.toString().is$2()"
        );

        // 模式10: .length()
        result = result.replaceAll(
            getParamsGet + "\\s*\\.\\s*length\\s*\\(\\s*\\)",
            "$1.toString().length()"
        );

        // 模式11: JSON.parseObject(getParams().get("key")) 直接作为参数
        // 匹配 parseObject(...getParams().get("key")...) — 修复为 .toString()
        result = result.replaceAll(
            "(parseObject|parseArray)\\s*\\(\\s*" + getParamsGet + "\\s*\\)",
            "$1($2.toString())"
        );

        // 模式12: 变量赋值给局部变量后未显式转型 — String xxx = request.getParams().get("key");
        //   → String xxx = request.getParams().get("key").toString();
        // 注意：只匹配 String 类型的声明，不匹配 Object/var 类型
        result = result.replaceAll(
            "String\\s+(\\w+)\\s*=\\s*" + getParamsGet + "\\s*;",
            "String $1 = $2.toString();"
        );

        if (!result.equals(sourceCode)) {
            log.info("AI 代码模板 - 自动修复类型转换");
        }

        return result;
    }

    private String buildPrompt(String method, String path, String name, String description, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 API Mock 数据生成助手。请为以下 API 接口生成 ").append(count).append(" 个不同的 Mock 响应数据。\n\n");
        sb.append("接口信息：\n");
        sb.append("- 接口名称：").append(name != null ? name : "未命名接口").append("\n");
        sb.append("- 请求方法：").append(method).append("\n");
        sb.append("- 接口路径：").append(path).append("\n");
        if (description != null && !description.isBlank()) {
            sb.append("- 接口描述：").append(description).append("\n");
        }
        sb.append("\n");

        // 根据路径和名称推测业务场景
        sb.append("要求：\n");
        sb.append("1. 每个响应包含 statusCode（HTTP状态码）、contentType（固定为 application/json）、responseBody（JSON格式的响应体）、conditionDesc（该响应的使用场景描述，10字以内）\n");
        sb.append("2. 第1个必须是成功响应（200），响应体要符合接口名称和路径所暗示的业务场景，数据要逼真\n");
        sb.append("3. 其余响应可以是不同的业务场景（如参数错误400、未找到404、未授权401、服务器错误500等）\n");
        sb.append("4. 响应体必须使用中文内容，字段名使用 camelCase 英文\n");
        sb.append("5. 只返回纯 JSON 数组格式，不要包含 markdown 代码块标记，不要包含任何解释文字\n\n");

        sb.append("返回格式示例：\n");
        sb.append("[\n");
        sb.append("  {\"statusCode\":200,\"contentType\":\"application/json\",\"responseBody\":\"{\\\"code\\\":200,\\\"message\\\":\\\"操作成功\\\",\\\"data\\\":{...}}\",\"conditionDesc\":\"正常响应\"},\n");
        sb.append("  {\"statusCode\":400,\"contentType\":\"application/json\",\"responseBody\":\"{\\\"code\\\":400,\\\"message\\\":\\\"参数错误\\\"}\",\"conditionDesc\":\"参数错误\"}\n");
        sb.append("]");

        return sb.toString();
    }

    /**
     * 调用 OpenAI 兼容 API
     */
    private String callAiApi(AiConfig config, String prompt) throws Exception {
        String apiUrl = config.getApiUrl();
        // 标准化 API 地址并构建 chat/completions URL
        String chatUrl = AiConfigService.buildChatCompletionsUrl(apiUrl);

        String model = config.getDefaultModel();
        if (model == null || model.isBlank()) {
            model = "gpt-4o";
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个专业的 API Mock 数据生成助手，只返回要求的 JSON 格式数据，不返回任何额外内容。");
        messages.add(systemMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        if (config.getTemperature() != null) {
            requestBody.put("temperature", config.getTemperature());
        } else {
            requestBody.put("temperature", 0.7);
        }

        if (config.getMaxTokens() != null) {
            requestBody.put("max_tokens", config.getMaxTokens());
        } else {
            requestBody.put("max_tokens", 4096);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.info("AI API 请求: url={}, model={}", chatUrl, model);
        // debug: 打印请求体（隐藏 API Key）
        try {
            String reqBodyJson = objectMapper.writeValueAsString(requestBody);
            log.debug("AI API 请求体: {}", reqBodyJson.length() > 2000 ? reqBodyJson.substring(0, 2000) + "..." : reqBodyJson);
        } catch (Exception ignored) {}

        RestTemplate rt = getRestTemplate();
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = rt.exchange(chatUrl, HttpMethod.POST, entity, String.class);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("AI API 响应: status={}, bodyLength={}, elapsed={}ms",
                    response.getStatusCode().value(),
                    response.getBody() != null ? response.getBody().length() : 0,
                    elapsed);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // debug: 打印完整响应体
                String respBody = response.getBody();
                log.debug("AI API 响应体(前2000字符): {}", respBody.length() > 2000 ? respBody.substring(0, 2000) + "..." : respBody);
                return respBody;
            } else {
                String errorBody = response.getBody() != null ?
                        (response.getBody().length() > 500 ? response.getBody().substring(0, 500) : response.getBody()) : "";
                log.error("AI API 返回非 2xx 状态码: {}, body: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("AI API 返回非 2xx 状态码: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("AI API 网络连接失败: {}", e.getMessage());
            throw new RuntimeException("AI API 连接失败，请检查网络和 API 地址: " + e.getMessage(), e);
        }
    }

    /**
     * 清理 AI 响应中的 markdown 代码块标记，提取纯 JSON 内容
     */
    private String cleanMarkdownJson(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        content = content.trim();

        // 查找第一个 { 和最后一个 }，提取 JSON 对象
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }

        // 清理 markdown 代码块标记（可能仍有残留）
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    /**
     * 从 OpenAI 格式响应中提取文本内容
     */
    private String extractTextContent(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null) {
                JsonNode content = message.get("content");
                if (content != null) {
                    String text = content.asText();
                    if (text != null) {
                        return text.trim();
                    }
                }
            }
        }
        // 打印原始响应以便调试
        String preview = responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson;
        log.error("无法解析 AI 响应，原始响应预览: {}", preview);
        throw new RuntimeException("无法解析 AI 响应，响应格式不符合预期");
    }

    /**
     * 解析 AI 生成的 Mock 响应列表
     */
    private List<Map<String, Object>> parseResponse(String responseJson, int expectedCount) throws Exception {
        String content = extractTextContent(responseJson);
        log.debug("AI 原始响应内容: {}", content);

        // 尝试清理可能的 markdown 代码块标记
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        content = content.trim();

        JsonNode root = objectMapper.readTree(content);
        if (!root.isArray()) {
            throw new RuntimeException("AI 返回的数据格式不是 JSON 数组");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < root.size() && results.size() < expectedCount; i++) {
            JsonNode item = root.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("statusCode", item.has("statusCode") ? item.get("statusCode").asInt() : 200);
            map.put("contentType", item.has("contentType") ? item.get("contentType").asText() : "application/json");
            map.put("responseBody", item.has("responseBody") ? item.get("responseBody").asText() : "");
            map.put("conditionDesc", item.has("conditionDesc") ? item.get("conditionDesc").asText() : "");
            results.add(map);
        }

        return results;
    }

    /**
     * 通用 AI 对话（支持多轮上下文）
     *
     * @param messages 对话消息列表，每条包含 role 和 content
     * @return AI 回复的文本内容
     */
    public String chat(List<Map<String, String>> messages) throws Exception {
        return chat(messages, null, null);
    }

    /**
     * 通用 AI 对话（支持指定 configId 和 model）
     */
    public String chat(List<Map<String, String>> messages, Long configId, String model) throws Exception {
        AiConfig config = resolveConfig(configId);
        if (config == null) {
            throw new RuntimeException("未配置 AI 服务或未启用，请先在 AI 设置中配置并启用");
        }

        // 注入系统提示词（含项目文档知识），确保 AI 回答基于真实项目内容
        // 系统提示词不随 model 变化而变化，保证回答内容一致性
        List<Map<String, String>> enrichedMessages = enrichWithSystemPrompt(messages);

        String apiUrl = config.getApiUrl();
        String chatUrl = buildChatUrl(apiUrl);
        String effectiveModel = (model != null && !model.isBlank()) ? model : getEffectiveModel(config);

        Map<String, Object> requestBody = buildChatRequestBody(config, effectiveModel, enrichedMessages, false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = getRestTemplate();

        log.info("AI Chat 请求: url={}, model={}, messagesCount={}", chatUrl, effectiveModel, messages.size());
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(chatUrl, HttpMethod.POST, entity, String.class);
            long elapsed = System.currentTimeMillis() - startTime;

            log.info("AI Chat 响应: status={}, bodyLength={}, elapsed={}ms",
                    response.getStatusCode().value(),
                    response.getBody() != null ? response.getBody().length() : 0,
                    elapsed);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractTextContent(response.getBody());
            } else {
                String errorBody = response.getBody() != null ?
                        (response.getBody().length() > 500 ? response.getBody().substring(0, 500) : response.getBody()) : "";
                log.error("AI Chat API 返回非 2xx 状态码: {}, body: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("AI Chat API 返回错误: HTTP " + response.getStatusCode() +
                        (errorBody.isEmpty() ? "" : " - " + errorBody));
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("AI Chat 网络连接失败: {}", e.getMessage());
            throw new RuntimeException("AI 对话连接失败，请检查网络和 AI 服务配置: " + e.getMessage(), e);
        }
    }

    /**
     * 通用流式 AI 调用（通过提示词生成，SSE 逐 token 返回）
     * 复用于邮件模板、代码模板、接口描述等生成场景，避免超时问题
     *
     * @param prompt 提示词
     * @param systemPrompt 系统提示词（可为null）
     * @return 逐行 SSE 数据的 BufferedReader，调用方负责关闭
     */
    public java.io.BufferedReader callAiApiStream(String prompt, String systemPrompt) throws Exception {
        return callAiApiStream(prompt, systemPrompt, null);
    }

    /**
     * 通用流式 AI 调用（支持指定 configId 切换模型）
     */
    public java.io.BufferedReader callAiApiStream(String prompt, String systemPrompt, Long configId) throws Exception {
        AiConfig config = resolveConfig(configId);
        if (config == null) {
            throw new RuntimeException("未配置 AI 服务或未启用，请先在 AI 设置中配置并启用");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt != null ? systemPrompt : "你是一个专业的 API Mock 数据生成助手，只返回要求的 JSON 格式数据，不返回任何额外内容。");
        messages.add(sysMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        return doStreamRequest(config, messages);
    }

    /**
     * 执行流式请求（公共逻辑）
     */
    private java.io.BufferedReader doStreamRequest(AiConfig config, List<Map<String, String>> messages) throws Exception {
        return doStreamRequest(config, messages, null);
    }

    /**
     * 执行流式请求（支持 model 覆盖）
     */
    private java.io.BufferedReader doStreamRequest(AiConfig config, List<Map<String, String>> messages, String modelOverride) throws Exception {
        String chatUrl = buildChatUrl(config.getApiUrl());
        String model = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : getEffectiveModel(config);
        Map<String, Object> requestBody = buildChatRequestBody(config, model, messages, true);

        java.net.URL url = new java.net.URL(chatUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        int streamReadTimeout = config.getTimeout() != null && config.getTimeout() > 0 ? config.getTimeout() : 900;
        conn.setReadTimeout(streamReadTimeout * 1000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        conn.setRequestProperty("Accept", "text/event-stream");

        String reqBodyJson = objectMapper.writeValueAsString(requestBody);
        log.info("AI Stream 请求: url={}, model={}, messagesCount={}", chatUrl, model, messages.size());
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(reqBodyJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            String errorBody = "";
            try (InputStream errStream = conn.getErrorStream()) {
                if (errStream != null) {
                    errorBody = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));
                }
            }
            conn.disconnect();
            log.error("AI Stream API 返回非 2xx 状态码: {}, body: {}", status,
                    errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody);
            throw new RuntimeException("AI API 返回错误: HTTP " + status +
                    (errorBody.isEmpty() ? "" : " - " + errorBody));
        }

        return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 流式 AI 对话（SSE 逐 token 返回，实时加载）
     *
     * @param messages 对话消息列表
     * @return 逐行 SSE 数据的 BufferedReader，调用方负责关闭
     */
    public java.io.BufferedReader chatStream(List<Map<String, String>> messages) throws Exception {
        return chatStream(messages, null, null);
    }

    /**
     * 流式 AI 对话（支持指定 configId 和 model）
     *
     * @param messages 对话消息列表
     * @param configId 指定 AI 配置 ID（为 null 则使用默认配置）
     * @param model 指定模型名称（为 null 则使用配置的默认模型）
     * @return 逐行 SSE 数据的 BufferedReader，调用方负责关闭
     */
    public java.io.BufferedReader chatStream(List<Map<String, String>> messages, Long configId, String model) throws Exception {
        AiConfig config = resolveConfig(configId);
        if (config == null) {
            throw new RuntimeException("未配置 AI 服务或未启用，请先在 AI 设置中配置并启用");
        }

        // 注入系统提示词（含项目文档知识），确保 AI 回答基于真实项目内容
        // 系统提示词不随 model 变化而变化，保证回答内容一致性
        List<Map<String, String>> enrichedMessages = enrichWithSystemPrompt(messages);

        String effectiveModel = (model != null && !model.isBlank()) ? model : getEffectiveModel(config);
        log.info("AI Chat Stream 请求: configId={}, model={}, messagesCount={}", configId, effectiveModel, enrichedMessages.size());
        return doStreamRequest(config, enrichedMessages, effectiveModel);
    }

    /**
     * 根据 configId 解析 AI 配置，为 null 时返回默认配置
     */
    private AiConfig resolveConfig(Long configId) {
        if (configId != null) {
            AiConfig config = aiConfigService.getById(configId);
            if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                return config;
            }
        }
        return aiConfigService.getEnabledConfig();
    }

    /**
     * 构建 chat/completions URL
     */
    private String buildChatUrl(String apiUrl) {
        return AiConfigService.buildChatCompletionsUrl(apiUrl);
    }

    /**
     * 获取有效模型名称
     */
    private String getEffectiveModel(AiConfig config) {
        String model = config.getDefaultModel();
        return (model != null && !model.isBlank()) ? model : "gpt-4o";
    }

    /**
     * 在消息列表最前面注入系统提示词（含项目文档知识），
     * 先根据用户提问从 README + CHANGELOG + 使用说明中检索相关内容；
     * 若检索命中则优先使用文档上下文回答，未命中则允许 AI 使用通用知识生成回答，不直接阻断。
     * 如果消息列表中已包含 system 角色消息，则不重复注入。
     */
    private List<Map<String, String>> enrichWithSystemPrompt(List<Map<String, String>> messages) {
        // 检查是否已有 system 消息，避免重复注入
        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                return messages; // 已存在，不重复注入
            }
        }

        // 取最后一条用户消息作为当前提问
        String currentQuestion = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, String> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) {
                currentQuestion = msg.getOrDefault("content", "");
                break;
            }
        }

        List<Map<String, String>> enriched = new ArrayList<>();
        Map<String, String> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", buildSystemPrompt(currentQuestion));
        enriched.add(sysMsg);
        enriched.addAll(messages);
        return enriched;
    }

    /**
     * 构建 chat 请求体
     */
    private Map<String, Object> buildChatRequestBody(AiConfig config, String model,
                                                      List<Map<String, String>> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);

        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature());
        } else {
            body.put("temperature", 0.7);
        }

        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        } else {
            body.put("max_tokens", 4096);
        }

        return body;
    }

    // ==================== 建议问题（基于 README + CHANGELOG 生成并缓存） ====================

    /** 缓存版本标识（基于版本号），用于检测是否需要重新生成 */
    private String cachedVersion = null;
    /** 缓存的建议问题列表 */
    private List<String> cachedSuggestions = null;

    /**
     * 构建 AI 对话的系统提示词。
     * 先从 README + CHANGELOG + 使用说明中检索与问题相关的段落；
     * 若检索命中则注入项目文档上下文并要求优先基于文档回答，未命中时允许 AI 使用通用知识回答，避免直接阻断。
     *
     * @param question 用户当前提问（空字符串表示无特定问题，构建通用提示词）
     * @return 系统提示词
     */
    private String buildSystemPrompt(String question) {
        String readme = readStaticFile("README.md");
        String changelog = readStaticFile("CHANGELOG.md");
        String guide = readStaticFile("USER_GUIDE.md");

        StringBuilder context = new StringBuilder();
        boolean hasContext = false;

        if (readme != null && !readme.isBlank()) {
            String snippet = extractRelevantSections(readme, question, 1200);
            if (!snippet.isBlank()) {
                context.append("=== Mock Server 系统说明 ===\n").append(snippet).append("\n\n");
                hasContext = true;
            }
        }

        if (changelog != null && !changelog.isBlank()) {
            String snippet = extractRelevantSections(changelog, question, 800);
            if (!snippet.isBlank()) {
                context.append("=== 最新版本变更 ===\n").append(snippet).append("\n\n");
                hasContext = true;
            }
        }

        if (guide != null && !guide.isBlank()) {
            String snippet = extractRelevantSections(guide, question, 800);
            if (!snippet.isBlank()) {
                context.append("=== 使用说明 ===\n").append(snippet).append("\n\n");
                hasContext = true;
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 Mock Server 的智能助手。\n\n");

        if (hasContext) {
            prompt.append("请优先根据以下项目文档回答用户问题。如果文档中包含明确信息，请引用具体文档内容；")
                  .append("如果文档中未涉及用户提问，请使用你的通用知识为用户提供有帮助的回答，不要直接拒绝。\n\n");
            prompt.append(context);
        } else {
            prompt.append("我将为你回答关于 Mock Server 或相关技术的问题。请基于你的知识库提供准确、简洁的回答，优先使用中文。\n");
        }

        prompt.append("=== 回答规则 ===\n");
        prompt.append("1. 回答简洁清晰，优先使用中文\n");
        prompt.append("2. 如果项目文档中有明确描述，请引用具体的使用方法\n");
        prompt.append("3. 如果问题未被项目文档覆盖，请使用你的通用知识回答，不要直接拒绝或阻断\n");
        prompt.append("4. 如果用户提问与 Mock Server 完全无关，可以简短回应但不要过度展开\n");
        prompt.append("5. 代码示例优先使用文档中的真实示例\n");

        return prompt.toString();
    }

    /**
     * 从文档中提取与用户问题相关的段落。
     * 基于关键词匹配，返回包含关键词的段落及其前后上下文，长度不超过 maxLength。
     *
     * @param doc        文档内容
     * @param question   用户提问
     * @param maxLength  最大返回字符数
     * @return 相关段落摘要
     */
    private String extractRelevantSections(String doc, String question, int maxLength) {
        if (doc == null || doc.isBlank()) {
            return "";
        }
        if (question == null || question.isBlank()) {
            return truncateText(doc, maxLength);
        }

        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            return truncateText(doc, maxLength);
        }

        String[] lines = doc.split("\\n");
        StringBuilder result = new StringBuilder();
        int charCount = 0;
        int contextWindow = 2; // 命中行前后各保留 2 行

        for (int i = 0; i < lines.length; i++) {
            String lowerLine = lines[i].toLowerCase();
            boolean matched = false;
            for (String keyword : keywords) {
                if (lowerLine.contains(keyword)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }

            int start = Math.max(0, i - contextWindow);
            int end = Math.min(lines.length, i + contextWindow + 1);
            for (int j = start; j < end && charCount < maxLength; j++) {
                String line = lines[j] + "\n";
                if (charCount + line.length() > maxLength) {
                    line = line.substring(0, maxLength - charCount);
                }
                result.append(line);
                charCount += line.length();
            }
            if (charCount < maxLength) {
                result.append("\n");
            }
        }

        return result.length() > 0 ? result.toString() : "";
    }

    /**
     * 从用户提问中提取关键词，用于文档检索。
     *
     * @param question 用户提问
     * @return 关键词列表
     */
    private List<String> extractKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        String[] tokens = question.toLowerCase().split("[^\\p{L}\\p{Nd}]+");
        for (String token : tokens) {
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * 从 README 提取核心信息：系统简介 + 功能特性表格
     */
    private String extractCoreInfo(String readme) {
        StringBuilder core = new StringBuilder();
        String[] lines = readme.split("\\n");
        boolean inFeatureSection = false;
        int featureLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            // 保留标题和简介行（跳过 badges/p 标签内的图片行）
            if (trimmed.startsWith("<h1") || trimmed.startsWith("Mock Server") && !trimmed.startsWith("Mock Server 有哪些")) {
                core.append(line.replaceAll("<[^>]+>", "")).append("\n");
                continue;
            }
            // 保留介绍段落（p align="center" 的文本行）
            if (trimmed.matches("^HTTP.*Mock.*$") || trimmed.matches("^Spring.*$")) {
                core.append(line.replaceAll("<[^>]+>", "")).append("\n");
                continue;
            }
            // 进入核心特性表格
            if (trimmed.contains("核心特性") || trimmed.contains("Core Features")) {
                inFeatureSection = true;
                core.append("\n## 核心功能模块\n\n");
                continue;
            }
            // 收集表格行
            if (inFeatureSection && trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (trimmed.matches("\\|[-: |]+\\|")) continue; // 跳过分隔行
                if (trimmed.contains("模块") || trimmed.contains("Module")) continue; // 跳过表头
                // 清理表格行：去掉 HTML 标签和图片标记，保留纯文本
                String cleaned = line.replaceAll("<[^>]+>", "");
                core.append(cleaned).append("\n");
                featureLines++;
                if (featureLines >= 15) break; // 最多收集15行
                continue;
            }
            // 表格结束后停止
            if (inFeatureSection && (trimmed.startsWith("##") || trimmed.startsWith("---"))
                    && featureLines > 0) {
                break;
            }
        }
        return core.toString();
    }

    /**
     * 从 CHANGELOG 提取最新版本的变更摘要（前两个版本）
     */
    private String extractLatestChanges(String changelog) {
        StringBuilder summary = new StringBuilder();
        String[] lines = changelog.split("\\n");
        int versionsFound = 0;
        boolean inVersion = false;

        for (String line : lines) {
            String trimmed = line.trim();
            // 找到版本标题
            if (trimmed.matches("^##\\s+v\\d+\\.\\d+.*")) {
                if (versionsFound >= 2) break; // 最多两个版本
                versionsFound++;
                inVersion = true;
                summary.append("\n").append(trimmed).append("\n");
                continue;
            }
            if (!inVersion) continue;
            // 停止条件
            if (versionsFound >= 2 && trimmed.matches("^##\\s+v\\d+\\.\\d+.*")) break;

            // 收集：版本描述行（> xxx）、三级标题、带粗体的列表项
            if (trimmed.startsWith("> ")) {
                summary.append(trimmed).append("\n");
            } else if (trimmed.startsWith("### ")) {
                summary.append(trimmed).append("\n");
            } else if (trimmed.startsWith("- **")) {
                summary.append(trimmed).append("\n");
            } else if (trimmed.equals("---") && versionsFound >= 2) {
                break;
            }

            // 限制总长度
            if (summary.length() > 3000) {
                summary.append("...\n");
                break;
            }
        }
        return summary.toString();
    }

    /**
     * 应用启动后异步预生成建议问题缓存，避免前端首次请求时长时间等待。
     */
    @PostConstruct
    public void initChatSuggestions() {
        new Thread(() -> {
            try {
                // 等待 Spring 完全初始化（延迟 5 秒确保 DB/Config 就绪）
                Thread.sleep(5000);
                log.info("开始预生成 AI 对话建议问题...");
                getChatSuggestions();
                log.info("AI 对话建议问题预生成完成，共 {} 条", 
                        cachedSuggestions != null ? cachedSuggestions.size() : 0);
                // 同时预构建系统提示词（空问题，用于提前加载文档文件）
                buildSystemPrompt("");
                log.info("AI 对话系统提示词预构建完成");
            } catch (Exception e) {
                log.warn("预生成 AI 对话建议问题失败: {}", e.getMessage());
            }
        }, "suggestions-init").start();
    }

    /**
     * 获取 AI 对话建议问题列表。
     * 首次调用时基于 README + CHANGELOG 生成，后续从缓存返回。
     * 版本更新后自动重新生成。
     */
    public List<String> getChatSuggestions() {
        // 获取当前版本号
        String currentVersion = resolveAppVersion();
        // 缓存命中直接返回
        if (cachedSuggestions != null && currentVersion != null && currentVersion.equals(cachedVersion)) {
            return cachedSuggestions;
        }

        // 读取 README 和 CHANGELOG 内容
        String readme = readStaticFile("README.md");
        String changelog = readStaticFile("CHANGELOG.md");

        if ((readme == null || readme.isBlank()) && (changelog == null || changelog.isBlank())) {
            log.warn("无法读取 README 或 CHANGELOG，使用默认建议问题");
            List<String> defaults = Arrays.asList(
                    "Mock Server 有哪些核心功能？",
                    "如何快速创建一个 Mock 接口？",
                    "AI 智能生成功能怎么使用？",
                    "如何进行项目管理和权限分配？"
            );
            cachedSuggestions = defaults;
            cachedVersion = currentVersion;
            return defaults;
        }

        // 截取摘要（减少 prompt 长度）
        String readmeSummary = readme != null ? truncateText(readme, 3000) : "";
        String changelogSummary = changelog != null ? truncateText(changelog, 2000) : "";

        // 尝试用 AI 生成建议问题
        AiConfig config = null;
        try {
            config = aiConfigService.getEnabledConfig();
        } catch (Exception ignored) {}

        if (config != null && config.getApiUrl() != null && !config.getApiUrl().isBlank()
                && config.getApiKey() != null && !config.getApiKey().isBlank()) {
            try {
                List<String> aiSuggestions = generateSuggestionsWithAI(config, readmeSummary, changelogSummary);
                if (aiSuggestions != null && !aiSuggestions.isEmpty()) {
                    cachedSuggestions = aiSuggestions;
                    cachedVersion = currentVersion;
                    log.info("AI 生成建议问题成功，共 {} 条，版本={}", aiSuggestions.size(), currentVersion);
                    return aiSuggestions;
                }
            } catch (Exception e) {
                log.warn("AI 生成建议问题失败，使用规则生成: {}", e.getMessage());
            }
        }

        // 回退：基于关键词规则从 README 提取建议问题
        List<String> fallback = generateSuggestionsByRule(readmeSummary, changelogSummary);
        cachedSuggestions = fallback;
        cachedVersion = currentVersion;
        log.info("规则生成建议问题成功，共 {} 条，版本={}", fallback.size(), currentVersion);
        return fallback;
    }

    /**
     * 读取 classpath 下的静态文件内容
     */
    private String readStaticFile(String filename) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("static/" + filename);
            if (is == null) {
                // 尝试直接从 classpath 根路径读取
                is = getClass().getClassLoader().getResourceAsStream(filename);
            }
            if (is == null) {
                log.debug("未找到静态文件: {}", filename);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.warn("读取静态文件 {} 失败: {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前应用版本号（简单实现：从 SystemInfoController 读取或返回默认值）
     */
    private String resolveAppVersion() {
        try {
            // 尝试从 pom.properties 读取
            InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/maven/com.carolcoral/mock-server/pom.properties");
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String version = props.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /**
     * 截断文本到指定长度
     */
    private String truncateText(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        // 尝试在段落边界截断
        int cutoff = text.lastIndexOf("\n\n", maxLen);
        if (cutoff > maxLen / 2) {
            return text.substring(0, cutoff) + "\n\n...";
        }
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 使用 AI 生成建议问题
     */
    private List<String> generateSuggestionsWithAI(AiConfig config, String readme, String changelog) {
        try {
            String prompt = "你是 Mock Server 的智能助手。请严格根据以下系统文档（README 和 CHANGELOG），" +
                    "生成 6 个用户最可能提问的建议问题。\n\n" +
                    "重要约束：\n" +
                    "1. 每个问题必须直接来源于下面文档中提到的具体功能/特性/变更，禁止凭空编造\n" +
                    "2. 覆盖 README 中的 3~4 个核心功能模块 + CHANGELOG 中的 2~3 个最新变更\n" +
                    "3. 问题简洁明了，20字以内，以问号结尾\n" +
                    "4. 问题使用中文，风格自然如用户真实提问\n" +
                    "5. 返回纯 JSON 数组格式，如：[\"问题1\",\"问题2\",\"问题3\"]\n" +
                    "6. 不要包含 markdown 代码块标记，不要包含任何解释文字\n\n" +
                    "=== 系统说明（README） ===\n" + readme + "\n\n" +
                    "=== 最新变更（CHANGELOG） ===\n" + changelog;

            String responseJson = callAiApi(config, prompt);
            String content = extractTextContent(responseJson);
            content = cleanMarkdownJson(content);

            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                List<String> result = new ArrayList<>();
                for (int i = 0; i < root.size() && i < 6; i++) {
                    String q = root.get(i).asText().trim();
                    if (!q.isEmpty()) {
                        result.add(q);
                    }
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception e) {
            log.warn("AI 生成或解析建议问题失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 基于规则从 README/CHANGELOG 提取建议问题（不依赖 AI）。
     * 解析 README 核心特性表格和 CHANGELOG 最新版本变更条目，
     * 确保生成的问题真实反映文档内容。
     */
    private List<String> generateSuggestionsByRule(String readme, String changelog) {
        List<String> questions = new ArrayList<>();

        // 1. 从 README 核心特性表格提取功能模块名称
        List<String> readmeTopics = extractReadmeTopics(readme);
        List<String> topicQuestions = generateQuestionsFromTopics(readmeTopics, "readme");
        questions.addAll(topicQuestions);

        // 2. 从 CHANGELOG 提取最新版本的新增功能
        List<String> changelogEntries = extractChangelogFeatures(changelog);
        List<String> changelogQuestions = generateQuestionsFromTopics(changelogEntries, "changelog");
        for (String q : changelogQuestions) {
            if (questions.size() >= 6) break;
            // 去重：避免与 README 生成的问题高度重复
            boolean duplicate = false;
            for (String existing : questions) {
                if (similarQuestion(existing, q)) { duplicate = true; break; }
            }
            if (!duplicate) questions.add(q);
        }

        // 3. 确保至少有 4 条，最多 6 条
        List<String> defaults = Arrays.asList(
                "Mock Server 有哪些核心功能？",
                "如何快速创建一个 Mock 接口？",
                "AI 智能平台怎么使用？",
                "如何进行项目管理和权限分配？"
        );

        while (questions.size() < 4) {
            for (String d : defaults) {
                if (!questions.contains(d) && questions.size() < 6) {
                    questions.add(d);
                }
            }
        }

        if (questions.size() > 6) {
            questions = questions.subList(0, 6);
        }

        log.info("基于文档规则生成建议问题 {} 条: {}", questions.size(), questions);
        return questions;
    }

    /**
     * 解析 README 核心特性表格，提取功能模块名称。
     * 匹配格式：| emoji 模块名 | 描述 |
     */
    private List<String> extractReadmeTopics(String readme) {
        List<String> topics = new ArrayList<>();
        if (readme == null || readme.isBlank()) return topics;

        String[] lines = readme.split("\\n");
        boolean inFeatureTable = false;
        for (String line : lines) {
            String trimmed = line.trim();
            // 检测进入核心特性表格
            if (trimmed.contains("核心特性") || trimmed.contains("Core Features")) {
                inFeatureTable = true;
                continue;
            }
            // 表格结束（遇到空行后的非表格行，或下一个 ## 标题）
            if (inFeatureTable && (trimmed.startsWith("##") || trimmed.startsWith("---"))) {
                break;
            }
            if (inFeatureTable && trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // 跳过表头分隔行
                if (trimmed.matches("\\|[-: |]+\\|")) continue;
                // 跳过表头行（包含 --- 的）
                if (trimmed.contains("---") && trimmed.contains("|")) continue;

                String[] cols = trimmed.split("\\|");
                if (cols.length >= 2) {
                    // 第1列是 emoji + 模块名，第2列是描述
                    String topicRaw = cols[1].trim();
                    // 去掉 emoji 前缀（Unicode 表情符号）
                    String topic = cleanTopicName(topicRaw);
                    if (!topic.isEmpty() && topic.length() >= 2 && !topic.startsWith("-")) {
                        topics.add(topic);
                    }
                }
            }
        }
        return topics;
    }

    /**
     * 解析 CHANGELOG 最新版本的新增功能条目。
     * 匹配格式：- **功能名**：描述
     */
    private List<String> extractChangelogFeatures(String changelog) {
        List<String> features = new ArrayList<>();
        if (changelog == null || changelog.isBlank()) return features;

        String[] lines = changelog.split("\\n");
        boolean inLatestVersion = false;
        boolean foundVersion = false;

        for (String line : lines) {
            String trimmed = line.trim();
            // 找到第一个版本标题（## vX.Y.Z）
            if (!foundVersion && trimmed.matches("^##\\s+v\\d+\\.\\d+.*")) {
                inLatestVersion = true;
                foundVersion = true;
                continue;
            }
            // 遇到下一个版本标题，停止
            if (foundVersion && inLatestVersion && trimmed.matches("^##\\s+v\\d+\\.\\d+.*")) {
                break;
            }
            // 遇到 --- 分隔线也停止
            if (foundVersion && inLatestVersion && trimmed.equals("---")) {
                break;
            }

            if (inLatestVersion) {
                // 提取三级标题（### 标题）作为功能类别
                if (trimmed.startsWith("### ")) {
                    String heading = trimmed.substring(4).trim();
                    // 去掉 emoji 前缀
                    heading = cleanTopicName(heading);
                    if (heading.length() >= 2 && !heading.startsWith("-")) {
                        features.add(heading);
                    }
                }
                // 提取列表项（- **功能名**：描述）
                if (trimmed.startsWith("- **")) {
                    int boldEnd = trimmed.indexOf("**", 4);
                    if (boldEnd > 4) {
                        String featureName = trimmed.substring(4, boldEnd).trim();
                        if (featureName.length() >= 2 && !featureName.equals("修复")
                                && !featureName.equals("升级说明") && !featureName.equals("新增")
                                && !featureName.equals("优化") && !featureName.equals("安全")) {
                            features.add(featureName);
                        }
                    }
                }
            }
        }
        return features;
    }

    /**
     * 清理模块名称：去掉 emoji、特殊符号、markdown 标记、括号内容
     */
    private String cleanTopicName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        // 去掉 markdown 粗体标记
        raw = raw.replaceAll("\\*\\*", "");
        // 去掉常见的 markdown/HTML 标记
        raw = raw.replaceAll("</?[a-z]+>", "");
        // 去掉 emoji（Unicode 范围：\uD83C-\uDBFF\uDC00-\uDFFF 和常见符号）
        raw = raw.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "");
        raw = raw.replaceAll("[\\u2600-\\u27BF]", "");
        raw = raw.replaceAll("[\\u2B50\\u2705\\u274C\\u2795-\\u2797\\u23CF\\u23E9-\\u23F3\\u23F8-\\u23FA\\u200D\\uFE0F]", "");
        // 去掉括号内的英文缩写说明，如 "细粒度权限控制 (RBAC)" → "细粒度权限控制"
        raw = raw.replaceAll("\\s*\\([A-Za-z0-9 /&]+\\)", "");
        // 去掉常见的中文标点符号和特殊字符前缀
        raw = raw.replaceAll("^[：:，,。；;！!？?\\s]+", "");
        raw = raw.replaceAll("[：:，,。；;！!？?\\s]+$", "");
        return raw.trim();
    }

    /**
     * 从模块名称列表生成自然语言问题。
     * 使用多样化的提问模板，避免所有问题都是同一种句式。
     */
    private List<String> generateQuestionsFromTopics(List<String> topics, String source) {
        List<String> questions = new ArrayList<>();

        // 多样化提问模板（轮换使用）
        String[][] readmeTemplates = {
            {"如何创建和配置%s？", "如何配置%s？", "%s怎么使用？"},
            {"%s有哪些功能？", "%s支持哪些模式？", "%s能做什么？"},
            {"%s如何设置？", "如何使用%s？", "%s功能怎么配置？"},
            {"%s是什么？怎么用？", "如何使用%s功能？", "%s在哪里配置？"},
        };

        String[][] changelogTemplates = {
            {"最新版%s有什么新功能？", "%s新增了什么？", "%s怎么用？"},
            {"%s功能如何使用？", "%s有什么变化？", "如何使用新版%s？"},
        };

        String[][] templates = "changelog".equals(source) ? changelogTemplates : readmeTemplates;

        for (int i = 0; i < topics.size() && questions.size() < 6; i++) {
            String topic = topics.get(i);
            if (topic.length() <= 1) continue;
            // 跳过纯英文缩写
            if (topic.matches("^[A-Z/&\\s]+$")) continue;
            // 轮换使用不同模板
            String[] tmplSet = templates[i % templates.length];
            String tmpl = tmplSet[i % tmplSet.length];
            String question = String.format(tmpl, topic);
            // 确保以问号结尾
            if (!question.endsWith("？") && !question.endsWith("?")) {
                question += "？";
            }
            questions.add(question);
        }

        return questions;
    }

    /**
     * 判断两个问题是否高度相似（用于去重）
     */
    private boolean similarQuestion(String a, String b) {
        if (a == null || b == null) return false;
        // 简单相似度：提取核心关键词比较
        String coreA = a.replaceAll("[？?！!。，,、\\s]", "");
        String coreB = b.replaceAll("[？?！!。，,、\\s]", "");
        if (coreA.length() < 2 || coreB.length() < 2) return false;
        // 计算公共子串长度占比
        int commonChars = 0;
        for (char c : coreA.toCharArray()) {
            if (coreB.indexOf(c) >= 0) commonChars++;
        }
        double ratio = (double) commonChars / Math.max(coreA.length(), coreB.length());
        return ratio > 0.6;
    }
}
