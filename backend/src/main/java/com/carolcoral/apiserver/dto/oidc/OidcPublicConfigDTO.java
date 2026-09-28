/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

import java.util.ArrayList;
import java.util.List;

/**
 * OIDC 公开配置DTO（无需认证）
 * <p>供登录页判断是否展示 OIDC 登录入口，不含敏感信息。</p>
 *
 * @author carolcoral
 */
public class OidcPublicConfigDTO {

    /** 是否启用 OIDC 登录（存在至少一个可用服务商时为 true） */
    private Boolean enabled;

    /** 可用于登录的服务商列表（仅公开字段） */
    private List<OidcPublicProviderDTO> providers = new ArrayList<>();

    public OidcPublicConfigDTO() {
    }

    public OidcPublicConfigDTO(Boolean enabled, List<OidcPublicProviderDTO> providers) {
        this.enabled = enabled;
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<OidcPublicProviderDTO> getProviders() { return providers; }
    public void setProviders(List<OidcPublicProviderDTO> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }
}
