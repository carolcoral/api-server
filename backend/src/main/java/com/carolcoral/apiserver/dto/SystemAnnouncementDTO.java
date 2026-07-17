/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 系统公告DTO
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "系统公告DTO")
public class SystemAnnouncementDTO {

    @Schema(description = "公告ID", example = "1")
    private Long id;

    @Schema(description = "公告标题", example = "系统升级通知", required = true)
    @NotBlank(message = "公告标题不能为空")
    private String title;

    @Schema(description = "公告内容（支持Markdown）", example = "## 升级内容\n1. 新增XX功能", required = true)
    @NotBlank(message = "公告内容不能为空")
    private String content;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "优先级", example = "NORMAL")
    private String priority;

    @Schema(description = "创建人", example = "admin")
    private String createBy;

    @Schema(description = "创建人ID", example = "1")
    private Long createUserId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
