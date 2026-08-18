/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.MockApi;
import com.carolcoral.apiserver.entity.MockResponse;
import com.carolcoral.apiserver.entity.ResponseRequestParam;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.ResponseRequestParamRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 接口 Markdown 文档导出服务
 * <p>
 * 按 Issue #21 规定的格式模板将接口导出为 Markdown 文档，支持：
 * 1. 多选接口导出（接口名、描述、请求方式、接口地址、Header、请求参数、响应数据、响应示例）
 * 2. 手动控制是否进行 AI 增强
 * 3. AI 增强内容同样必须遵守规定的格式约束（缺失必需章节时回退为基础模板）
 * </p>
 *
 * @author carolcoral
 * @since 2026-08-13
 */
@Service
public class MockApiMarkdownService {

    private static final Logger log = LoggerFactory.getLogger(MockApiMarkdownService.class);

    private final MockApiRepository mockApiRepository;
    private final ResponseRequestParamRepository responseRequestParamRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 规定的 Markdown 文档中每个接口必须包含的章节标题（AI 增强输出必须全部包含，否则回退）
     */
    private static final String[] REQUIRED_SECTIONS = {
            "## 请求方式",
            "## 接口地址",
            "## Header",
            "## 请求参数",
            "## 响应数据",
            "### 响应示例"
    };

    /**
     * 构造器
     */
    public MockApiMarkdownService(MockApiRepository mockApiRepository,
                                  ResponseRequestParamRepository responseRequestParamRepository,
                                  AiService aiService) {
        this.mockApiRepository = mockApiRepository;
        this.responseRequestParamRepository = responseRequestParamRepository;
        this.aiService = aiService;
    }

    /**
     * 导出多个接口为 Markdown 文档
     * <p>
     * 每个接口的基础文档会完整导出其全部响应对应的 Header、请求参数、响应字段及响应示例；
     * 启用 AI 增强时，若 AI 调用异常或输出不合规，会在文档对应章节插入友好提示，
     * 并将警告信息收集到 {@link ExportResult#getWarnings()} 供调用方（前端）弹窗展示。
     * </p>
     *
     * @param apiIds    接口ID列表
     * @param aiEnhance 是否启用 AI 增强（手动控制）
     * @return 导出结果（Markdown 文本 + 警告信息列表）
     */
    @Transactional(readOnly = true)
    public ExportResult exportMarkdown(List<Long> apiIds, boolean aiEnhance) {
        if (apiIds == null || apiIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个接口");
        }

        // 阶段1：在事务中串行加载接口数据并生成基础 Markdown（避免懒加载在多线程中失效）
        List<ApiExportContext> contexts = new ArrayList<>();
        for (Long apiId : apiIds) {
            Optional<MockApi> apiOpt = mockApiRepository.findById(apiId);
            if (apiOpt.isEmpty()) {
                log.warn("导出 Markdown 时接口不存在，跳过: apiId={}", apiId);
                continue;
            }
            MockApi api = apiOpt.get();
            contexts.add(new ApiExportContext(apiId, api, buildApiMarkdown(api)));
        }

        // 阶段2：对需要 AI 增强的接口并行调用 AI（IO 密集型，可并行缩短总耗时）
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        if (aiEnhance && !contexts.isEmpty()) {
            contexts.parallelStream().forEach(ctx -> {
                String enhanced = applyAiEnhancement(ctx.getApi(), ctx.getBaseMarkdown(), warnings);
                ctx.setEnhancedMarkdown(enhanced);
            });
        }

        // 阶段3：按原始顺序拼接最终文档
        StringBuilder sb = new StringBuilder();
        sb.append("# API 接口文档\n\n");
        sb.append("> 由接口管理平台自动生成");
        if (aiEnhance) {
            sb.append("（已启用 AI 增强）");
        }
        sb.append("\n\n---\n\n");

        int index = 0;
        for (ApiExportContext ctx : contexts) {
            String section = aiEnhance ? ctx.getEnhancedMarkdown() : ctx.getBaseMarkdown();
            if (index > 0) {
                sb.append("\n\n---\n\n");
            }
            sb.append(section);
            index++;
        }

        if (index == 0) {
            sb.append("未找到可导出的接口。\n");
            warnings.add("未找到可导出的接口，请确认所选接口仍存在");
        }
        return new ExportResult(sb.toString(), new ArrayList<>(warnings));
    }

    /**
     * 单个接口导出上下文（用于阶段式并行处理）
     */
    private static class ApiExportContext {
        private final Long apiId;
        private final MockApi api;
        private final String baseMarkdown;
        private String enhancedMarkdown;

        ApiExportContext(Long apiId, MockApi api, String baseMarkdown) {
            this.apiId = apiId;
            this.api = api;
            this.baseMarkdown = baseMarkdown;
            this.enhancedMarkdown = baseMarkdown;
        }

        public Long getApiId() {
            return apiId;
        }

        public MockApi getApi() {
            return api;
        }

        public String getBaseMarkdown() {
            return baseMarkdown;
        }

        public String getEnhancedMarkdown() {
            return enhancedMarkdown;
        }

        public void setEnhancedMarkdown(String enhancedMarkdown) {
            this.enhancedMarkdown = enhancedMarkdown;
        }
    }

    /**
     * 对单个接口执行 AI 增强
     * <p>
     * AI 调用异常或输出不符合格式约束时，回退为基础文档，同时在文档章节顶部插入
     * 友好提示块，并将警告信息收集到 warnings 列表。
     * </p>
     *
     * @param api      接口
     * @param section  基础 Markdown 章节
     * @param warnings 警告信息收集列表
     * @return 增强后的 Markdown（失败时为基础文档 + 提示块）
     */
    private String applyAiEnhancement(MockApi api, String section, List<String> warnings) {
        String aiMarkdown = null;
        String aiError = null;
        try {
            aiMarkdown = aiService.enhanceApiMarkdown(buildAiContext(api, section));
            // 格式约束校验：AI 输出必须包含全部必需章节，否则回退为基础模板
            if (!ensureFormatCompliant(aiMarkdown)) {
                aiError = "AI 增强输出不符合规定的格式约束，已回退为基础文档";
                log.warn("AI 增强输出不符合格式约束，接口 {} 回退为基础文档", api.getId());
                aiMarkdown = null;
            }
        } catch (Exception e) {
            aiError = "AI 增强服务调用异常（" + extractFriendlyMessage(e) + "），已回退为基础文档";
            log.error("AI 增强接口 {} 失败，回退为基础文档: {}", api.getId(), e.getMessage(), e);
            aiMarkdown = null;
        }

        if (aiMarkdown != null) {
            return aiMarkdown;
        }
        if (aiError != null) {
            warnings.add("接口【" + api.getName() + "】：" + aiError);
            return "> ⚠️ " + aiError + "\n\n" + section;
        }
        return section;
    }

    /**
     * 从异常中提取友好的错误信息（去除异常类型前缀等噪音）
     */
    private String extractFriendlyMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        // 去除形如 "AI 增强失败: xxx" 中的外层包装，保留最有价值的部分
        String cleaned = message.replaceFirst("^AI 增强失败[:：]?\\s*", "").trim();
        return cleaned.isEmpty() ? "未知错误" : cleaned;
    }

    /**
     * 按规定的模板生成单个接口的基础 Markdown
     * <p>
     * 完整导出该接口下全部响应（而非仅激活/默认响应）：
     * Header、请求参数为所有响应的请求参数汇总（按参数名去重）；
     * 响应数据为所有响应响应体字段汇总（按字段名去重）；
     * 响应示例为每个响应独立生成的子章节。
     * </p>
     */
    private String buildApiMarkdown(MockApi api) {
        StringBuilder sb = new StringBuilder();

        // # 接口名 + 描述
        sb.append("# ").append(str(api.getName())).append("\n");
        sb.append(str(api.getDescription())).append("\n\n");

        // 请求方式
        sb.append("## 请求方式\n");
        sb.append(str(api.getMethod())).append("\n\n");

        // 接口地址
        sb.append("## 接口地址\n");
        sb.append(buildApiUri(api)).append("\n\n");

        // 全部响应（保留数据库中的定义顺序）
        List<MockResponse> responses = api.getResponses();
        if (responses == null || responses.isEmpty()) {
            responses = Collections.emptyList();
        }

        // 汇总所有响应的 Header/请求参数（按参数名去重）
        List<ResponseRequestParam> headerParams = new ArrayList<>();
        List<ResponseRequestParam> requestParams = new ArrayList<>();
        Set<String> headerSeen = new LinkedHashSet<>();
        Set<String> requestSeen = new LinkedHashSet<>();
        for (MockResponse r : responses) {
            List<ResponseRequestParam> params = responseRequestParamRepository.findByMockResponseId(r.getId());
            if (params == null) continue;
            for (ResponseRequestParam p : params) {
                boolean isHeader = ResponseRequestParam.ParamType.HEADER.name()
                        .equalsIgnoreCase(String.valueOf(p.getParamType()));
                Set<String> seen = isHeader ? headerSeen : requestSeen;
                String key = p.getParamName() + "|" + p.getParamType();
                if (p.getParamName() != null && !seen.add(key)) {
                    continue; // 同名同类型参数去重
                }
                if (isHeader) {
                    headerParams.add(p);
                } else {
                    requestParams.add(p);
                }
            }
        }

        // Header 表格
        sb.append("## Header\n");
        sb.append("|  名称 | 说明 | 是否必须 | 数据类型 | 默认值 |\n");
        sb.append("|-------|------|---------|---------|--------|\n");
        if (headerParams.isEmpty()) {
            sb.append("| - | - | - | - | |\n");
        } else {
            for (ResponseRequestParam p : headerParams) {
                sb.append("| ").append(esc(p.getParamName()))
                        .append(" | ").append(esc(p.getParamValue()))
                        .append(" | ").append(required(p.getRequired()))
                        .append(" | string | |\n");
            }
        }
        sb.append("\n");

        // 请求参数表格
        sb.append("## 请求参数\n");
        sb.append("|  名称 | 说明 | 是否必须 | 数据类型 | 默认值 |\n");
        sb.append("|-------|------|---------|---------|--------|\n");
        if (requestParams.isEmpty()) {
            sb.append("| - | - | - | - | |\n");
        } else {
            for (ResponseRequestParam p : requestParams) {
                sb.append("| ").append(esc(p.getParamName()))
                        .append(" | ").append(esc(p.getParamValue()))
                        .append(" | ").append(required(p.getRequired()))
                        .append(" | ").append(str(p.getParamType()))
                        .append(" | |\n");
            }
        }
        sb.append("\n");

        // 响应数据表格（汇总所有响应的字段，按字段名去重）
        sb.append("## 响应数据\n");
        sb.append("|  名称 | 说明 | 是否必须 | 数据类型 |\n");
        sb.append("|-------|------|---------|---------|\n");
        int rowCount = 0;
        Set<String> fieldSeen = new LinkedHashSet<>();
        for (MockResponse r : responses) {
            rowCount += appendResponseRows(sb, r.getResponseBody(), fieldSeen);
        }
        if (rowCount == 0) {
            sb.append("| - | - | - | - |\n");
        }
        sb.append("\n");

        // 响应示例：每个响应独立成节，全部导出
        sb.append("### 响应示例\n");
        if (responses.isEmpty()) {
            sb.append("```json\n{}\n```\n");
        } else {
            int respIndex = 0;
            for (MockResponse r : responses) {
                respIndex++;
                sb.append("#### 响应 ").append(respIndex);
                if (r.getStatusCode() != null) {
                    sb.append("（状态码 ").append(r.getStatusCode()).append("）");
                }
                if (Boolean.TRUE.equals(r.getActive())) {
                    sb.append("（激活）");
                } else if (Boolean.TRUE.equals(r.getIsDefault())) {
                    sb.append("（默认）");
                }
                if (r.getConditionDesc() != null && !r.getConditionDesc().isBlank()) {
                    sb.append("：").append(esc(r.getConditionDesc()));
                }
                sb.append("\n");
                String responseBody = r.getResponseBody();
                if (responseBody != null && !responseBody.isBlank()) {
                    sb.append("```json\n").append(prettyPrintJson(responseBody)).append("\n```\n\n");
                } else {
                    sb.append("```json\n{}\n```\n\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 生成响应数据表格行：尝试解析响应体 JSON 并提取顶层字段，按字段名去重
     *
     * @return 生成的行数
     */
    private int appendResponseRows(StringBuilder sb, String responseBody, Set<String> fieldSeen) {
        if (responseBody == null || responseBody.isBlank()) {
            return 0;
        }
        int count = 0;
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    if (fieldSeen.add(entry.getKey())) {
                        sb.append("| ").append(esc(entry.getKey()))
                                .append(" | - | - | ").append(inferType(entry.getValue())).append(" |\n");
                        count++;
                    }
                }
            } else if (node.isArray()) {
                if (fieldSeen.add("[数组]")) {
                    sb.append("| ").append(esc("[数组]")).append(" | 数组，共 ").append(node.size()).append(" 项 | - | array |\n");
                    count++;
                }
            } else {
                if (fieldSeen.add("[标量]")) {
                    sb.append("| ").append(esc("[标量]")).append(" | - | - | ").append(inferType(node)).append(" |\n");
                    count++;
                }
            }
        } catch (Exception e) {
            if (fieldSeen.add("[原始响应]")) {
                sb.append("| - | 原始响应体 | - | text |\n");
                count++;
            }
        }
        return count;
    }

    /**
     * 格式化 JSON（解析后美化输出，非 JSON 保持原样）
     */
    private String prettyPrintJson(String responseBody) {
        try {
            Object parsed = objectMapper.readValue(responseBody, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return responseBody;
        }
    }

    /**
     * 校验 AI 增强输出是否符合规定的格式约束（必须包含全部必需章节）
     */
    private boolean ensureFormatCompliant(String aiMarkdown) {
        if (aiMarkdown == null || aiMarkdown.isBlank()) {
            return false;
        }
        if (!aiMarkdown.contains("# ")) {
            return false;
        }
        for (String section : REQUIRED_SECTIONS) {
            if (!aiMarkdown.contains(section)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构造 AI 增强的上下文（接口原始信息 + 基础文档）
     */
    private String buildAiContext(MockApi api, String baseMarkdown) {
        return "接口名称：" + str(api.getName()) + "\n"
                + "接口描述：" + str(api.getDescription()) + "\n"
                + "请求方式：" + str(api.getMethod()) + "\n"
                + "接口地址：" + buildApiUri(api) + "\n\n"
                + "当前基础 Markdown 文档（可在此基础上增强，但必须保持章节结构）：\n" + baseMarkdown;
    }

    /**
     * 生成接口完整地址（与前端展示保持一致）
     */
    private String buildApiUri(MockApi api) {
        String code = api.getProject() != null ? api.getProject().getCode() : null;
        String path = api.getPath() != null ? api.getPath() : "";
        return "/api/api-server/" + (code != null ? code : "") + path;
    }

    /**
     * 推断 JSON 节点数据类型
     */
    private String inferType(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isFloatingPointNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        return "string";
    }

    /**
     * 是否必填展示（true/false）
     */
    private String required(Boolean required) {
        return required == null ? "-" : String.valueOf(required);
    }

    /**
     * 转义 Markdown 表格中的特殊字符
     */
    private String esc(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "\\|")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /**
     * 空值兜底
     */
    private String str(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
