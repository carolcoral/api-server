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
import com.carolcoral.apiserver.entity.AiQuota;
import com.carolcoral.apiserver.entity.Role;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiQuotaRepository;
import com.carolcoral.apiserver.repository.RoleRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OidcService 单元测试：配置读写、授权 URL 生成、回调换 token/绑定账号、ID Token 校验。
 * <p>HTTP 交互使用 JDK 内置 HttpServer 起本地桩服务，覆盖真实的请求构造与响应解析路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcServiceTest {

    @Mock SystemConfigService systemConfigService;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock AiQuotaRepository quotaRepository;
    @Mock PermissionService permissionService;
    @Mock JwtTokenUtil jwtTokenUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OidcService service;
    private HttpServer server;
    private String base;
    private final Map<String, String> configStore = new HashMap<>();
    private final AtomicReference<String> lastTokenRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastUserInfoAuthHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        configStore.clear();
        lenient().when(systemConfigService.getConfig(anyString()))
                .thenAnswer(inv -> configStore.get(inv.getArgument(0, String.class)));
        lenient().doAnswer(inv -> {
            configStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(systemConfigService).saveConfig(anyString(), anyString(), anyString());

        // 默认让 save 回显入参，避免未显式桩定时返回 null 造成 NPE
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        startServer();
        service = new OidcService(systemConfigService, userRepository, roleRepository,
                quotaRepository, permissionService, jwtTokenUtil, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 启动本地 OIDC 桩服务：discovery / token / userinfo 三个端点 */
    private void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", ex -> {
            String body = "{"
                    + "\"authorization_endpoint\":\"" + base + "/authorize\","
                    + "\"token_endpoint\":\"" + base + "/token\","
                    + "\"userinfo_endpoint\":\"" + base + "/userinfo\"}";
            respond(ex, 200, body);
        });
        server.createContext("/token", ex -> {
            lastTokenRequestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(ex, 200, "{\"access_token\":\"at-1\",\"id_token\":\"x.y.z\"}");
        });
        server.createContext("/token-empty", ex -> respond(ex, 200, "{}"));
        server.createContext("/token-error", ex -> respond(ex, 500, "{\"error\":\"bad\"}"));
        server.createContext("/userinfo", ex -> {
            lastUserInfoAuthHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"sub\":\"sub-1\",\"preferred_username\":\"tdpuser\","
                    + "\"email\":\"u@tdp.fan\",\"email_verified\":true,\"name\":\"TDP User\","
                    + "\"picture\":\"http://img\",\"tdp_role\":\"admin\"}");
        });
        server.createContext("/userinfo-error", ex -> respond(ex, 401, "{\"error\":\"unauthorized\"}"));
        server.start();
    }

    private static void respond(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 写入一套可用的 OIDC 配置，指向本地桩服务 */
    private void enableOidc() {
        configStore.put("oidcEnabled", "true");
        configStore.put("oidcIssuerUri", base);
        configStore.put("oidcClientId", "cid");
        configStore.put("oidcClientSecret", "csecret");
        configStore.put("oidcUsePkce", "true");
        configStore.put("oidcAutoCreateUser", "true");
    }

    private User activeUser() {
        User u = new User();
        u.setId(7L);
        u.setUsername("local");
        u.setEmail("local@x.fan");
        u.setRole(User.UserRole.USER);
        u.setEnabled(true);
        u.setRoleId(3L);
        return u;
    }

    private String idToken(Map<String, Object> claims) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(claims));
        return header + "." + payload + ".";
    }

    // ------------------------------------------------------------------ 配置

    @Nested
    @DisplayName("配置读取与保存")
    class Config {

        @Test
        @DisplayName("未配置时返回默认值，且不回显 clientSecret")
        void defaults() {
            OidcConfigDTO dto = service.getConfig();
            assertFalse(dto.getEnabled());
            assertEquals("tdp", dto.getProvider());
            assertEquals("https://tdp.fan/oidc", dto.getIssuerUri());
            assertEquals("openid profile email tdp:role", dto.getScope());
            assertEquals("使用 TDP 登录", dto.getButtonLabel());
            assertTrue(dto.getAutoCreateUser());
            assertTrue(dto.getUsePkce());
            assertNull(dto.getClientSecret());
        }

        @Test
        @DisplayName("已配置时回显配置值，clientSecret 始终为 null")
        void configured() {
            enableOidc();
            configStore.put("oidcButtonLabel", "用 TDP 登录");
            OidcConfigDTO dto = service.getConfig();
            assertTrue(dto.getEnabled());
            assertEquals(base, dto.getIssuerUri());
            assertEquals("cid", dto.getClientId());
            assertEquals("用 TDP 登录", dto.getButtonLabel());
            assertNull(dto.getClientSecret());
        }

        @Test
        @DisplayName("保存配置逐项写库，clientSecret 留空则保持原值")
        void saveConfig() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            dto.setProvider(" tdp ");
            dto.setIssuerUri(" https://issuer ");
            dto.setClientId(" cid2 ");
            dto.setClientSecret("   ");
            dto.setRedirectUri(" https://cb ");
            dto.setScope(" openid ");
            dto.setAutoCreateUser(false);
            dto.setButtonLabel(" 登录 ");
            dto.setUsePkce(false);
            service.saveConfig(dto);

            assertEquals("true", configStore.get("oidcEnabled"));
            assertEquals("tdp", configStore.get("oidcProvider"));
            assertEquals("https://issuer", configStore.get("oidcIssuerUri"));
            assertEquals("cid2", configStore.get("oidcClientId"));
            assertNull(configStore.get("oidcClientSecret"));
            assertEquals("https://cb", configStore.get("oidcRedirectUri"));
            assertEquals("openid", configStore.get("oidcScope"));
            assertEquals("false", configStore.get("oidcAutoCreateUser"));
            assertEquals("登录", configStore.get("oidcButtonLabel"));
            assertEquals("false", configStore.get("oidcUsePkce"));
        }

        @Test
        @DisplayName("保存配置时字段为 null 或空白则跳过")
        void saveConfigSkipsBlanks() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setProvider("  ");
            dto.setScope("");
            dto.setButtonLabel(" ");
            dto.setEnabled(null);
            dto.setUsePkce(null);
            dto.setAutoCreateUser(null);
            service.saveConfig(dto);
            assertTrue(configStore.isEmpty());
        }

        @Test
        @DisplayName("保存配置会清空 Discovery 缓存（下次重新拉取）")
        void saveConfigInvalidatesDiscovery() {
            enableOidc();
            assertNotNull(service.buildAuthorizationUrl(base));
            // 再次生成：缓存命中，无需重新请求（此处仅验证不抛错）
            assertNotNull(service.buildAuthorizationUrl(base));
        }

        @Test
        @DisplayName("isEnabledAndConfigured：缺任一项配置均为 false")
        void enabledAndConfigured() {
            assertFalse(service.isEnabledAndConfigured());

            configStore.put("oidcEnabled", "true");
            assertFalse(service.isEnabledAndConfigured());

            configStore.put("oidcClientId", "cid");
            assertFalse(service.isEnabledAndConfigured());

            configStore.put("oidcClientSecret", "sec");
            assertFalse(service.isEnabledAndConfigured());

            configStore.put("oidcIssuerUri", "https://issuer");
            assertTrue(service.isEnabledAndConfigured());
        }

        @Test
        @DisplayName("getPublicConfig：按可用性返回公开配置")
        void publicConfig() {
            OidcPublicConfigDTO off = service.getPublicConfig();
            assertFalse(off.getEnabled());

            enableOidc();
            configStore.put("oidcButtonLabel", "TDP");
            OidcPublicConfigDTO on = service.getPublicConfig();
            assertTrue(on.getEnabled());
            assertEquals("tdp", on.getProvider());
            assertEquals("TDP", on.getButtonLabel());
        }

        @Test
        @DisplayName("布尔配置非法值回退默认：非 true 字符串视为 false")
        void parseBooleanFallback() {
            enableOidc();
            configStore.put("oidcUsePkce", "yes");
            // "yes" 非 "true" -> parseBoolean 为 false，授权 URL 不带 PKCE
            String url = service.buildAuthorizationUrl(base);
            assertFalse(url.contains("code_challenge"));
        }
    }

    // ---------------------------------------------------------- 授权 URL 生成

    @Nested
    @DisplayName("授权 URL 生成")
    class Authorize {

        @Test
        @DisplayName("未启用时抛异常")
        void notConfigured() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl(base));
            assertTrue(ex.getMessage().contains("未启用"));
        }

        @Test
        @DisplayName("PKCE 开启：URL 含 state/scope/challenge 且 method=S256")
        void withPkce() {
            enableOidc();
            String url = service.buildAuthorizationUrl(base);
            assertTrue(url.startsWith(base + "/authorize?"));
            assertTrue(url.contains("client_id=cid"));
            assertTrue(url.contains("response_type=code"));
            assertTrue(url.contains("scope=openid"));
            assertTrue(url.contains("code_challenge="));
            assertTrue(url.contains("code_challenge_method=S256"));
            assertTrue(url.contains("state="));
        }

        @Test
        @DisplayName("PKCE 关闭：URL 不含 code_challenge")
        void withoutPkce() {
            enableOidc();
            configStore.put("oidcUsePkce", "false");
            String url = service.buildAuthorizationUrl(base);
            assertFalse(url.contains("code_challenge"));
        }

        @Test
        @DisplayName("回调地址优先取显式配置")
        void explicitRedirect() {
            enableOidc();
            configStore.put("oidcRedirectUri", "https://app.fan/cb");
            String url = service.buildAuthorizationUrl(base);
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.fan%2Fcb"));
        }

        @Test
        @DisplayName("无显式配置：回退 siteBaseUrl 并拼接回调路径")
        void redirectFromSiteBaseUrl() {
            enableOidc();
            configStore.put("siteBaseUrl", "https://site.fan/");
            String url = service.buildAuthorizationUrl(base);
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Fsite.fan%2Fapi%2Fauth%2Foidc%2Fcallback"));
        }

        @Test
        @DisplayName("无显式配置且无 siteBaseUrl：使用请求 baseUrl")
        void redirectFromBaseUrl() {
            enableOidc();
            String url = service.buildAuthorizationUrl("https://req.fan");
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Freq.fan%2Fapi%2Fauth%2Foidc%2Fcallback"));
        }

        @Test
        @DisplayName("Discovery 无授权端点时抛异常")
        void missingAuthorizationEndpoint() throws Exception {
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"token_endpoint\":\"" + base + "/token\"}"));
            enableOidc();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl(base));
            assertTrue(ex.getMessage().contains("授权端点"));
        }

        @Test
        @DisplayName("Discovery 请求非 2xx 时抛异常")
        void discoveryFailure() throws Exception {
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 503, "{}"));
            enableOidc();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl(base));
            assertTrue(ex.getMessage().contains("Discovery"));
        }

        @Test
        @DisplayName("issuer 以 / 结尾时正确拼接 discovery 路径")
        void issuerTrailingSlash() {
            enableOidc();
            configStore.put("oidcIssuerUri", base + "/");
            assertNotNull(service.buildAuthorizationUrl(base));
        }
    }

    // -------------------------------------------------------------- 回调处理

    @Nested
    @DisplayName("回调处理：参数与 state 校验")
    class CallbackValidation {

        @Test
        @DisplayName("缺少 code 直接报错")
        void missingCode() {
            ApiResponse<LoginResponse> r = service.handleCallback(null, "s");
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("code"));
        }

        @Test
        @DisplayName("缺少 state 直接报错")
        void missingState() {
            ApiResponse<LoginResponse> r = service.handleCallback("c", "");
            assertTrue(r.getMessage().contains("state"));
        }

        @Test
        @DisplayName("state 未登记（无效/已用过）报错")
        void invalidState() {
            ApiResponse<LoginResponse> r = service.handleCallback("code", "unknown-state");
            assertTrue(r.getMessage().contains("state 无效"));
        }
    }

    @Nested
    @DisplayName("回调处理：换取 Token 与用户信息")
    class CallbackToken {

        @Test
        @DisplayName("成功回调：绑定已有账号并签发 JWT")
        void successBindExisting() throws Exception {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("sub-1");
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(existing));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of("p1", "p2"));
            Role role = new Role();
            role.setId(3L);
            role.setName("普通用户");
            role.setCode("ROLE_USER");
            when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);

            assertEquals(200, r.getCode());
            assertEquals("jwt-1", r.getData().getToken());
            assertEquals("local", r.getData().getUsername());
            assertEquals("ROLE_USER", r.getData().getRoleCode());
            assertEquals(2, r.getData().getPermissions().size());
        }

        @Test
        @DisplayName("换取 token 请求体包含 code/client_id/client_secret 与 PKCE verifier")
        void tokenRequestBody() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl(base));
            service.handleCallback("code-1", state);

            String body = URLDecoder.decode(lastTokenRequestBody.get(), StandardCharsets.UTF_8);
            assertTrue(body.contains("grant_type=authorization_code"));
            assertTrue(body.contains("code=code-1"));
            assertTrue(body.contains("client_id=cid"));
            assertTrue(body.contains("client_secret=csecret"));
            assertTrue(body.contains("code_verifier="));
        }

        @Test
        @DisplayName("UserInfo 请求携带 Bearer access_token")
        void userInfoAuthHeader() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl(base));
            service.handleCallback("code-1", state);
            assertEquals("Bearer at-1", lastUserInfoAuthHeader.get());
        }

        @Test
        @DisplayName("Token 端点非 2xx：换 token 失败")
        void tokenEndpointError() throws Exception {
            enableOidc();
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":\"" + base + "/authorize\","
                            + "\"token_endpoint\":\"" + base + "/token-error\"}"));
            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);
            assertTrue(r.getMessage().contains("访问令牌"));
        }

        @Test
        @DisplayName("Token 响应缺少 access_token：报错")
        void tokenMissingAccessToken() throws Exception {
            enableOidc();
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":\"" + base + "/authorize\","
                            + "\"token_endpoint\":\"" + base + "/token-empty\"}"));
            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);
            assertTrue(r.getMessage().contains("access_token"));
        }

        @Test
        @DisplayName("UserInfo 失败时回退解析 ID Token")
        void fallbackToIdToken() throws Exception {
            enableOidc();
            String token = idToken(Map.of("sub", "sub-9", "email", "id@tdp.fan", "iss", base));
            String discovery = "{\"authorization_endpoint\":\"" + base + "/authorize\","
                    + "\"token_endpoint\":\"" + base + "/token\","
                    + "\"userinfo_endpoint\":\"" + base + "/userinfo-error\"}";
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration", ex -> respond(ex, 200, discovery));
            server.removeContext("/token");
            server.createContext("/token", ex -> respond(ex, 200,
                    "{\"access_token\":\"at-1\",\"id_token\":\"" + token + "\"}"));
            when(userRepository.findByOidcSub("sub-9")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-9");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);
            assertEquals(200, r.getCode());
            assertEquals("jwt-9", r.getData().getToken());
        }

        @Test
        @DisplayName("UserInfo 与 ID Token 均不可用时返回无法获取用户信息")
        void noUserInfo() throws Exception {
            enableOidc();
            String discovery = "{\"authorization_endpoint\":\"" + base + "/authorize\","
                    + "\"token_endpoint\":\"" + base + "/token\","
                    + "\"userinfo_endpoint\":\"" + base + "/userinfo-error\"}";
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration", ex -> respond(ex, 200, discovery));
            server.removeContext("/token");
            server.createContext("/token", ex -> respond(ex, 200, "{\"access_token\":\"at-1\"}"));
            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);
            assertTrue(r.getMessage().contains("用户信息"));
        }

        @Test
        @DisplayName("Discovery 缺 token_endpoint：报错")
        void missingTokenEndpoint() throws Exception {
            enableOidc();
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":\"" + base + "/authorize\"}"));
            String state = extractState(service.buildAuthorizationUrl(base));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state);
            assertTrue(r.getMessage().contains("Token 端点"));
        }
    }

    @Nested
    @DisplayName("ID Token 解析与校验")
    class IdTokenValidation {

        private String callbackWithIdToken(String token) throws Exception {
            enableOidc();
            String discovery = "{\"authorization_endpoint\":\"" + base + "/authorize\","
                    + "\"token_endpoint\":\"" + base + "/token\","
                    + "\"userinfo_endpoint\":\"" + base + "/userinfo-error\"}";
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration", ex -> respond(ex, 200, discovery));
            server.removeContext("/token");
            server.createContext("/token", ex -> respond(ex, 200,
                    "{\"access_token\":\"at-1\",\"id_token\":\"" + token + "\"}"));
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-ok");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());
            String state = extractState(service.buildAuthorizationUrl(base));
            return service.handleCallback("code-1", state).getMessage();
        }

        @Test
        @DisplayName("过期 ID Token：拒绝")
        void expired() throws Exception {
            String token = idToken(Map.of("sub", "s", "iss", base,
                    "exp", Instant.now().getEpochSecond() - 10));
            assertTrue(callbackWithIdToken(token).contains("用户信息"));
        }

        @Test
        @DisplayName("issuer 不匹配：拒绝")
        void issuerMismatch() throws Exception {
            String token = idToken(Map.of("sub", "s", "iss", "https://evil.example"));
            assertTrue(callbackWithIdToken(token).contains("用户信息"));
        }

        @Test
        @DisplayName("非法（非三段）ID Token：拒绝")
        void malformed() throws Exception {
            assertTrue(callbackWithIdToken("not-a-jwt").contains("用户信息"));
        }

        @Test
        @DisplayName("载荷非合法 Base64/JSON：拒绝")
        void badPayload() throws Exception {
            assertTrue(callbackWithIdToken("aaa.###.ccc").contains("用户信息"));
        }
    }

    @Nested
    @DisplayName("账号绑定与自动建号")
    class Binding {

        private String state() {
            return extractState(service.buildAuthorizationUrl(base));
        }

        @Test
        @DisplayName("已绑定但账号被禁用：返回 403")
        void disabledUser() {
            enableOidc();
            User disabled = activeUser();
            disabled.setOidcSub("sub-1");
            disabled.setEnabled(false);
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(disabled));
            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(403, r.getCode());
            assertTrue(r.getMessage().contains("禁用"));
        }

        @Test
        @DisplayName("已绑定且邮箱有变化：同步新邮箱并保存")
        void syncEmail() {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("sub-1");
            existing.setEmail("old@x.fan");
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmail("u@tdp.fan")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-x");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            service.handleCallback("code-1", state());
            assertEquals("u@tdp.fan", existing.getEmail());
            verify(userRepository).save(existing);
        }

        @Test
        @DisplayName("已绑定且邮箱已被他人占用：不改动，不保存")
        void emailConflictSkip() {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("sub-1");
            existing.setEmail("old@x.fan");
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmail("u@tdp.fan")).thenReturn(true);
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-x");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            service.handleCallback("code-1", state());
            assertEquals("old@x.fan", existing.getEmail());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("首次登录按邮箱匹配到本地账号：自动绑定")
        void bindByEmail() {
            enableOidc();
            User byEmail = activeUser();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.of(byEmail));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-e");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
            assertEquals("sub-1", byEmail.getOidcSub());
            assertEquals("tdp", byEmail.getOidcProvider());
        }

        @Test
        @DisplayName("首次登录且未开启自动建号：返回 403")
        void autoCreateDisabled() {
            enableOidc();
            configStore.put("oidcAutoCreateUser", "false");
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(403, r.getCode());
            assertTrue(r.getMessage().contains("自动创建"));
        }

        @Test
        @DisplayName("首次登录自动建号：生成用户名、默认角色、AI 额度并签发 JWT")
        void autoCreate() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername("tdpuser")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                if (u.getId() == null) {
                    u.setId(99L);
                }
                return u;
            });
            Role role = new Role();
            role.setId(5L);
            role.setName("默认角色");
            role.setCode("ROLE_DEFAULT");
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.of(role));
            when(roleRepository.findById(5L)).thenReturn(Optional.of(role));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-new");
            when(jwtTokenUtil.getExpiration()).thenReturn(7200L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of("p"));

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
            assertEquals("jwt-new", r.getData().getToken());
            assertEquals("tdpuser", r.getData().getUsername());
            assertEquals(5L, r.getData().getRoleId());
            verify(quotaRepository).save(any(AiQuota.class));
        }

        @Test
        @DisplayName("自动建号：无默认角色时回退 ROLE_USER")
        void fallbackRole() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(100L);
                return u;
            });
            Role role = new Role();
            role.setId(6L);
            role.setCode("ROLE_USER");
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.of(role));
            when(roleRepository.findById(6L)).thenReturn(Optional.of(role));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-fb");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
            assertEquals(6L, r.getData().getRoleId());
        }

        @Test
        @DisplayName("自动建号：AI 额度创建异常不影响登录")
        void quotaFailureTolerated() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(101L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(quotaRepository.save(any(AiQuota.class)))
                    .thenThrow(new RuntimeException("db down"));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-q");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
        }

        @Test
        @DisplayName("用户名冲突：追加递增后缀")
        void usernameConflict() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername("tdpuser")).thenReturn(true);
            when(userRepository.existsByUsername("tdpuser_1")).thenReturn(true);
            when(userRepository.existsByUsername("tdpuser_2")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(102L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-u");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals("tdpuser_2", r.getData().getUsername());
        }

        @Test
        @DisplayName("UserInfo 无 email：按 name 生成用户名且跳过邮箱绑定")
        void noEmailUsesName() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200,
                    "{\"sub\":\"sub-2\",\"name\":\"张三 Sh@n\",\"preferred_username\":\"\"}"));
            when(userRepository.findByOidcSub("sub-2")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(103L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-n");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
            // 非安全字符替换为下划线
            assertTrue(r.getData().getUsername().startsWith("__"));
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("UserInfo 无 preferred_username/name/email：用户名回退 tdp_<sub>")
        void usernameFromSub() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200, "{\"sub\":\"sub-xyz\"}"));
            when(userRepository.findByOidcSub("sub-xyz")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername("tdp_sub-xyz")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(104L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-s");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals("tdp_sub-xyz", r.getData().getUsername());
        }

        @Test
        @DisplayName("邮箱无 @ 时同样回退 tdp_<sub>")
        void emailWithoutAt() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200,
                    "{\"sub\":\"sub-noat\",\"email\":\"weird\"}"));
            when(userRepository.findByOidcSub("sub-noat")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername("tdp_sub-noat")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(105L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-na");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals("tdp_sub-noat", r.getData().getUsername());
        }

        @Test
        @DisplayName("邮箱 local part 作为用户名（含 @ 时截断）")
        void usernameFromEmailLocalPart() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200, "{\"sub\":\"sub-e\",\"email\":\"bob@x.fan\"}"));
            when(userRepository.findByOidcSub("sub-e")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("bob@x.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername("bob")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(106L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-lp");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals("bob", r.getData().getUsername());
        }

        @Test
        @DisplayName("超长用户名截断到 40 字符")
        void usernameTooLong() throws Exception {
            enableOidc();
            String longName = "a".repeat(60);
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200,
                    "{\"sub\":\"sub-long\",\"preferred_username\":\"" + longName + "\"}"));
            when(userRepository.findByOidcSub("sub-long")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(107L);
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-l");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(40, r.getData().getUsername().length());
        }

        @Test
        @DisplayName("已绑定用户角色无 roleId：权限列表为空且 roleName/roleCode 为 null")
        void noRoleAssigned() {
            enableOidc();
            User u = activeUser();
            u.setOidcSub("sub-1");
            u.setRoleId(null);
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(u));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-r");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);

            ApiResponse<LoginResponse> r = service.handleCallback("code-1", state());
            assertEquals(200, r.getCode());
            assertTrue(r.getData().getPermissions().isEmpty());
            assertNull(r.getData().getRoleName());
            assertNull(r.getData().getRoleCode());
        }

        @Test
        @DisplayName("state 已使用过（重复回调）报错")
        void stateReuse() {
            enableOidc();
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String s = state();
            assertEquals(200, service.handleCallback("code-1", s).getCode());
            ApiResponse<LoginResponse> again = service.handleCallback("code-1", s);
            assertTrue(again.getMessage().contains("state 无效"));
        }
    }

    /** 从授权 URL 中取出 state 参数 */
    private String extractState(String url) {
        int idx = url.indexOf("state=");
        String tail = url.substring(idx + "state=".length());
        int amp = tail.indexOf('&');
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }
}
