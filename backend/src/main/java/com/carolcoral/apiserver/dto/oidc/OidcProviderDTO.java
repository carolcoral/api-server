/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

/**
 * 单个 OIDC 服务商配置DTO
 * <p>用于后台设置中新增/编辑一个允许登录的 OIDC 服务商，
 * 不再局限于 TDP，任何标准 OpenID Connect 服务均可配置。</p>
 *
 * @author carolcoral
 */
public class OidcProviderDTO {

    /** 服务商标识（唯一，作为配置键与前端按钮标识），如 tdp / keycloak / auth0 */
    private String providerId;

    /** 展示名称，如 TDP / Keycloak */
    private String name;

    /** 登录按钮显示名称，留空则回退为 name */
    private String buttonLabel;

    /** 是否启用该服务商 */
    private Boolean enabled;

    /** Issuer 地址，如 https://tdp.fan/oidc */
    private String issuerUri;

    /** 应用 Client ID */
    private String clientId;

    /** 应用 Client Secret（写入后不回显原文，仅返回是否已配置） */
    private String clientSecret;

    /** Secret 状态：normal / keep / clear（用于前端提交意图） */
    private String clientSecretStatus;

    /** 该服务商是否已配置 Client Secret（只读，用于前端展示占位提示） */
    private Boolean clientSecretConfigured;

    /** 回调地址，留空则按当前站点自动推导 */
    private String redirectUri;

    /** 请求的 Scope（空格分隔） */
    private String scope;

    /** 自动创建本地账号（用户首次通过该 OIDC 登录时自动建号） */
    private Boolean autoCreateUser;

    /** 是否启用 PKCE（推荐开启） */
    private Boolean usePkce;

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getClientSecretStatus() { return clientSecretStatus; }
    public void setClientSecretStatus(String clientSecretStatus) { this.clientSecretStatus = clientSecretStatus; }

    public Boolean getClientSecretConfigured() { return clientSecretConfigured; }
    public void setClientSecretConfigured(Boolean clientSecretConfigured) { this.clientSecretConfigured = clientSecretConfigured; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Boolean getAutoCreateUser() { return autoCreateUser; }
    public void setAutoCreateUser(Boolean autoCreateUser) { this.autoCreateUser = autoCreateUser; }

    public Boolean getUsePkce() { return usePkce; }
    public void setUsePkce(Boolean usePkce) { this.usePkce = usePkce; }
}
