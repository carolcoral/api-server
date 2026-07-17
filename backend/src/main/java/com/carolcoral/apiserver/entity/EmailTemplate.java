/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 邮件模板实体
 * 用于存储邮件模板信息，支持 HTML 格式和占位符替换
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Schema(description = "邮件模板实体")
@Entity
@Table(name = "t_email_template")
public class EmailTemplate {

    /**
     * 模板类型常量
     */
    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_RESET_PASSWORD = "RESET_PASSWORD";
    public static final String TYPE_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    /**
     * 所有支持的模板类型列表
     */
    public static final java.util.List<String> ALL_TYPES = java.util.List.of(
            TYPE_REGISTER, TYPE_RESET_PASSWORD, TYPE_PASSWORD_CHANGED
    );

    @Schema(description = "模板ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "模板名称", example = "注册验证邮件")
    @Column(nullable = false, length = 200)
    private String name;

    @Schema(description = "模板类型", example = "REGISTER", allowableValues = {"REGISTER", "RESET_PASSWORD", "PASSWORD_CHANGED"})
    @Column(nullable = false, length = 50)
    private String type = TYPE_REGISTER;

    @Schema(description = "邮件主题", example = "【API Server】注册验证码")
    @Column(nullable = false, length = 500)
    private String subject;

    @Schema(description = "邮件内容（支持HTML格式）", example = "<div>您的验证码：{{code}}</div>")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /**
     * 默认构造函数
     */
    public EmailTemplate() {
    }

    /**
     * 全参构造函数
     */
    public EmailTemplate(Long id, String name, String type, String subject,
                         String content, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.subject = subject;
        this.content = content;
        this.enabled = enabled;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // ==================== Getter 和 Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // ==================== equals 和 hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailTemplate that = (EmailTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "EmailTemplate{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", subject='" + subject + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
