/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.LoginResponse;
import com.carolcoral.apiserver.dto.oidc.OidcConfigDTO;
import com.carolcoral.apiserver.dto.oidc.OidcPublicConfigDTO;
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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TDP OIDC 登录服务
 * <p>
 * 基于标准 OpenID Connect Authorization Code Flow（含 PKCE），
 * 允许用户使用 TDP 账号（https://tdp.fan/oidc）登录本系统。
 * 参考文档：https://cnb.cool/tdp/docs/-/blob/docs/zh/oidc.md
 * </p>
 *
 * @author carolcoral
 */
@Tag(name = "OIDC 登录服务", description = "TDP OIDC 登录业务逻辑处理")
@Service
public class OidcService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OidcService.class);

    /** 系统配置键前缀 */
    private static final String KEY_ENABLED = "oidcEnabled";
    private static final String KEY_PROVIDER = "oidcProvider";
    private static final String KEY_ISSUER = "oidcIssuerUri";
    private static final String KEY_CLIENT_ID = "oidcClientId";
    private static final String KEY_CLIENT_SECRET = "oidcClientSecret";
    private static final String KEY_REDIRECT_URI = "oidcRedirectUri";
    private static final String KEY_SCOPE = "oidcScope";
    private static final String KEY_AUTO_CREATE = "oidcAutoCreateUser";
    private static final String KEY_BUTTON_LABEL = "oidcButtonLabel";
    private static final String KEY_USE_PKCE = "oidcUsePkce";

    /** 默认配置值 */
    private static final String DEFAULT_PROVIDER = "tdp";
    private static final String DEFAULT_ISSUER = "https://tdp.fan/oidc";
    private static final String DEFAULT_SCOPE = "openid profile email tdp:role";
    private static final String DEFAULT_BUTTON_LABEL = "使用 TDP 登录";

    /** state/PKCE 缓存有效期（10 分钟） */
    private static final long STATE_TTL_SECONDS = 600;

    /** 待处理的授权请求（state -> 上下文） */
    private final Map<String, PendingAuthRequest> pendingRequests = new ConcurrentHashMap<>();

    private final SystemConfigService systemConfigService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AiQuotaRepository quotaRepository;
    private final PermissionService permissionService;
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    /** 缓存的 Discovery 文档 */
    private volatile JsonNode discoveryDocument;
    private volatile Instant discoveryFetchedAt;

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
     * 待处理的授权请求上下文
     */
    private static class PendingAuthRequest {
        private final String state;
        private final String codeVerifier;
        private final String redirectUri;
        private final Instant createdAt;

        PendingAuthRequest(String state, String codeVerifier, String redirectUri) {
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.redirectUri = redirectUri;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(STATE_TTL_SECONDS));
        }
    }

    /**
     * 读取完整 OIDC 配置
     *
     * @return 配置DTO
     */
    @Operation(summary = "读取 OIDC 配置")
    public OidcConfigDTO getConfig() {
        OidcConfigDTO dto = new OidcConfigDTO();
        dto.setEnabled(parseBoolean(KEY_ENABLED, false));
        dto.setProvider(getConfigOrDefault(KEY_PROVIDER, DEFAULT_PROVIDER));
        dto.setIssuerUri(getConfigOrDefault(KEY_ISSUER, DEFAULT_ISSUER));
        dto.setClientId(systemConfigService.getConfig(KEY_CLIENT_ID));
        // 出于安全考虑，不回显 clientSecret 原文，由前端根据是否已配置决定占位
        dto.setClientSecret(null);
        dto.setRedirectUri(systemConfigService.getConfig(KEY_REDIRECT_URI));
        dto.setScope(getConfigOrDefault(KEY_SCOPE, DEFAULT_SCOPE));
        dto.setAutoCreateUser(parseBoolean(KEY_AUTO_CREATE, true));
        dto.setButtonLabel(getConfigOrDefault(KEY_BUTTON_LABEL, DEFAULT_BUTTON_LABEL));
        dto.setUsePkce(parseBoolean(KEY_USE_PKCE, true));
        return dto;
    }

    /**
     * 判断 OIDC 登录是否已启用且配置完整
     *
     * @return 是否可用
     */
    public boolean isEnabledAndConfigured() {
        boolean enabled = parseBoolean(KEY_ENABLED, false);
        String clientId = systemConfigService.getConfig(KEY_CLIENT_ID);
        String clientSecret = systemConfigService.getConfig(KEY_CLIENT_SECRET);
        String issuer = systemConfigService.getConfig(KEY_ISSUER);
        return enabled
                && clientId != null && !clientId.isEmpty()
                && clientSecret != null && !clientSecret.isEmpty()
                && issuer != null && !issuer.isEmpty();
    }

    /**
     * 获取 OIDC 公开配置（登录页使用）
     *
     * @return 公开配置
     */
    @Operation(summary = "获取 OIDC 公开配置")
    public OidcPublicConfigDTO getPublicConfig() {
        boolean available = isEnabledAndConfigured();
        return new OidcPublicConfigDTO(
                available,
                getConfigOrDefault(KEY_PROVIDER, DEFAULT_PROVIDER),
                getConfigOrDefault(KEY_BUTTON_LABEL, DEFAULT_BUTTON_LABEL));
    }

    /**
     * 保存 OIDC 配置
     *
     * @param dto 配置DTO
     */
    @Operation(summary = "保存 OIDC 配置")
    @Transactional
    public void saveConfig(OidcConfigDTO dto) {
        if (dto.getEnabled() != null) {
            systemConfigService.saveConfig(KEY_ENABLED, String.valueOf(dto.getEnabled()), "是否启用 TDP OIDC 登录");
        }
        if (dto.getProvider() != null && !dto.getProvider().trim().isEmpty()) {
            systemConfigService.saveConfig(KEY_PROVIDER, dto.getProvider().trim(), "OIDC 提供方标识");
        }
        if (dto.getIssuerUri() != null) {
            systemConfigService.saveConfig(KEY_ISSUER, dto.getIssuerUri().trim(), "OIDC Issuer 地址");
        }
        if (dto.getClientId() != null) {
            systemConfigService.saveConfig(KEY_CLIENT_ID, dto.getClientId().trim(), "OIDC Client ID");
        }
        // clientSecret 允许留空表示保持原值不变
        if (dto.getClientSecret() != null && !dto.getClientSecret().trim().isEmpty()) {
            systemConfigService.saveConfig(KEY_CLIENT_SECRET, dto.getClientSecret().trim(), "OIDC Client Secret");
        }
        if (dto.getRedirectUri() != null) {
            systemConfigService.saveConfig(KEY_REDIRECT_URI, dto.getRedirectUri().trim(), "OIDC 回调地址（留空自动推导）");
        }
        if (dto.getScope() != null && !dto.getScope().trim().isEmpty()) {
            systemConfigService.saveConfig(KEY_SCOPE, dto.getScope().trim(), "OIDC 请求 Scope");
        }
        if (dto.getAutoCreateUser() != null) {
            systemConfigService.saveConfig(KEY_AUTO_CREATE, String.valueOf(dto.getAutoCreateUser()), "OIDC 首次登录自动创建账号");
        }
        if (dto.getButtonLabel() != null && !dto.getButtonLabel().trim().isEmpty()) {
            systemConfigService.saveConfig(KEY_BUTTON_LABEL, dto.getButtonLabel().trim(), "OIDC 登录按钮显示名称");
        }
        if (dto.getUsePkce() != null) {
            systemConfigService.saveConfig(KEY_USE_PKCE, String.valueOf(dto.getUsePkce()), "OIDC 是否启用 PKCE");
        }
        // 配置变更后失效 discovery 缓存
        this.discoveryDocument = null;
        this.discoveryFetchedAt = null;
        log.info("OIDC 配置已更新: enabled={}, issuer={}, clientId={}",
                dto.getEnabled(), dto.getIssuerUri(), dto.getClientId());
    }

    /**
     * 清理过期的待处理授权请求
     */
    private void evictExpiredRequests() {
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 生成授权跳转 URL（Authorization Code Flow + PKCE）
     *
     * @param baseUrl 站点基础地址（用于自动推导回调地址，可为空）
     * @return 授权 URL
     */
    @Operation(summary = "生成 OIDC 授权跳转 URL")
    public String buildAuthorizationUrl(String baseUrl) {
        if (!isEnabledAndConfigured()) {
            throw new IllegalStateException("OIDC 登录未启用或配置不完整");
        }
        evictExpiredRequests();

        JsonNode discovery = fetchDiscovery();
        String authorizationEndpoint = textOf(discovery, "authorization_endpoint");
        if (authorizationEndpoint == null || authorizationEndpoint.isEmpty()) {
            throw new IllegalStateException("无法从 Discovery 文档获取授权端点");
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        String redirectUri = resolveRedirectUri(baseUrl);
        boolean usePkce = parseBoolean(KEY_USE_PKCE, true);

        String codeVerifier = null;
        String codeChallenge = null;
        if (usePkce) {
            codeVerifier = generateCodeVerifier();
            codeChallenge = generateCodeChallenge(codeVerifier);
        }

        pendingRequests.put(state, new PendingAuthRequest(state, codeVerifier, redirectUri));

        StringBuilder url = new StringBuilder(authorizationEndpoint);
        url.append(authorizationEndpoint.contains("?") ? "&" : "?");
        url.append("client_id=").append(enc(getConfigOrDefault(KEY_CLIENT_ID, "")));
        url.append("&redirect_uri=").append(enc(redirectUri));
        url.append("&response_type=code");
        url.append("&scope=").append(enc(getConfigOrDefault(KEY_SCOPE, DEFAULT_SCOPE)));
        url.append("&state=").append(enc(state));
        if (usePkce && codeChallenge != null) {
            url.append("&code_challenge=").append(enc(codeChallenge));
            url.append("&code_challenge_method=S256");
        }
        log.info("生成 OIDC 授权 URL: state={}, redirectUri={}, pkce={}", state, redirectUri, usePkce);
        return url.toString();
    }

    /**
     * 处理 OIDC 回调：换取 Token、获取用户信息、绑定/创建本地账号并签发 JWT
     *
     * @param code  授权码
     * @param state 状态参数
     * @return 登录响应
     */
    @Operation(summary = "处理 OIDC 回调并完成登录")
    @Transactional
    public ApiResponse<LoginResponse> handleCallback(String code, String state) {
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

        try {
            JsonNode discovery = fetchDiscovery();
            String tokenEndpoint = textOf(discovery, "token_endpoint");
            if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
                return ApiResponse.error("无法从 Discovery 文档获取 Token 端点");
            }

            // 1. 用 code 换取 Token
            JsonNode tokenResponse = exchangeCodeForToken(tokenEndpoint, code, pending);
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
                userInfo = parseIdToken(idToken);
            }
            if (userInfo == null || userInfo.getSub() == null || userInfo.getSub().isEmpty()) {
                return ApiResponse.error("无法获取 OIDC 用户信息");
            }

            // 3. 绑定或创建本地账号
            return bindOrCreateUser(userInfo);

        } catch (Exception e) {
            log.error("处理 OIDC 回调失败: {}", e.getMessage(), e);
            return ApiResponse.error("OIDC 登录失败：" + e.getMessage());
        }
    }

    /**
     * 用授权码换取 Token
     */
    private JsonNode exchangeCodeForToken(String tokenEndpoint, String code, PendingAuthRequest pending) {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=authorization_code");
        form.append("&code=").append(enc(code));
        form.append("&redirect_uri=").append(enc(pending.redirectUri));
        form.append("&client_id=").append(enc(getConfigOrDefault(KEY_CLIENT_ID, "")));
        form.append("&client_secret=").append(enc(getConfigOrDefault(KEY_CLIENT_SECRET, "")));
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
    private OidcUserInfo parseIdToken(String idToken) {
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
            String expectedIssuer = getConfigOrDefault(KEY_ISSUER, DEFAULT_ISSUER);
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
    private ApiResponse<LoginResponse> bindOrCreateUser(OidcUserInfo userInfo) {
        String sub = userInfo.getSub();
        Optional<User> existingOpt = userRepository.findByOidcSub(sub);

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
            // 首次登录：尝试按邮箱匹配已有账号并绑定，否则按配置自动建号
            user = null;
            if (userInfo.getEmail() != null && !userInfo.getEmail().isEmpty()) {
                Optional<User> byEmail = userRepository.findByEmail(userInfo.getEmail());
                if (byEmail.isPresent()) {
                    user = byEmail.get();
                    user.setOidcSub(sub);
                    user.setOidcProvider(getConfigOrDefault(KEY_PROVIDER, DEFAULT_PROVIDER));
                    user = userRepository.save(user);
                    log.info("OIDC 首次登录，已按邮箱绑定已有账号: {}", user.getUsername());
                }
            }
            if (user == null) {
                boolean autoCreate = parseBoolean(KEY_AUTO_CREATE, true);
                if (!autoCreate) {
                    return ApiResponse.error(403, "OIDC 账号未绑定本地用户，且系统未开启自动创建账号");
                }
                user = createUserFromOidc(userInfo, sub);
                if (user == null) {
                    return ApiResponse.error("自动创建本地账号失败");
                }
            }
        }

        return buildLoginResponse(user);
    }

    /**
     * 根据 OIDC 用户信息创建本地账号
     */
    private User createUserFromOidc(OidcUserInfo userInfo, String sub) {
        User user = new User();
        user.setUsername(generateUsername(userInfo, sub));
        // OIDC 账号无本地密码，写入随机占位密码（BCrypt 不可逆，无法用于常规密码登录）
        user.setPassword(UUID.randomUUID().toString());
        user.setEmail(userInfo.getEmail());
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);
        user.setOidcSub(sub);
        user.setOidcProvider(getConfigOrDefault(KEY_PROVIDER, DEFAULT_PROVIDER));
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
    private String generateUsername(OidcUserInfo userInfo, String sub) {
        String base = userInfo.getPreferredUsername();
        if (base == null || base.trim().isEmpty()) {
            base = userInfo.getName();
        }
        if (base == null || base.trim().isEmpty()) {
            if (userInfo.getEmail() != null && userInfo.getEmail().contains("@")) {
                base = userInfo.getEmail().substring(0, userInfo.getEmail().indexOf('@'));
            } else {
                base = "tdp_" + sub;
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
     * 获取 Discovery 文档（带内存缓存，有效期 1 小时）
     */
    private JsonNode fetchDiscovery() {
        JsonNode cached = discoveryDocument;
        if (cached != null && discoveryFetchedAt != null
                && Instant.now().isBefore(discoveryFetchedAt.plusSeconds(3600))) {
            return cached;
        }
        String issuer = getConfigOrDefault(KEY_ISSUER, DEFAULT_ISSUER);
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
            this.discoveryDocument = doc;
            this.discoveryFetchedAt = Instant.now();
            return doc;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("获取 OIDC Discovery 文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 推导回调地址：优先使用显式配置，否则基于站点配置或请求地址推导
     */
    private String resolveRedirectUri(String baseUrl) {
        String configured = systemConfigService.getConfig(KEY_REDIRECT_URI);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        // 依次回退：systemConfig 的 siteBaseUrl -> 本次请求的 baseUrl
        String siteBaseUrl = systemConfigService.getConfig("siteBaseUrl");
        String base = (siteBaseUrl != null && !siteBaseUrl.trim().isEmpty())
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
     * 读取配置，不存在时返回默认值
     */
    private String getConfigOrDefault(String key, String defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
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
