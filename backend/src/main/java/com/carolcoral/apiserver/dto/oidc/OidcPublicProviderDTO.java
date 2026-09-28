/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

/**
 * 单个 OIDC 服务商公开信息DTO（无需认证）
 * <p>供登录页渲染登录按钮，不含任何敏感信息。</p>
 *
 * @author carolcoral
 */
public class OidcPublicProviderDTO {

    /** 服务商标识 */
    private String providerId;

    /** 展示名称 */
    private String name;

    /** 登录按钮显示名称 */
    private String buttonLabel;

    /** 登录图标访问地址（无图标时为 null，前端使用默认图标） */
    private String iconUrl;

    public OidcPublicProviderDTO() {
    }

    public OidcPublicProviderDTO(String providerId, String name, String buttonLabel) {
        this(providerId, name, buttonLabel, null);
    }

    public OidcPublicProviderDTO(String providerId, String name, String buttonLabel, String iconUrl) {
        this.providerId = providerId;
        this.name = name;
        this.buttonLabel = buttonLabel;
        this.iconUrl = iconUrl;
    }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
}
