/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto.oidc;

import java.util.ArrayList;
import java.util.List;

/**
 * OIDC 登录配置DTO
 * <p>用于后台设置中管理「所有允许 OIDC 登录的服务商」。
 * 总体开关与需要自动创建本地账号的场景由 {@code providers} 列表描述，
 * 不再局限于单一 TDP 服务商。</p>
 *
 * @author carolcoral
 */
public class OidcConfigDTO {

    /** 是否启用 OIDC 登录（总开关，关闭时所有服务商入口均不展示） */
    private Boolean enabled;

    /** 已配置的 OIDC 服务商列表 */
    private List<OidcProviderDTO> providers = new ArrayList<>();

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<OidcProviderDTO> getProviders() { return providers; }
    public void setProviders(List<OidcProviderDTO> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }
}
