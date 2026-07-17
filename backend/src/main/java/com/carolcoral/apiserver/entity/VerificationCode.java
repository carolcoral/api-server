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
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 邮箱验证码实体
 * 用于存储注册/找回密码等场景的邮箱验证码
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Schema(description = "邮箱验证码实体")
@Entity
@Table(name = "t_verification_code")
public class VerificationCode {

    @Schema(description = "验证码ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "目标邮箱地址", example = "user@example.com")
    @Column(nullable = false, length = 200)
    private String email;

    @Schema(description = "验证码", example = "123456")
    @Column(nullable = false, length = 20)
    private String code;

    @Schema(description = "验证码类型", example = "REGISTER")
    @Column(nullable = false, length = 50)
    private String type = "REGISTER";

    @Schema(description = "过期时间")
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Schema(description = "是否已使用", example = "false")
    @Column(nullable = false)
    private Boolean used = false;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 默认构造函数
     */
    public VerificationCode() {
    }

    /**
     * 全参构造函数
     */
    public VerificationCode(Long id, String email, String code, String type,
                            LocalDateTime expiresAt, Boolean used) {
        this.id = id;
        this.email = email;
        this.code = code;
        this.type = type;
        this.expiresAt = expiresAt;
        this.used = used;
    }

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    // ==================== Getter 和 Setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    // ==================== equals 和 hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationCode that = (VerificationCode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "VerificationCode{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", type='" + type + '\'' +
                ", used=" + used +
                '}';
    }
}
