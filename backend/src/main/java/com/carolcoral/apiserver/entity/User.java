/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户实体类
 * <p>
 * 用户实体类，实现了Spring Security的UserDetails接口，
 * 用于系统中用户信息的持久化存储和认证授权。
 * </p>
 *
 * @author carolcoral
 * @since 1.0.0
 */
@Schema(description = "用户实体")
@Entity
@Table(name = "t_user")
public class User implements UserDetails {

    /**
     * 用户ID，主键自增
     */
    @Schema(description = "用户ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名，唯一且不能为空
     */
    @Schema(description = "用户名", example = "admin")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 密码，加密存储
     */
    @Schema(description = "密码（加密存储）")
    @Column(nullable = false)
    private String password;

    /**
     * 用户邮箱
     */
    @Schema(description = "邮箱", example = "user@example.com")
    @Column(length = 100)
    private String email;

    /**
     * 用户语言偏好，默认为中文
     */
    @Schema(description = "用户语言", example = "zh-CN", allowableValues = {"zh-CN", "en-US", "ja-JP"})
    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'zh-CN'")
    private String language = "zh-CN";

    /**
     * 用户角色，默认为普通用户
     */
    @Schema(description = "用户角色", example = "ADMIN", allowableValues = {"ADMIN", "USER"})
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    /**
     * 角色ID（关联t_role表，v2.3.0新增）
     */
    @Schema(description = "角色ID")
    @Column
    private Long roleId;

    /**
     * 权限编码集合（非持久化字段，由 UserDetailsService 动态加载）
     */
    @Schema(description = "权限编码列表")
    @Transient
    private Set<String> permissions;

    /**
     * 是否启用，默认为true
     */
    @Schema(description = "是否启用", example = "true")
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * 创建时间，创建时自动设置
     */
    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间，更新时自动更新
     */
    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /**
     * 默认构造函数
     */
    public User() {
    }

    /**
     * 全参构造函数
     *
     * @param id       用户ID
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @param role     角色
     * @param enabled  是否启用
     */
    public User(Long id, String username, String password, String email, UserRole role, Boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
    }

    /**
     * 实体持久化前回调，设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createTime = now;
        this.updateTime = now;
    }

    /**
     * 实体更新前回调，更新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // ==================== Getter 和 Setter 方法 ====================

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置用户ID
     *
     * @param id 用户ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取密码
     *
     * @return 密码
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码
     *
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取邮箱
     *
     * @return 邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱
     *
     * @param email 邮箱
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取用户语言偏好
     *
     * @return 用户语言
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 设置用户语言偏好
     *
     * @param language 用户语言
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * 获取用户角色
     *
     * @return 用户角色
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * 设置用户角色
     *
     * @param role 用户角色
     */
    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * 获取角色ID
     *
     * @return 角色ID
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * 设置角色ID
     *
     * @param roleId 角色ID
     */
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    // ==================== UserDetails 接口实现 ====================

    /**
     * 获取用户权限集合（包含角色 + 动态权限码）
     *
     * @return 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 添加角色权限
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        // 添加动态权限码（从 t_role_permission + t_permission 加载）
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
        return authorities;
    }

    /**
     * 获取权限编码集合
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * 设置权限编码集合
     */
    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    /**
     * 账户是否未过期
     *
     * @return 始终返回true
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定
     *
     * @return 始终返回true
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期
     *
     * @return 始终返回true
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 设置账户是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取启用状态
     *
     * @return 启用状态
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 账户是否启用（UserDetails接口方法）
     *
     * @return 启用状态
     */
    @Override
    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    // ==================== equals 和 hashCode ====================

    /**
     * 判断两个用户是否相等（基于ID）
     *
     * @param o 另一个对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    /**
     * 计算哈希码（基于ID）
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * 转换为字符串表示
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", enabled=" + enabled +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }

    /**
     * 用户角色枚举
     * <p>
     * 定义系统中用户的角色类型，包括管理员和普通用户
     * </p>
     */
    public enum UserRole {
        /** 管理员角色，拥有所有权限 */
        ADMIN,
        /** 普通用户角色，拥有受限权限 */
        USER
    }
}
