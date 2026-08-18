/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 接口 Markdown 导出结果
 * <p>
 * 包含最终导出的 Markdown 文本，以及导出过程中产生的警告信息
 * （如 AI 增强调用异常、AI 输出不合规时的友好提示），
 * 由调用方（Controller）决定如何传递给前端展示。
 * </p>
 *
 * @author carolcoral
 * @since 2026-08-17
 */
public class ExportResult {

    private final String markdown;
    private final List<String> warnings;

    /**
     * 构造器
     *
     * @param markdown 导出的 Markdown 文本
     * @param warnings 导出过程警告信息列表（可为 null）
     */
    public ExportResult(String markdown, List<String> warnings) {
        this.markdown = markdown;
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    /**
     * 获取导出的 Markdown 文本
     *
     * @return Markdown 文本
     */
    public String getMarkdown() {
        return markdown;
    }

    /**
     * 获取导出过程中的警告信息列表（只读）
     *
     * @return 警告信息列表
     */
    public List<String> getWarnings() {
        return warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
    }
}
