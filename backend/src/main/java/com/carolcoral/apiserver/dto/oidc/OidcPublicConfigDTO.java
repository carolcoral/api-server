/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

/**
 * OIDC 公开配置DTO（无需认证）
 * <p>供登录页判断是否展示 OIDC 登录入口，不含敏感信息。</p>
 *
 * @author carolcoral
 */
public class OidcPublicConfigDTO {

    /** 是否启用 OIDC 登录 */
    private Boolean enabled;

    /** OIDC 提供方标识 */
    private String provider;

    /** 登录按钮显示名称 */
    private String buttonLabel;

    public OidcPublicConfigDTO() {
    }

    public OidcPublicConfigDTO(Boolean enabled, String provider, String buttonLabel) {
        this.enabled = enabled;
        this.provider = provider;
        this.buttonLabel = buttonLabel;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }
}
