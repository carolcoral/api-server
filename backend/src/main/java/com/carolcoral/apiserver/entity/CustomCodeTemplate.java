/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 自定义代码模板实体类
 * 用于管理项目维度的自定义接口返回报文代码模板
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-15
 */
@Schema(description = "自定义代码模板实体")
@Entity
@Table(name = "t_custom_code_template")
public class CustomCodeTemplate {

    @Schema(description = "模板ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "模板名称", example = "标准格式包装器")
    @Column(nullable = false, length = 200)
    private String name;

    @Schema(description = "模板描述", example = "将响应包装为 {code, message, data, timestamp} 格式")
    @Column(length = 500)
    private String description;

    @Schema(description = "Java源代码")
    @Column(columnDefinition = "TEXT")
    private String sourceCode;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    @Schema(description = "是否为系统默认模板（不可修改/删除）", example = "false")
    @Column(nullable = false)
    private Boolean isSystem = false;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    @Schema(description = "创建人ID", example = "1")
    @Column(nullable = false)
    private Long createUserId;

    @Schema(description = "所属项目（系统模板为null，表示全局可用）")
    @ManyToOne
    @JoinColumn(name = "project_id")
    @JsonIgnoreProperties({"mockApis", "hibernateLazyInitializer", "handler"})
    private Project project;

    /**
     * 无参构造函数
     */
    public CustomCodeTemplate() {
    }

    /**
     * 全参构造函数
     */
    public CustomCodeTemplate(Long id, String name, String description, String sourceCode,
                              Boolean enabled, LocalDateTime createTime, LocalDateTime updateTime,
                              Long createUserId, Project project) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.sourceCode = sourceCode;
        this.enabled = enabled;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createUserId = createUserId;
        this.project = project;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getCreateUserId() {
        return createUserId;
    }

    public void setCreateUserId(Long createUserId) {
        this.createUserId = createUserId;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomCodeTemplate that = (CustomCodeTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CustomCodeTemplate{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", enabled=" + enabled +
                ", isSystem=" + isSystem +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", createUserId=" + createUserId +
                ", project=" + (project != null ? project.getId() : null) +
                '}';
    }

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
