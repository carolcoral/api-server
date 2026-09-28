/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

/**
 * OIDC 用户信息
 * <p>从 ID Token / UserInfo 端点解析出的标准用户信息。</p>
 *
 * @author carolcoral
 */
public class OidcUserInfo {

    /** 主体标识（sub），全局唯一且稳定 */
    private String sub;

    /** 用户名（preferred_username） */
    private String preferredUsername;

    /** 昵称（name） */
    private String name;

    /** 邮箱（email） */
    private String email;

    /** 邮箱是否已验证 */
    private Boolean emailVerified;

    /** 头像（picture） */
    private String picture;

    /** TDP 角色（tdp_role）：user / certified / admin */
    private String tdpRole;

    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    public String getPreferredUsername() { return preferredUsername; }
    public void setPreferredUsername(String preferredUsername) { this.preferredUsername = preferredUsername; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getPicture() { return picture; }
    public void setPicture(String picture) { this.picture = picture; }

    public String getTdpRole() { return tdpRole; }
    public void setTdpRole(String tdpRole) { this.tdpRole = tdpRole; }
}
