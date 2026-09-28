/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

/**
 * OIDC 登录配置DTO
 * <p>用于后台设置中配置 TDP OIDC 登录所需的参数。</p>
 *
 * @author carolcoral
 */
public class OidcConfigDTO {

    /** 是否启用 OIDC 登录 */
    private Boolean enabled;

    /** OIDC 提供方标识，默认 tdp */
    private String provider;

    /** Issuer 地址，如 https://tdp.fan/oidc */
    private String issuerUri;

    /** 应用 Client ID */
    private String clientId;

    /** 应用 Client Secret（写入后不回显原文，仅返回是否已配置） */
    private String clientSecret;

    /** 回调地址，留空则按当前站点自动推导 */
    private String redirectUri;

    /** 请求的 Scope（空格分隔） */
    private String scope;

    /** 自动创建本地账号（用户首次通过 OIDC 登录时自动建号） */
    private Boolean autoCreateUser;

    /** OIDC 登录按钮显示名称 */
    private String buttonLabel;

    /** 是否启用 PKCE（推荐开启） */
    private Boolean usePkce;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Boolean getAutoCreateUser() { return autoCreateUser; }
    public void setAutoCreateUser(Boolean autoCreateUser) { this.autoCreateUser = autoCreateUser; }

    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }

    public Boolean getUsePkce() { return usePkce; }
    public void setUsePkce(Boolean usePkce) { this.usePkce = usePkce; }
}
