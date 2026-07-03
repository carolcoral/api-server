/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 登录响应DTO
 *
 * @author carolcoral
 */
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "用户邮箱", example = "admin@mockserver.com")
    private String email;

    @Schema(description = "用户角色", example = "ADMIN")
    private String role;

    @Schema(description = "角色ID", example = "1")
    private Long roleId;

    @Schema(description = "角色名称（展示用）", example = "普通用户")
    private String roleName;

    @Schema(description = "角色编码", example = "ROLE_USER")
    private String roleCode;

    @Schema(description = "用户语言", example = "zh-CN")
    private String language;

    @Schema(description = "过期时间（毫秒）", example = "86400000")
    private Long expiresIn;

    @Schema(description = "用户权限编码列表", example = "[\"ai-chat:view\", \"statistics:view\"]")
    private java.util.List<String> permissions;

    /**
     * 默认构造器
     */
    public LoginResponse() {
    }

    /**
     * 全参构造器
     */
    public LoginResponse(String token, String tokenType, Long userId, String username, String email, String role, Long roleId, String roleName, String roleCode, String language, Long expiresIn, java.util.List<String> permissions) {
        this.token = token;
        this.tokenType = tokenType;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleCode = roleCode;
        this.language = language;
        this.expiresIn = expiresIn;
        this.permissions = permissions;
    }

    /**
     * Builder方法
     */
    public static LoginResponseBuilder builder() {
        return new LoginResponseBuilder();
    }

    /**
     * Builder类
     */
    public static class LoginResponseBuilder {
        private String token;
        private String tokenType;
        private Long userId;
        private String username;
        private String email;
        private String role;
        private Long roleId;
        private String roleName;
        private String roleCode;
        private String language;
        private Long expiresIn;
        private java.util.List<String> permissions;

        public LoginResponseBuilder token(String token) {
            this.token = token;
            return this;
        }

        public LoginResponseBuilder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public LoginResponseBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public LoginResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public LoginResponseBuilder email(String email) {
            this.email = email;
            return this;
        }

        public LoginResponseBuilder role(String role) {
            this.role = role;
            return this;
        }

        public LoginResponseBuilder roleId(Long roleId) {
            this.roleId = roleId;
            return this;
        }

        public LoginResponseBuilder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public LoginResponseBuilder roleCode(String roleCode) {
            this.roleCode = roleCode;
            return this;
        }

        public LoginResponseBuilder language(String language) {
            this.language = language;
            return this;
        }

        public LoginResponseBuilder expiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public LoginResponseBuilder permissions(java.util.List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public LoginResponse build() {
            return new LoginResponse(token, tokenType, userId, username, email, role, roleId, roleName, roleCode, language, expiresIn, permissions);
        }
    }

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public java.util.List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(java.util.List<String> permissions) {
        this.permissions = permissions;
    }
}
