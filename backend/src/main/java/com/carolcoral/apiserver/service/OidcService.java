/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.LoginResponse;
import com.carolcoral.apiserver.dto.oidc.OidcConfigDTO;
import com.carolcoral.apiserver.dto.oidc.OidcProviderDTO;
import com.carolcoral.apiserver.dto.oidc.OidcPublicConfigDTO;
import com.carolcoral.apiserver.dto.oidc.OidcPublicProviderDTO;
import com.carolcoral.apiserver.dto.oidc.OidcUserInfo;
import com.carolcoral.apiserver.entity.AiQuota;
import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiQuotaRepository;
import com.carolcoral.apiserver.repository.RoleRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC 登录服务（多服务商）
 * <p>
 * 基于标准 OpenID Connect Authorization Code Flow（含 PKCE），
 * 支持配置<strong>任意</strong>允许 OIDC 的服务商（TDP、Keycloak、Auth0、Casdoor…）作为登录方式，
 * 每个服务商独立配置 Issuer / Client ID / Client Secret / Scope / 回调地址等参数，
 * 登录页按启用状态渲染多个登录入口。
 * 参考文档：https://cnb.cool/tdp/docs/-/blob/docs/zh/oidc.md
 * </p>
 *
 * @author carolcoral
 */
@Tag(name = "OIDC 登录服务", description = "多服务商 OIDC 登录业务逻辑处理")
@Service
public class OidcService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OidcService.class);

    /** 系统配置键：OIDC 总开关（与配置列表共用同一套键，保持向后兼容） */
    private static final String KEY_ENABLED = "oidcEnabled";

    /** 系统配置键：OIDC 服务商列表（JSON 数组） */
    private static final String KEY_PROVIDERS = "oidcProviders";

    /** 系统配置键：OIDC 服务商列表配置说明 */
    private static final String KEY_PROVIDERS_DESC = "OIDC 服务商列表（JSON）";

    /** 默认配置值 */
    private static final String DEFAULT_PROVIDER_ID = "tdp";
    private static final String DEFAULT_PROVIDER_NAME = "TDP";
    private static final String DEFAULT_ISSUER = "https://tdp.fan/oidc";
    private static final String DEFAULT_SCOPE = "openid profile email tdp:role";
    private static final String DEFAULT_BUTTON_LABEL = "使用 TDP 登录";

    /** state/PKCE 缓存有效期（10 分钟） */
    private static final long STATE_TTL_SECONDS = 600;

    /** Discovery 文档缓存有效期（1 小时） */
    private static final long DISCOVERY_TTL_SECONDS = 3600;

    /** 待处理的授权请求（state -> 上下文） */
    private final Map<String, PendingAuthRequest> pendingRequests = new ConcurrentHashMap<>();

    /** Discovery 文档缓存（providerId -> 文档） */
    private final Map<String, CachedDiscovery> discoveryCache = new ConcurrentHashMap<>();

    private final SystemConfigService systemConfigService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AiQuotaRepository quotaRepository;
    private final PermissionService permissionService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 构造器
     */
    public OidcService(SystemConfigService systemConfigService,
                       UserRepository userRepository,
                       RoleRepository roleRepository,
                       AiQuotaRepository quotaRepository,
                       PermissionService permissionService,
                       JwtTokenUtil jwtTokenUtil,
                       ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.quotaRepository = quotaRepository;
        this.permissionService = permissionService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * 待处理的授权请求上下文（记录发起登录时使用的服务商与 PKCE/回调信息）
     */
    private static class PendingAuthRequest {
        private final String state;
        private final String providerId;
        private final String codeVerifier;
        private final String redirectUri;
        private final Instant createdAt;

        PendingAuthRequest(String state, String providerId, String codeVerifier, String redirectUri) {
            this.state = state;
            this.providerId = providerId;
            this.codeVerifier = codeVerifier;
            this.redirectUri = redirectUri;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(STATE_TTL_SECONDS));
        }
    }

    /**
     * Discovery 文档缓存项
     */
    private record CachedDiscovery(JsonNode document, Instant fetchedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plusSeconds(DISCOVERY_TTL_SECONDS));
        }
    }

    // ------------------------------------------------------------------ 配置

    /**
     * 读取完整 OIDC 配置（总开关 + 服务商列表）
     *
     * @return 配置DTO
     */
    @Operation(summary = "读取 OIDC 配置")
    public OidcConfigDTO getConfig() {
        OidcConfigDTO dto = new OidcConfigDTO();
        // 兼容历史单一服务商配置：未启用多服务商时，按旧键回显为一个服务商，避免配置丢失
        dto.setEnabled(parseBoolean(KEY_ENABLED, false));
        List<OidcProviderDTO> providers = loadProviders();
        if (providers.isEmpty()) {
            OidcProviderDTO legacy = loadLegacyProvider();
            if (legacy != null) {
                providers.add(legacy);
            }
        }
        providers.forEach(this::maskSecret);
        dto.setProviders(providers);
        return dto;
    }

    /**
     * 保存 OIDC 配置（整体覆盖式保存，未提交的 Secret 保持原值）
     *
     * @param dto 配置DTO
     */
    @Operation(summary = "保存 OIDC 配置")
    @Transactional
    public void saveConfig(OidcConfigDTO dto) {
        if (dto == null) {
            return;
        }
        if (dto.getEnabled() != null) {
            systemConfigService.saveConfig(KEY_ENABLED, String.valueOf(dto.getEnabled()), "是否启用 OIDC 登录");
        }
        List<OidcProviderDTO> incoming = dto.getProviders();
        if (incoming != null) {
            List<OidcProviderDTO> normalized = new ArrayList<>();
            List<String> usedIds = new ArrayList<>();
            for (OidcProviderDTO provider : incoming) {
                OidcProviderDTO existing = provider.getProviderId() == null ? null
                        : findProvider(loadProviders(), provider.getProviderId());
                OidcProviderDTO item = normalizeProvider(provider, existing);
                if (item == null) {
                    continue;
                }
                String base = item.getProviderId();
                if (usedIds.contains(base)) {
                    // 同一次提交内 ID 重复：跳过后者，避免覆盖
                    log.warn("OIDC 配置保存：服务商标识重复，已忽略: {}", base);
                    continue;
                }
                usedIds.add(base);
                normalized.add(item);
            }
            systemConfigService.saveConfig(KEY_PROVIDERS, writeProviders(normalized), KEY_PROVIDERS_DESC);
            // 服务商配置变更后清空 Discovery 缓存
            discoveryCache.clear();
            log.info("OIDC 配置已更新: enabled={}, providers={}", dto.getEnabled(),
                    normalized.stream().map(OidcProviderDTO::getProviderId).toList());
        }
    }

    /**
     * 判断 OIDC 登录是否至少有一个可用服务商
     *
     * @return 是否可用
     */
    public boolean isEnabledAndConfigured() {
        return !getAvailableProviders().isEmpty();
    }

    /**
     * 判断指定服务商是否可用（用于发起登录前的白名单校验）
     *
     * @param providerId 服务商标识
     * @return 是否可用
     */
    public boolean isProviderAvailable(String providerId) {
        return providerId != null && findProvider(getAvailableProviders(), providerId) != null;
    }

    /**
     * 获取 OIDC 公开配置（登录页使用）
     *
     * @return 公开配置
     */
    @Operation(summary = "获取 OIDC 公开配置")
    public OidcPublicConfigDTO getPublicConfig() {
        List<OidcPublicProviderDTO> list = new ArrayList<>();
        for (OidcProviderDTO provider : getAvailableProviders()) {
            String label = provider.getButtonLabel();
            list.add(new OidcPublicProviderDTO(provider.getProviderId(), provider.getName(), label));
        }
        return new OidcPublicConfigDTO(!list.isEmpty(), list);
    }

    /**
     * 列出所有已启用的可用服务商（总开关开启 + 配置完整）
     */
    private List<OidcProviderDTO> getAvailableProviders() {
        List<OidcProviderDTO> available = new ArrayList<>();
        if (!parseBoolean(KEY_ENABLED, false)) {
            return available;
        }
        List<OidcProviderDTO> providers = loadProviders();
        if (providers.isEmpty()) {
            // 兼容历史单一服务商配置：升级后尚未保存新列表时按旧键生效，避免登录入口中断
            OidcProviderDTO legacy = loadLegacyProvider();
            if (legacy != null) {
                providers.add(legacy);
            }
        }
        for (OidcProviderDTO provider : providers) {
            if (isComplete(provider)) {
                available.add(provider);
            }
        }
        return available;
    }

    /**
     * 配置是否完整可用：启用 + issuer/clientId/clientSecret 齐备
     */
    private boolean isComplete(OidcProviderDTO provider) {
        return provider != null
                && Boolean.TRUE.equals(provider.getEnabled())
                && notBlank(provider.getIssuerUri())
                && notBlank(provider.getClientId())
                && notBlank(provider.getClientSecret());
    }

    /**
     * 按标识查找服务商
     */
    private OidcProviderDTO findProvider(List<OidcProviderDTO> providers, String providerId) {
        if (providers == null || providerId == null) {
            return null;
        }
        for (OidcProviderDTO provider : providers) {
            if (providerId.equals(provider.getProviderId())) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 规范化单个服务商配置：补齐默认值、唯一化标识、处理 Secret 保留/清空、回填历史 Secret
     *
     * @param incoming 本次提交的服务商配置
     * @param existing 已存的服务商配置（可为空）
     * @return 规范化后的配置；providerId/clientId 非法时返回 null（忽略该项）
     */
    private OidcProviderDTO normalizeProvider(OidcProviderDTO incoming, OidcProviderDTO existing) {
        if (incoming == null) {
            return null;
        }
        String name = trimToNull(incoming.getName());
        String providerId = trimToNull(incoming.getProviderId());
        if (providerId == null) {
            // 未提供标识时按展示名称推导，仍无则忽略
            providerId = name == null ? null : name.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(java.util.Locale.ROOT);
        }
        if (providerId == null || providerId.isEmpty()) {
            log.warn("OIDC 配置保存：服务商标识为空，已忽略该服务商");
            return null;
        }
        OidcProviderDTO result = new OidcProviderDTO();
        result.setProviderId(providerId);
        result.setName(name != null ? name : providerId);
        result.setButtonLabel(trimToNull(incoming.getButtonLabel()) != null
                ? trimToNull(incoming.getButtonLabel())
                : result.getName());
        result.setEnabled(incoming.getEnabled() == null || incoming.getEnabled());
        result.setIssuerUri(trimToNull(incoming.getIssuerUri()));
        result.setRedirectUri(trimToNull(incoming.getRedirectUri()));
        result.setScope(trimToNull(incoming.getScope()) != null
                ? trimToNull(incoming.getScope())
                : DEFAULT_SCOPE);
        result.setAutoCreateUser(incoming.getAutoCreateUser() == null || incoming.getAutoCreateUser());
        result.setUsePkce(incoming.getUsePkce() == null || incoming.getUsePkce());

        // clientId 允许留空（沿用已存值），避免前端未改动时误清空
        String clientId = trimToNull(incoming.getClientId());
        if (clientId == null && existing != null) {
            clientId = existing.getClientId();
        }
        result.setClientId(clientId);

        // clientSecret：默认保持原值，仅在显式提供新值或标记 clear 时变更
        result.setClientSecret(resolveSecret(incoming, existing));
        return result;
    }

    /**
     * 解析 Client Secret 的写入值
     * <p>优先级：显式 clear → 本次提交的非空新值 → 已存值。</p>
     */
    private String resolveSecret(OidcProviderDTO incoming, OidcProviderDTO existing) {
        String status = trimToNull(incoming.getClientSecretStatus());
        if ("clear".equalsIgnoreCase(status)) {
            return null;
        }
        String provided = trimToNull(incoming.getClientSecret());
        if (provided != null && !"keep".equalsIgnoreCase(status)) {
            return provided;
        }
        return existing != null ? existing.getClientSecret() : provided;
    }

    /**
     * 去除 Secret 与敏感字段后返回（列表接口不回显密钥原文）
     */
    private void maskSecret(OidcProviderDTO provider) {
        boolean configured = notBlank(provider.getClientSecret());
        provider.setClientSecret(null);
        provider.setClientSecretConfigured(configured);
        if (provider.getClientSecretStatus() == null) {
            provider.setClientSecretStatus("keep");
        }
    }

    /**
     * 读取服务商列表（JSON 数组）
     */
    private List<OidcProviderDTO> loadProviders() {
        String raw = systemConfigService.getConfig(KEY_PROVIDERS);
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isArray()) {
                return new ArrayList<>();
            }
            List<OidcProviderDTO> providers = new ArrayList<>();
            for (JsonNode item : node) {
                providers.add(fromJson(item));
            }
            return providers;
        } catch (Exception e) {
            log.warn("解析 OIDC 服务商列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * JSON 节点 → 服务商配置
     */
    private OidcProviderDTO fromJson(JsonNode node) {
        OidcProviderDTO provider = new OidcProviderDTO();
        provider.setProviderId(textOf(node, "providerId"));
        provider.setName(textOf(node, "name"));
        provider.setButtonLabel(textOf(node, "buttonLabel"));
        provider.setEnabled(node.path("enabled").asBoolean(false));
        provider.setIssuerUri(textOf(node, "issuerUri"));
        provider.setClientId(textOf(node, "clientId"));
        provider.setClientSecret(textOf(node, "clientSecret"));
        provider.setRedirectUri(textOf(node, "redirectUri"));
        provider.setScope(textOf(node, "scope"));
        provider.setAutoCreateUser(node.path("autoCreateUser").asBoolean(true));
        provider.setUsePkce(node.path("usePkce").asBoolean(true));
        return provider;
    }

    /**
     * 服务商配置 → JSON 字符串
     */
    private String writeProviders(List<OidcProviderDTO> providers) {
        ArrayNode array = objectMapper.createArrayNode();
        for (OidcProviderDTO provider : providers) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("providerId", provider.getProviderId());
            node.put("name", provider.getName());
            node.put("buttonLabel", provider.getButtonLabel());
            node.put("enabled", Boolean.TRUE.equals(provider.getEnabled()));
            node.put("issuerUri", provider.getIssuerUri());
            node.put("clientId", provider.getClientId());
            node.put("clientSecret", provider.getClientSecret());
            node.put("redirectUri", provider.getRedirectUri());
            node.put("scope", provider.getScope());
            node.put("autoCreateUser", !Boolean.FALSE.equals(provider.getAutoCreateUser()));
            node.put("usePkce", !Boolean.FALSE.equals(provider.getUsePkce()));
            array.add(node);
        }
        return array.toString();
    }

    /**
     * 读取历史单一服务商配置（旧版键），升级后首次读取时用于回显/迁移
     *
     * @return 历史配置；未配置过返回 null
     */
    private OidcProviderDTO loadLegacyProvider() {
        String clientId = trimToNull(systemConfigService.getConfig("oidcClientId"));
        String clientSecret = trimToNull(systemConfigService.getConfig("oidcClientSecret"));
        String issuer = trimToNull(systemConfigService.getConfig("oidcIssuerUri"));
        if (clientId == null && clientSecret == null && issuer == null) {
            return null;
        }
        OidcProviderDTO provider = new OidcProviderDTO();
        provider.setProviderId(trimToNull(systemConfigService.getConfig("oidcProvider")) != null
                ? trimToNull(systemConfigService.getConfig("oidcProvider"))
                : DEFAULT_PROVIDER_ID);
        provider.setName(provider.getProviderId().toUpperCase(java.util.Locale.ROOT));
        String label = trimToNull(systemConfigService.getConfig("oidcButtonLabel"));
        provider.setButtonLabel(label != null ? label : DEFAULT_BUTTON_LABEL);
        provider.setEnabled(parseBoolean(KEY_ENABLED, false));
        provider.setIssuerUri(issuer != null ? issuer : DEFAULT_ISSUER);
        provider.setClientId(clientId);
        provider.setClientSecret(clientSecret);
        provider.setRedirectUri(trimToNull(systemConfigService.getConfig("oidcRedirectUri")));
        String scope = trimToNull(systemConfigService.getConfig("oidcScope"));
        provider.setScope(scope != null ? scope : DEFAULT_SCOPE);
        provider.setAutoCreateUser(parseBoolean("oidcAutoCreateUser", true));
        provider.setUsePkce(parseBoolean("oidcUsePkce", true));
        return provider;
    }

    /**
     * 清理过期的待处理授权请求
     */
    private void evictExpiredRequests() {
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    // -------------------------------------------------------------- 授权与回调

    /**
     * 生成授权跳转 URL（Authorization Code Flow + PKCE）
     *
     * @param providerId 服务商标识
     * @param baseUrl    站点基础地址（用于自动推导回调地址，可为空）
     * @return 授权 URL
     */
    @Operation(summary = "生成 OIDC 授权跳转 URL")
    public String buildAuthorizationUrl(String providerId, String baseUrl) {
        OidcProviderDTO provider = resolveAvailableProvider(providerId);
        evictExpiredRequests();

        JsonNode discovery = fetchDiscovery(provider);
        String authorizationEndpoint = textOf(discovery, "authorization_endpoint");
        if (authorizationEndpoint == null || authorizationEndpoint.isEmpty()) {
            throw new IllegalStateException("无法从 Discovery 文档获取授权端点");
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        String redirectUri = resolveRedirectUri(provider, baseUrl);
        boolean usePkce = provider.getUsePkce() == null || provider.getUsePkce();

        String codeVerifier = null;
        String codeChallenge = null;
        if (usePkce) {
            codeVerifier = generateCodeVerifier();
            codeChallenge = generateCodeChallenge(codeVerifier);
        }

        pendingRequests.put(state, new PendingAuthRequest(state, provider.getProviderId(), codeVerifier, redirectUri));

        StringBuilder url = new StringBuilder(authorizationEndpoint);
        url.append(authorizationEndpoint.contains("?") ? "&" : "?");
        url.append("client_id=").append(enc(provider.getClientId()));
        url.append("&redirect_uri=").append(enc(redirectUri));
        url.append("&response_type=code");
        url.append("&scope=").append(enc(provider.getScope()));
        url.append("&state=").append(enc(state));
        if (usePkce && codeChallenge != null) {
            url.append("&code_challenge=").append(enc(codeChallenge));
            url.append("&code_challenge_method=S256");
        }
        log.info("生成 OIDC 授权 URL: provider={}, state={}, redirectUri={}, pkce={}",
                provider.getProviderId(), state, redirectUri, usePkce);
        return url.toString();
    }

    /**
     * 处理 OIDC 回调：换取 Token、获取用户信息、绑定/创建本地账号并签发 JWT
     *
     * @param providerId 服务商标识（可为空，为空时按 state 记录的服务商处理）
     * @param code       授权码
     * @param state      状态参数
     * @return 登录响应
     */
    @Operation(summary = "处理 OIDC 回调并完成登录")
    @Transactional
    public ApiResponse<LoginResponse> handleCallback(String providerId, String code, String state) {
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("缺少授权码 code");
        }
        if (state == null || state.isEmpty()) {
            return ApiResponse.error("缺少 state 参数");
        }
        PendingAuthRequest pending = pendingRequests.remove(state);
        if (pending == null) {
            return ApiResponse.error("state 无效或已过期，请重新发起登录");
        }
        if (pending.isExpired()) {
            return ApiResponse.error("登录请求已过期，请重新发起登录");
        }
        // 回调携带的 providerId 仅作校验：以 state 中记录的为准，防止串用其他服务商配置
        if (providerId != null && !providerId.isEmpty() && !providerId.equals(pending.providerId)) {
            log.warn("OIDC 回调服务商不匹配: callback={}, state={}", providerId, pending.providerId);
            return ApiResponse.error("OIDC 服务商不匹配，请重新发起登录");
        }

        OidcProviderDTO provider;
        try {
            provider = resolveAvailableProvider(pending.providerId);
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        }

        try {
            JsonNode discovery = fetchDiscovery(provider);
            String tokenEndpoint = textOf(discovery, "token_endpoint");
            if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
                return ApiResponse.error("无法从 Discovery 文档获取 Token 端点");
            }

            // 1. 用 code 换取 Token
            JsonNode tokenResponse = exchangeCodeForToken(provider, tokenEndpoint, code, pending);
            if (tokenResponse == null) {
                return ApiResponse.error("换取访问令牌失败");
            }
            String accessToken = textOf(tokenResponse, "access_token");
            String idToken = textOf(tokenResponse, "id_token");
            if (accessToken == null || accessToken.isEmpty()) {
                return ApiResponse.error("OIDC 未返回 access_token");
            }

            // 2. 获取用户信息：优先使用 UserInfo 端点，回退解析 ID Token
            OidcUserInfo userInfo = fetchUserInfo(discovery, accessToken);
            if (userInfo == null && idToken != null) {
                userInfo = parseIdToken(provider, idToken);
            }
            if (userInfo == null || userInfo.getSub() == null || userInfo.getSub().isEmpty()) {
                return ApiResponse.error("无法获取 OIDC 用户信息");
            }

            // 3. 绑定或创建本地账号
            return bindOrCreateUser(provider, userInfo);

        } catch (Exception e) {
            log.error("处理 OIDC 回调失败: {}", e.getMessage(), e);
            return ApiResponse.error("OIDC 登录失败：" + e.getMessage());
        }
    }

    /**
     * 解析并校验可用服务商
     */
    private OidcProviderDTO resolveAvailableProvider(String providerId) {
        if (!parseBoolean(KEY_ENABLED, false)) {
            throw new IllegalStateException("OIDC 登录未启用或配置不完整");
        }
        List<OidcProviderDTO> available = getAvailableProviders();
        OidcProviderDTO provider = null;
        if (providerId == null || providerId.isEmpty()) {
            if (available.size() == 1) {
                provider = available.get(0);
            }
        } else {
            provider = findProvider(available, providerId);
        }
        if (provider == null) {
            throw new IllegalStateException(providerId == null || providerId.isEmpty()
                    ? "OIDC 登录未启用或配置不完整"
                    : "OIDC 服务商不存在或未启用: " + providerId);
        }
        return provider;
    }

    /**
     * 用授权码换取 Token
     */
    private JsonNode exchangeCodeForToken(OidcProviderDTO provider, String tokenEndpoint,
                                          String code, PendingAuthRequest pending) {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=authorization_code");
        form.append("&code=").append(enc(code));
        form.append("&redirect_uri=").append(enc(pending.redirectUri));
        form.append("&client_id=").append(enc(provider.getClientId()));
        form.append("&client_secret=").append(enc(provider.getClientSecret()));
        if (pending.codeVerifier != null) {
            form.append("&code_verifier=").append(enc(pending.codeVerifier));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OIDC Token 端点返回异常状态码: {}, body={}", response.statusCode(), response.body());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("请求 OIDC Token 端点失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 UserInfo 端点获取用户信息
     */
    private OidcUserInfo fetchUserInfo(JsonNode discovery, String accessToken) {
        String userInfoEndpoint = textOf(discovery, "userinfo_endpoint");
        if (userInfoEndpoint == null || userInfoEndpoint.isEmpty()) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(userInfoEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OIDC UserInfo 端点返回异常状态码: {}", response.statusCode());
                return null;
            }
            return mapUserInfo(objectMapper.readTree(response.body()));
        } catch (Exception e) {
            log.error("请求 OIDC UserInfo 端点失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 ID Token（仅做载荷解析，不校验签名——签名校验交由 TLS + issuer 保障）
     * <p>为增强安全性，此处会校验 iss 与 exp 声明。</p>
     */
    private OidcUserInfo parseIdToken(OidcProviderDTO provider, String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(payload);

            // 校验 exp
            JsonNode expNode = claims.get("exp");
            if (expNode != null && expNode.isNumber()) {
                if (Instant.now().getEpochSecond() > expNode.asLong()) {
                    log.warn("ID Token 已过期");
                    return null;
                }
            }
            // 校验 iss
            String expectedIssuer = provider.getIssuerUri();
            String iss = textOf(claims, "iss");
            if (iss != null && !iss.equals(expectedIssuer)) {
                log.warn("ID Token issuer 不匹配: expected={}, actual={}", expectedIssuer, iss);
                return null;
            }
            return mapUserInfo(claims);
        } catch (Exception e) {
            log.error("解析 ID Token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 OIDC 声明的 JSON 映射为用户信息对象
     */
    private OidcUserInfo mapUserInfo(JsonNode node) {
        OidcUserInfo info = new OidcUserInfo();
        info.setSub(textOf(node, "sub"));
        info.setPreferredUsername(textOf(node, "preferred_username"));
        info.setName(textOf(node, "name"));
        info.setEmail(textOf(node, "email"));
        JsonNode verified = node.get("email_verified");
        if (verified != null && verified.isBoolean()) {
            info.setEmailVerified(verified.asBoolean());
        }
        info.setPicture(textOf(node, "picture"));
        info.setTdpRole(textOf(node, "tdp_role"));
        return info;
    }

    /**
     * 绑定已有账号或按配置自动创建本地账号，并签发 JWT
     */
    private ApiResponse<LoginResponse> bindOrCreateUser(OidcProviderDTO provider, OidcUserInfo userInfo) {
        String sub = userInfo.getSub();
        // 多服务商下的 sub 可能碰撞，统一带上服务商前缀作为本地唯一标识
        String accountKey = buildAccountKey(provider.getProviderId(), sub);
        Optional<User> existingOpt = userRepository.findByOidcSub(accountKey);

        User user;
        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (!user.getEnabled()) {
                return ApiResponse.error(403, "用户已被禁用");
            }
            // 同步基础资料
            boolean changed = false;
            if (userInfo.getEmail() != null && !userInfo.getEmail().isEmpty()
                    && !userInfo.getEmail().equals(user.getEmail())
                    && !userRepository.existsByEmail(userInfo.getEmail())) {
                user.setEmail(userInfo.getEmail());
                changed = true;
            }
            if (changed) {
                user = userRepository.save(user);
            }
        } else {
            // 首次登录：优先按「带服务商前缀」的账号标识匹配，兼容历史库中保存的裸 sub
            user = resolveLegacyBoundUser(sub, accountKey);
            if (user == null && userInfo.getEmail() != null && !userInfo.getEmail().isEmpty()) {
                Optional<User> byEmail = userRepository.findByEmail(userInfo.getEmail());
                if (byEmail.isPresent()) {
                    user = byEmail.get();
                    user.setOidcSub(accountKey);
                    user.setOidcProvider(provider.getProviderId());
                    user = userRepository.save(user);
                    log.info("OIDC 首次登录，已按邮箱绑定已有账号: {}", user.getUsername());
                }
            }
            if (user == null) {
                boolean autoCreate = provider.getAutoCreateUser() == null || provider.getAutoCreateUser();
                if (!autoCreate) {
                    return ApiResponse.error(403, "OIDC 账号未绑定本地用户，且系统未开启自动创建账号");
                }
                user = createUserFromOidc(provider, userInfo, accountKey);
                if (user == null) {
                    return ApiResponse.error("自动创建本地账号失败");
                }
            }
        }

        return buildLoginResponse(user);
    }

    /**
     * 构建本地账号标识：{providerId}:{sub}，避免多服务商 sub 冲突
     */
    private String buildAccountKey(String providerId, String sub) {
        if (providerId == null || providerId.isEmpty() || sub == null) {
            return sub;
        }
        return providerId + ":" + sub;
    }

    /**
     * 兼容历史数据：查找以裸 sub 绑定的账号并升级为带服务商前缀的标识
     */
    private User resolveLegacyBoundUser(String sub, String accountKey) {
        Optional<User> legacyOpt = userRepository.findByOidcSub(sub);
        if (legacyOpt.isEmpty()) {
            return null;
        }
        User legacy = legacyOpt.get();
        if (!legacy.getEnabled()) {
            return null;
        }
        legacy.setOidcSub(accountKey);
        return userRepository.save(legacy);
    }

    /**
     * 根据 OIDC 用户信息创建本地账号
     */
    private User createUserFromOidc(OidcProviderDTO provider, OidcUserInfo userInfo, String accountKey) {
        User user = new User();
        user.setUsername(generateUsername(provider, userInfo, accountKey));
        // OIDC 账号无本地密码，写入随机占位密码（BCrypt 不可逆，无法用于常规密码登录）
        user.setPassword(UUID.randomUUID().toString());
        user.setEmail(userInfo.getEmail());
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);
        user.setOidcSub(accountKey);
        user.setOidcProvider(provider.getProviderId());
        user.setOidcAccount(true);
        resolveDefaultRoleId(user);

        User saved = userRepository.save(user);
        // 为新用户创建默认 AI 额度（与本地注册保持一致）
        try {
            AiQuota quota = new AiQuota();
            quota.setUser(saved);
            quota.setTokenLimit(1_000_000L);
            quota.setTokenUsed(0L);
            quota.setTimeWindowSeconds(18000);
            quota.setWindowStart(java.time.LocalDateTime.now());
            quota.setStatus(true);
            quotaRepository.save(quota);
        } catch (Exception e) {
            log.warn("为 OIDC 新用户创建默认 AI 额度失败: {}", e.getMessage());
        }
        log.info("OIDC 首次登录，已自动创建本地账号: {}", saved.getUsername());
        return saved;
    }

    /**
     * 生成唯一用户名（优先 preferred_username / name，冲突时追加随机后缀）
     */
    private String generateUsername(OidcProviderDTO provider, OidcUserInfo userInfo, String accountKey) {
        String base = userInfo.getPreferredUsername();
        if (base == null || base.trim().isEmpty()) {
            base = userInfo.getName();
        }
        if (base == null || base.trim().isEmpty()) {
            if (userInfo.getEmail() != null && userInfo.getEmail().contains("@")) {
                base = userInfo.getEmail().substring(0, userInfo.getEmail().indexOf('@'));
            } else {
                base = provider.getProviderId() + "_" + userInfo.getSub();
            }
        }
        // 仅保留安全字符
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    /**
     * 根据角色枚举解析默认角色ID
     */
    private void resolveDefaultRoleId(User user) {
        if (user.getRoleId() != null) {
            return;
        }
        try {
            // 与本地注册保持一致：优先使用默认角色，其次回退 ROLE_USER
            Optional<Role> defaultRole = roleRepository.findByIsDefaultTrue();
            if (defaultRole.isPresent()) {
                user.setRoleId(defaultRole.get().getId());
            } else {
                roleRepository.findByCode("ROLE_USER")
                        .ifPresent(r -> user.setRoleId(r.getId()));
            }
        } catch (Exception e) {
            log.warn("解析默认角色ID失败: {}", e.getMessage());
        }
    }

    /**
     * 构建登录响应（与本地密码登录一致）
     */
    private ApiResponse<LoginResponse> buildLoginResponse(User user) {
        String token = jwtTokenUtil.generateToken(user, user.getId(), user.getRole().name());

        java.util.List<String> permList = new java.util.ArrayList<>();
        Long roleId = user.getRoleId();
        String roleName = null;
        String roleCode = null;
        if (roleId != null) {
            Set<String> permCodes = permissionService.getUserPermissionCodes(
                    java.util.Collections.singletonList(roleId));
            permList = new java.util.ArrayList<>(permCodes);
            Optional<Role> roleOpt = roleRepository.findById(roleId);
            if (roleOpt.isPresent()) {
                Role r = roleOpt.get();
                roleName = r.getName();
                roleCode = r.getCode();
            }
        }

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .roleId(roleId)
                .roleName(roleName)
                .roleCode(roleCode)
                .language(user.getLanguage())
                .permissions(permList)
                .expiresIn(jwtTokenUtil.getExpiration())
                .build();

        log.info("OIDC 登录成功: {}", user.getUsername());
        return ApiResponse.success(response);
    }

    /**
     * 获取 Discovery 文档（按服务商缓存，有效期 1 小时）
     */
    private JsonNode fetchDiscovery(OidcProviderDTO provider) {
        String providerId = provider.getProviderId();
        CachedDiscovery cached = discoveryCache.get(providerId);
        if (cached != null && !cached.isExpired()) {
            return cached.document();
        }
        String issuer = provider.getIssuerUri();
        String discoveryUrl = issuer.endsWith("/")
                ? issuer + ".well-known/openid-configuration"
                : issuer + "/.well-known/openid-configuration";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(discoveryUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Discovery 文档请求失败，状态码: " + response.statusCode());
            }
            JsonNode doc = objectMapper.readTree(response.body());
            discoveryCache.put(providerId, new CachedDiscovery(doc, Instant.now()));
            return doc;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("获取 OIDC Discovery 文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 推导回调地址：优先使用服务商显式配置，否则基于站点配置或请求地址推导
     */
    private String resolveRedirectUri(OidcProviderDTO provider, String baseUrl) {
        if (notBlank(provider.getRedirectUri())) {
            return provider.getRedirectUri();
        }
        // 依次回退：systemConfig 的 siteBaseUrl -> 本次请求的 baseUrl
        String siteBaseUrl = systemConfigService.getConfig("siteBaseUrl");
        String base = notBlank(siteBaseUrl)
                ? siteBaseUrl.trim()
                : (baseUrl != null ? baseUrl.trim() : "");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/auth/oidc/callback";
    }

    /**
     * 生成 PKCE code_verifier（43-128 位随机 URL 安全字符串）
     */
    private String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 生成 PKCE code_challenge（S256）
     */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("生成 PKCE code_challenge 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析布尔型配置
     */
    private boolean parseBoolean(String key, boolean defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 字符串是否非空白
     */
    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 去空白，空白返回 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 从 JSON 节点安全读取文本字段
     */
    private String textOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /**
     * URL 编码
     */
    private String enc(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
