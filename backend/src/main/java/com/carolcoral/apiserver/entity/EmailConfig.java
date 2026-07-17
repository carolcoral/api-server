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
 * 邮箱配置实体
 * 用于存储 SMTP 服务器配置信息
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Schema(description = "邮箱配置实体")
@Entity
@Table(name = "t_email_config")
public class EmailConfig {

    @Schema(description = "配置ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "SMTP服务器地址", example = "smtp.qq.com")
    @Column(nullable = false, length = 200)
    private String smtpHost;

    @Schema(description = "SMTP服务器端口", example = "465")
    @Column(nullable = false)
    private Integer smtpPort = 465;

    @Schema(description = "是否启用SSL", example = "true")
    @Column(nullable = false)
    private Boolean useSsl = true;

    @Schema(description = "发件人邮箱地址", example = "sender@example.com")
    @Column(nullable = false, length = 200)
    private String fromAddress;

    @Schema(description = "发件人显示名称（邮件中的昵称）", example = "API Server")
    @Column(length = 100)
    private String displayName;

    @Schema(description = "SMTP认证用户名", example = "sender@example.com")
    @Column(nullable = false, length = 200)
    private String username;

    @Schema(description = "SMTP认证密码（授权码）")
    @Column(nullable = false, length = 500)
    private String password;

    @Schema(description = "是否启用邮箱服务", example = "true")
    @Column(nullable = false)
    private Boolean enabled = false;

    @Schema(description = "验证码邮件模板ID", example = "1")
    @Column
    private Long verificationTemplateId;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /**
     * 默认构造函数
     */
    public EmailConfig() {
    }

    /**
     * 全参构造函数
     */
    public EmailConfig(Long id, String smtpHost, Integer smtpPort, Boolean useSsl,
                       String fromAddress, String username, String password, Boolean enabled) {
        this.id = id;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.useSsl = useSsl;
        this.fromAddress = fromAddress;
        this.username = username;
        this.password = password;
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

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public Boolean getUseSsl() { return useSsl; }
    public void setUseSsl(Boolean useSsl) { this.useSsl = useSsl; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Long getVerificationTemplateId() { return verificationTemplateId; }
    public void setVerificationTemplateId(Long verificationTemplateId) { this.verificationTemplateId = verificationTemplateId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // ==================== equals 和 hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailConfig that = (EmailConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "EmailConfig{" +
                "id=" + id +
                ", smtpHost='" + smtpHost + '\'' +
                ", smtpPort=" + smtpPort +
                ", fromAddress='" + fromAddress + '\'' +
                ", enabled=" + enabled +
                ", verificationTemplateId=" + verificationTemplateId +
                '}';
    }
}
