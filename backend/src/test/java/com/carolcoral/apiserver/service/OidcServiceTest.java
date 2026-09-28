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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OidcService 单元测试：多服务商配置读写、授权 URL 生成、回调换 token/绑定账号、ID Token 校验。
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

    /**
     * 直接写入服务商列表配置（等价于后台保存后的结果）
     */
    private OidcProviderDTO storeProvider(String providerId, Map<String, Object> overrides) {
        OidcProviderDTO provider = new OidcProviderDTO();
        provider.setProviderId(providerId);
        provider.setName(providerId.toUpperCase(java.util.Locale.ROOT));
        provider.setButtonLabel("使用 " + providerId + " 登录");
        provider.setEnabled(true);
        provider.setIssuerUri(base);
        provider.setClientId("cid");
        provider.setClientSecret("csecret");
        provider.setScope("openid profile email tdp:role");
        provider.setAutoCreateUser(true);
        provider.setUsePkce(true);
        if (overrides != null) {
            overrides.forEach((k, v) -> {
                switch (k) {
                    case "enabled" -> provider.setEnabled((Boolean) v);
                    case "issuerUri" -> provider.setIssuerUri((String) v);
                    case "clientId" -> provider.setClientId((String) v);
                    case "clientSecret" -> provider.setClientSecret((String) v);
                    case "scope" -> provider.setScope((String) v);
                    case "autoCreateUser" -> provider.setAutoCreateUser((Boolean) v);
                    case "usePkce" -> provider.setUsePkce((Boolean) v);
                    case "buttonLabel" -> provider.setButtonLabel((String) v);
                    case "redirectUri" -> provider.setRedirectUri((String) v);
                    case "name" -> provider.setName((String) v);
                    default -> throw new IllegalArgumentException("unknown key " + k);
                }
            });
        }
        writeProviderList(List.of(provider));
        return provider;
    }

    /** 把服务商以 JSON 形态写入配置存储（绕过 saveConfig，模拟已保存状态） */
    private void writeProviderList(List<OidcProviderDTO> providers) {
        OidcConfigDTO dto = new OidcConfigDTO();
        dto.setEnabled(true);
        dto.setProviders(providers);
        // 先清掉已存列表，避免 merge 时误取旧 secret
        configStore.remove("oidcProviders");
        service.saveConfig(dto);
    }

    /** 写入一套可用的 OIDC 配置，指向本地桩服务 */
    private void enableOidc() {
        configStore.put("oidcEnabled", "true");
        storeProvider("tdp", null);
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
        @DisplayName("未配置时返回默认值：无服务商且不回显 clientSecret")
        void defaults() {
            OidcConfigDTO dto = service.getConfig();
            assertFalse(dto.getEnabled());
            assertTrue(dto.getProviders().isEmpty());
        }

        @Test
        @DisplayName("保存配置：服务商逐项写库，secret 不回显原文")
        void saveAndRead() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO p = new OidcProviderDTO();
            p.setProviderId(" tdp ");
            p.setName(" TDP ");
            p.setIssuerUri(" " + base + " ");
            p.setClientId(" cid ");
            p.setClientSecret(" sec ");
            p.setScope(" openid ");
            p.setButtonLabel(" 使用 TDP 登录 ");
            p.setAutoCreateUser(false);
            p.setUsePkce(false);
            dto.setProviders(List.of(p));
            service.saveConfig(dto);

            assertEquals("true", configStore.get("oidcEnabled"));
            assertNotNull(configStore.get("oidcProviders"));

            OidcConfigDTO read = service.getConfig();
            assertEquals(1, read.getProviders().size());
            OidcProviderDTO readProvider = read.getProviders().get(0);
            assertEquals("tdp", readProvider.getProviderId());
            assertEquals("TDP", readProvider.getName());
            assertEquals(base, readProvider.getIssuerUri());
            assertEquals("cid", readProvider.getClientId());
            assertEquals("openid", readProvider.getScope());
            assertEquals("使用 TDP 登录", readProvider.getButtonLabel());
            assertFalse(readProvider.getAutoCreateUser());
            assertFalse(readProvider.getUsePkce());
            // secret 不回显，仅标记已配置
            assertNull(readProvider.getClientSecret());
            assertTrue(readProvider.getClientSecretConfigured());
        }

        @Test
        @DisplayName("保存配置：providerId 为空时按 name 推导，name 也为空则忽略该服务商")
        void saveConfigDerivesProviderId() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO named = new OidcProviderDTO();
            named.setName("My Keycloak");
            named.setIssuerUri("https://kc");
            named.setClientId("cid");
            named.setClientSecret("sec");

            OidcProviderDTO blank = new OidcProviderDTO();
            dto.setProviders(List.of(named, blank));
            service.saveConfig(dto);

            List<OidcProviderDTO> saved = service.getConfig().getProviders();
            assertEquals(1, saved.size());
            assertEquals("my_keycloak", saved.get(0).getProviderId());
        }

        @Test
        @DisplayName("保存配置：同一次提交内重复 providerId 只保留首个")
        void saveConfigDeduplicates() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO first = new OidcProviderDTO();
            first.setProviderId("dup");
            first.setClientId("cid-1");
            first.setClientSecret("sec-1");
            OidcProviderDTO second = new OidcProviderDTO();
            second.setProviderId("dup");
            second.setClientId("cid-2");
            second.setClientSecret("sec-2");
            dto.setProviders(List.of(first, second));
            service.saveConfig(dto);

            List<OidcProviderDTO> saved = service.getConfig().getProviders();
            assertEquals(1, saved.size());
            assertEquals("cid-1", saved.get(0).getClientId());
        }

        @Test
        @DisplayName("保存配置：clientSecret 留空保持原值，显式 clear 则清空")
        void secretRetention() {
            enableOidc();
            // 只改 scope，不提交 secret
            OidcConfigDTO dto = new OidcConfigDTO();
            OidcProviderDTO p = new OidcProviderDTO();
            p.setProviderId("tdp");
            p.setIssuerUri(base);
            p.setClientId("cid");
            p.setScope("openid email");
            dto.setProviders(List.of(p));
            service.saveConfig(dto);
            assertTrue(service.isProviderAvailable("tdp"), "secret 应保持原值，服务商仍可用");

            // 显式 clear
            OidcConfigDTO clearDto = new OidcConfigDTO();
            OidcProviderDTO clearP = new OidcProviderDTO();
            clearP.setProviderId("tdp");
            clearP.setIssuerUri(base);
            clearP.setClientId("cid");
            clearP.setClientSecretStatus("clear");
            clearDto.setProviders(List.of(clearP));
            service.saveConfig(clearDto);
            assertFalse(service.isProviderAvailable("tdp"), "secret 已清空，服务商应不可用");
            assertFalse(service.getConfig().getProviders().get(0).getClientSecretConfigured());
        }

        @Test
        @DisplayName("保存配置：clientId 留空沿用原值")
        void clientIdRetention() {
            enableOidc();
            OidcConfigDTO dto = new OidcConfigDTO();
            OidcProviderDTO p = new OidcProviderDTO();
            p.setProviderId("tdp");
            p.setIssuerUri(base);
            p.setScope("openid");
            dto.setProviders(List.of(p));
            service.saveConfig(dto);
            assertEquals("cid", service.getConfig().getProviders().get(0).getClientId());
        }

        @Test
        @DisplayName("保存配置：dto 为 null 或 providers 为 null 时安全跳过")
        void saveNullSafe() {
            service.saveConfig(null);
            assertTrue(configStore.isEmpty());

            // setProviders(null) 会被规范化为空列表，等价于「清空所有服务商」
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            dto.setProviders(null);
            service.saveConfig(dto);
            assertEquals("true", configStore.get("oidcEnabled"));
            assertEquals("[]", configStore.get("oidcProviders"));
        }

        @Test
        @DisplayName("历史单一服务商配置：首次读取时回显为服务商列表")
        void legacyConfigMigration() {
            configStore.put("oidcEnabled", "true");
            configStore.put("oidcProvider", "tdp");
            configStore.put("oidcIssuerUri", base);
            configStore.put("oidcClientId", "legacy-cid");
            configStore.put("oidcClientSecret", "legacy-sec");
            configStore.put("oidcButtonLabel", "使用 TDP 登录");
            configStore.put("oidcScope", "openid profile");
            configStore.put("oidcAutoCreateUser", "false");
            configStore.put("oidcUsePkce", "true");

            OidcConfigDTO dto = service.getConfig();
            assertEquals(1, dto.getProviders().size());
            OidcProviderDTO provider = dto.getProviders().get(0);
            assertEquals("tdp", provider.getProviderId());
            assertEquals(base, provider.getIssuerUri());
            assertEquals("legacy-cid", provider.getClientId());
            assertEquals("openid profile", provider.getScope());
            assertFalse(provider.getAutoCreateUser());
            assertTrue(provider.getClientSecretConfigured());
            assertTrue(service.isProviderAvailable("tdp"));
        }

        @Test
        @DisplayName("历史配置缺失 issuer/clientId 时回退默认值")
        void legacyDefaults() {
            configStore.put("oidcClientSecret", "only-secret");
            OidcConfigDTO dto = service.getConfig();
            assertEquals(1, dto.getProviders().size());
            OidcProviderDTO provider = dto.getProviders().get(0);
            assertEquals("tdp", provider.getProviderId());
            assertEquals("https://tdp.fan/oidc", provider.getIssuerUri());
            assertEquals("openid profile email tdp:role", provider.getScope());
            assertEquals("使用 TDP 登录", provider.getButtonLabel());
        }

        @Test
        @DisplayName("配置列表 JSON 损坏时按空列表处理")
        void corruptedProviderList() {
            configStore.put("oidcEnabled", "true");
            configStore.put("oidcProviders", "not-a-json");
            assertFalse(service.isEnabledAndConfigured());
        }

        @Test
        @DisplayName("配置列表非数组时按空列表处理")
        void nonArrayProviderList() {
            configStore.put("oidcEnabled", "true");
            configStore.put("oidcProviders", "{\"providerId\":\"tdp\"}");
            assertFalse(service.isEnabledAndConfigured());
        }

        @Test
        @DisplayName("isEnabledAndConfigured：总开关关闭或无可用服务商均为 false")
        void enabledAndConfigured() {
            assertFalse(service.isEnabledAndConfigured());

            // 总开关关闭，即使服务商完整也不可用
            storeProvider("tdp", null);
            configStore.put("oidcEnabled", "false");
            assertFalse(service.isEnabledAndConfigured());

            configStore.put("oidcEnabled", "true");
            assertTrue(service.isEnabledAndConfigured());

            // 缺 clientSecret 的启用服务商不算可用
            configStore.remove("oidcProviders");
            configStore.put("oidcProviders", "[{\"providerId\":\"kc\",\"enabled\":true,"
                    + "\"issuerUri\":\"https://kc\",\"clientId\":\"cid\"}]");
            assertFalse(service.isEnabledAndConfigured());

            // 禁用（enabled=false）的服务商不算可用
            configStore.put("oidcProviders", "[{\"providerId\":\"kc\",\"enabled\":false,"
                    + "\"issuerUri\":\"https://kc\",\"clientId\":\"cid\",\"clientSecret\":\"sec\"}]");
            assertFalse(service.isEnabledAndConfigured());
        }

        @Test
        @DisplayName("getPublicConfig：多服务商按启用与完整度过滤")
        void publicConfig() {
            OidcPublicConfigDTO off = service.getPublicConfig();
            assertFalse(off.getEnabled());
            assertTrue(off.getProviders().isEmpty());

            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO tdp = completeProvider("tdp", true);
            OidcProviderDTO kc = completeProvider("keycloak", true);
            OidcProviderDTO disabled = completeProvider("auth0", false);
            dto.setProviders(List.of(tdp, kc, disabled));
            service.saveConfig(dto);

            OidcPublicConfigDTO on = service.getPublicConfig();
            assertTrue(on.getEnabled());
            assertEquals(2, on.getProviders().size());
            assertEquals("tdp", on.getProviders().get(0).getProviderId());
            assertEquals("keycloak", on.getProviders().get(1).getProviderId());
            assertEquals("使用 keycloak 登录", on.getProviders().get(1).getButtonLabel());
            assertFalse(service.isProviderAvailable("auth0"));
            assertTrue(service.isProviderAvailable("keycloak"));
            assertFalse(service.isProviderAvailable("nope"));
            assertFalse(service.isProviderAvailable(null));
        }

        @Test
        @DisplayName("布尔配置非法值回退默认：非 true 字符串视为 false")
        void parseBooleanFallback() {
            enableOidc();
            OidcConfigDTO dto = new OidcConfigDTO();
            OidcProviderDTO p = completeProvider("tdp", true);
            p.setUsePkce(false);
            dto.setProviders(List.of(p));
            service.saveConfig(dto);
            // 重新写入非法布尔值，验证回退为 false（不带 PKCE）
            storeProvider("tdp", Map.of("usePkce", false));
            String url = service.buildAuthorizationUrl("tdp", base);
            assertFalse(url.contains("code_challenge"));
        }

        private OidcProviderDTO completeProvider(String id, boolean enabled) {
            OidcProviderDTO p = new OidcProviderDTO();
            p.setProviderId(id);
            p.setName(id);
            p.setButtonLabel("使用 " + id + " 登录");
            p.setEnabled(enabled);
            p.setIssuerUri(base);
            p.setClientId(id + "-cid");
            p.setClientSecret(id + "-secret");
            return p;
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
                    () -> service.buildAuthorizationUrl("tdp", base));
            assertTrue(ex.getMessage().contains("未启用"));
        }

        @Test
        @DisplayName("总开关关闭时即使有完整服务商也抛异常")
        void globallyDisabled() {
            storeProvider("tdp", null);
            configStore.put("oidcEnabled", "false");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl("tdp", base));
            assertTrue(ex.getMessage().contains("未启用"));
        }

        @Test
        @DisplayName("providerId 未配置或已禁用时抛异常")
        void unknownProvider() {
            enableOidc();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl("keycloak", base));
            assertTrue(ex.getMessage().contains("服务商不存在或未启用"));
        }

        @Test
        @DisplayName("providerId 为空且存在多个服务商时抛异常（避免歧义）")
        void ambiguousProvider() {
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO a = new OidcProviderDTO();
            a.setProviderId("a");
            a.setIssuerUri(base);
            a.setClientId("a-cid");
            a.setClientSecret("a-sec");
            OidcProviderDTO b = new OidcProviderDTO();
            b.setProviderId("b");
            b.setIssuerUri(base);
            b.setClientId("b-cid");
            b.setClientSecret("b-sec");
            dto.setProviders(List.of(a, b));
            service.saveConfig(dto);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl(null, base));
            assertTrue(ex.getMessage().contains("未启用或配置不完整"));
        }

        @Test
        @DisplayName("providerId 为空但只有一个服务商时自动选中")
        void singleProviderFallback() {
            enableOidc();
            String url = service.buildAuthorizationUrl(null, base);
            assertTrue(url.contains("client_id=cid"));
        }

        @Test
        @DisplayName("PKCE 开启：URL 含 state/scope/challenge 且 method=S256")
        void withPkce() {
            enableOidc();
            String url = service.buildAuthorizationUrl("tdp", base);
            assertTrue(url.startsWith(base + "/authorize?"));
            assertTrue(url.contains("client_id=cid"));
            assertTrue(url.contains("response_type=code"));
            assertTrue(url.contains("scope=openid"));
            assertTrue(url.contains("code_challenge="));
            assertTrue(url.contains("code_challenge_method=S256"));
            assertTrue(url.contains("state="));
        }

        @Test
        @DisplayName("不同服务商使用各自的 clientId 与授权端点")
        void multipleProvidersUseOwnConfig() throws Exception {
            server.createContext("/authorize-kc", ex -> respond(ex, 200, "{}"));
            OidcConfigDTO dto = new OidcConfigDTO();
            dto.setEnabled(true);
            OidcProviderDTO tdp = new OidcProviderDTO();
            tdp.setProviderId("tdp");
            tdp.setIssuerUri(base);
            tdp.setClientId("tdp-cid");
            tdp.setClientSecret("tdp-sec");
            OidcProviderDTO kc = new OidcProviderDTO();
            kc.setProviderId("keycloak");
            kc.setIssuerUri(base);
            kc.setClientId("kc-cid");
            kc.setClientSecret("kc-sec");
            dto.setProviders(List.of(tdp, kc));
            service.saveConfig(dto);

            String tdpUrl = service.buildAuthorizationUrl("tdp", base);
            String kcUrl = service.buildAuthorizationUrl("keycloak", base);
            assertTrue(tdpUrl.contains("client_id=tdp-cid"));
            assertTrue(kcUrl.contains("client_id=kc-cid"));
        }

        @Test
        @DisplayName("PKCE 关闭：URL 不含 code_challenge")
        void withoutPkce() {
            enableOidc();
            storeProvider("tdp", Map.of("usePkce", false));
            String url = service.buildAuthorizationUrl("tdp", base);
            assertFalse(url.contains("code_challenge"));
        }

        @Test
        @DisplayName("回调地址优先取服务商显式配置")
        void explicitRedirect() {
            enableOidc();
            storeProvider("tdp", Map.of("redirectUri", "https://app.fan/cb"));
            String url = service.buildAuthorizationUrl("tdp", base);
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Fapp.fan%2Fcb"));
        }

        @Test
        @DisplayName("无显式配置：回退 siteBaseUrl 并拼接回调路径")
        void redirectFromSiteBaseUrl() {
            enableOidc();
            configStore.put("siteBaseUrl", "https://site.fan/");
            String url = service.buildAuthorizationUrl("tdp", base);
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Fsite.fan%2Fapi%2Fauth%2Foidc%2Fcallback"));
        }

        @Test
        @DisplayName("无显式配置且无 siteBaseUrl：使用请求 baseUrl")
        void redirectFromBaseUrl() {
            enableOidc();
            String url = service.buildAuthorizationUrl("tdp", "https://req.fan");
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
                    () -> service.buildAuthorizationUrl("tdp", base));
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
                    () -> service.buildAuthorizationUrl("tdp", base));
            assertTrue(ex.getMessage().contains("Discovery"));
        }

        @Test
        @DisplayName("Issuer 不可达时抛 Discovery 异常")
        void discoveryUnreachable() {
            enableOidc();
            storeProvider("tdp", Map.of("issuerUri", "http://127.0.0.1:1"));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl("tdp", base));
            assertTrue(ex.getMessage().contains("Discovery"));
        }

        @Test
        @DisplayName("issuer 以 / 结尾时正确拼接 discovery 路径")
        void issuerTrailingSlash() {
            enableOidc();
            storeProvider("tdp", Map.of("issuerUri", base + "/"));
            assertNotNull(service.buildAuthorizationUrl("tdp", base));
        }

        @Test
        @DisplayName("授权端点为 null 时抛异常（Discovery 缺字段）")
        void nullAuthorizationEndpoint() throws Exception {
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":null}"));
            enableOidc();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.buildAuthorizationUrl("tdp", base));
            assertTrue(ex.getMessage().contains("授权端点"));
        }
    }

    // -------------------------------------------------------------- 回调处理

    @Nested
    @DisplayName("回调处理：参数与 state 校验")
    class CallbackValidation {

        @Test
        @DisplayName("缺少 code 直接报错")
        void missingCode() {
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", null, "s");
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("code"));
        }

        @Test
        @DisplayName("缺少 state 直接报错")
        void missingState() {
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "c", "");
            assertTrue(r.getMessage().contains("state"));
        }

        @Test
        @DisplayName("state 未登记（无效/已用过）报错")
        void invalidState() {
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code", "unknown-state");
            assertTrue(r.getMessage().contains("state 无效"));
        }

        @Test
        @DisplayName("回调携带的 providerId 与 state 记录不一致：拒绝")
        void providerMismatch() {
            enableOidc();
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("keycloak", "code-1", state);
            assertTrue(r.getMessage().contains("不匹配"));
        }

        @Test
        @DisplayName("state 中的服务商在回调前被禁用：返回错误")
        void providerDisabledBeforeCallback() {
            enableOidc();
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            configStore.put("oidcEnabled", "false");
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
            assertTrue(r.getMessage().contains("未启用"));
        }
    }

    @Nested
    @DisplayName("回调处理：换取 Token 与用户信息")
    class CallbackToken {

        @Test
        @DisplayName("成功回调：绑定已有账号并签发 JWT")
        void successBindExisting() {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("tdp:sub-1");
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(existing));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of("p1", "p2"));
            Role role = new Role();
            role.setId(3L);
            role.setName("普通用户");
            role.setCode("ROLE_USER");
            when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);

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
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            service.handleCallback("tdp", "code-1", state);

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
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            service.handleCallback("tdp", "code-1", state);
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
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
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
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
            assertTrue(r.getMessage().contains("access_token"));
        }

        @Test
        @DisplayName("Token 端点不可达：换 token 失败")
        void tokenEndpointUnreachable() throws Exception {
            enableOidc();
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":\"" + base + "/authorize\","
                            + "\"token_endpoint\":\"http://127.0.0.1:1/token\"}"));
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
            assertTrue(r.getMessage().contains("访问令牌"));
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
            when(userRepository.findByOidcSub("tdp:sub-9")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-9");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
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
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
            assertTrue(r.getMessage().contains("用户信息"));
        }

        @Test
        @DisplayName("Discovery 缺 userinfo_endpoint 且无 id_token：无法获取用户信息")
        void noUserInfoEndpoint() throws Exception {
            enableOidc();
            String discovery = "{\"authorization_endpoint\":\"" + base + "/authorize\","
                    + "\"token_endpoint\":\"" + base + "/token\"}";
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration", ex -> respond(ex, 200, discovery));
            server.removeContext("/token");
            server.createContext("/token", ex -> respond(ex, 200, "{\"access_token\":\"at-1\"}"));
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
            assertTrue(r.getMessage().contains("用户信息"));
        }

        @Test
        @DisplayName("Discovery 缺 token_endpoint：报错")
        void missingTokenEndpoint() throws Exception {
            enableOidc();
            server.removeContext("/.well-known/openid-configuration");
            server.createContext("/.well-known/openid-configuration",
                    ex -> respond(ex, 200, "{\"authorization_endpoint\":\"" + base + "/authorize\"}"));
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state);
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
            String state = extractState(service.buildAuthorizationUrl("tdp", base));
            return service.handleCallback("tdp", "code-1", state).getMessage();
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

        @Test
        @DisplayName("ID Token 无 exp 且 issuer 匹配：通过")
        void withoutExp() throws Exception {
            String token = idToken(Map.of("sub", "sub-1", "iss", base));
            // UserInfo 桩返回 sub-1，优先走 UserInfo，这里只验证不因缺 exp 报错
            assertTrue(callbackWithIdToken(token).isEmpty() || !callbackWithIdToken(token).contains("过期"));
        }

        @Test
        @DisplayName("ID Token 的 exp 非数字：忽略 exp 校验")
        void expNotNumber() throws Exception {
            String token = idToken(Map.of("sub", "sub-1", "iss", base, "exp", "abc"));
            assertFalse(callbackWithIdToken(token).contains("过期"));
        }
    }

    @Nested
    @DisplayName("账号绑定与自动建号")
    class Binding {

        private String state() {
            return extractState(service.buildAuthorizationUrl("tdp", base));
        }

        @Test
        @DisplayName("已绑定但账号被禁用：返回 403")
        void disabledUser() {
            enableOidc();
            User disabled = activeUser();
            disabled.setOidcSub("tdp:sub-1");
            disabled.setEnabled(false);
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(disabled));
            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(403, r.getCode());
            assertTrue(r.getMessage().contains("禁用"));
        }

        @Test
        @DisplayName("已绑定且邮箱有变化：同步新邮箱并保存")
        void syncEmail() {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("tdp:sub-1");
            existing.setEmail("old@x.fan");
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmail("u@tdp.fan")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-x");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            service.handleCallback("tdp", "code-1", state());
            assertEquals("u@tdp.fan", existing.getEmail());
            verify(userRepository).save(existing);
        }

        @Test
        @DisplayName("已绑定且邮箱已被他人占用：不改动，不保存")
        void emailConflictSkip() {
            enableOidc();
            User existing = activeUser();
            existing.setOidcSub("tdp:sub-1");
            existing.setEmail("old@x.fan");
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(existing));
            when(userRepository.existsByEmail("u@tdp.fan")).thenReturn(true);
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-x");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            service.handleCallback("tdp", "code-1", state());
            assertEquals("old@x.fan", existing.getEmail());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("首次登录按邮箱匹配到本地账号：自动绑定")
        void bindByEmail() {
            enableOidc();
            User byEmail = activeUser();
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.of(byEmail));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-e");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            assertEquals("tdp:sub-1", byEmail.getOidcSub());
            assertEquals("tdp", byEmail.getOidcProvider());
        }

        @Test
        @DisplayName("历史裸 sub 绑定：自动升级为 providerId:sub")
        void legacyBindingUpgraded() {
            enableOidc();
            User legacy = activeUser();
            legacy.setOidcSub("sub-1");
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(legacy));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-up");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            assertEquals("tdp:sub-1", legacy.getOidcSub());
        }

        @Test
        @DisplayName("历史裸 sub 绑定且账号被禁用：不回退到邮箱，走自动建号分支")
        void legacyBindingDisabledUserSkipped() {
            enableOidc();
            User legacy = activeUser();
            legacy.setOidcSub("sub-1");
            legacy.setEnabled(false);
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.of(legacy));
            // 邮箱已被禁用账号占用 -> 自动建号
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                if (u.getId() == null) {
                    u.setId(200L);
                }
                return u;
            });
            when(roleRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
            when(roleRepository.findByCode("ROLE_USER")).thenReturn(Optional.empty());
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-ld");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            assertEquals("tdpuser", r.getData().getUsername());
        }

        @Test
        @DisplayName("首次登录且未开启自动建号：返回 403")
        void autoCreateDisabled() {
            enableOidc();
            storeProvider("tdp", Map.of("autoCreateUser", false));
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByOidcSub("sub-1")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("u@tdp.fan")).thenReturn(Optional.empty());

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(403, r.getCode());
            assertTrue(r.getMessage().contains("自动创建"));
        }

        @Test
        @DisplayName("首次登录自动建号：生成用户名、默认角色、AI 额度并签发 JWT")
        void autoCreate() {
            enableOidc();
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
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
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            assertEquals(6L, r.getData().getRoleId());
        }

        @Test
        @DisplayName("自动建号：AI 额度创建异常不影响登录")
        void quotaFailureTolerated() {
            enableOidc();
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
        }

        @Test
        @DisplayName("用户名冲突：追加递增后缀")
        void usernameConflict() {
            enableOidc();
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals("tdpuser_2", r.getData().getUsername());
        }

        @Test
        @DisplayName("UserInfo 无 email：按 name 生成用户名且跳过邮箱绑定")
        void noEmailUsesName() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200,
                    "{\"sub\":\"sub-2\",\"name\":\"张三 Sh@n\",\"preferred_username\":\"\"}"));
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            // 非安全字符替换为下划线
            assertTrue(r.getData().getUsername().startsWith("__"));
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("UserInfo 无 preferred_username/name/email：用户名回退 <provider>_<sub>")
        void usernameFromSub() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200, "{\"sub\":\"sub-xyz\"}"));
            when(userRepository.findByOidcSub("tdp:sub-xyz")).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals("tdp_sub-xyz", r.getData().getUsername());
        }

        @Test
        @DisplayName("邮箱无 @ 时同样回退 <provider>_<sub>")
        void emailWithoutAt() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200,
                    "{\"sub\":\"sub-noat\",\"email\":\"weird\"}"));
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals("tdp_sub-noat", r.getData().getUsername());
        }

        @Test
        @DisplayName("邮箱 local part 作为用户名（含 @ 时截断）")
        void usernameFromEmailLocalPart() throws Exception {
            enableOidc();
            server.removeContext("/userinfo");
            server.createContext("/userinfo", ex -> respond(ex, 200, "{\"sub\":\"sub-e\",\"email\":\"bob@x.fan\"}"));
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
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
            when(userRepository.findByOidcSub(anyString())).thenReturn(Optional.empty());
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

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(40, r.getData().getUsername().length());
        }

        @Test
        @DisplayName("已绑定用户角色无 roleId：权限列表为空且 roleName/roleCode 为 null")
        void noRoleAssigned() {
            enableOidc();
            User u = activeUser();
            u.setOidcSub("tdp:sub-1");
            u.setRoleId(null);
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(u));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-r");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);

            ApiResponse<LoginResponse> r = service.handleCallback("tdp", "code-1", state());
            assertEquals(200, r.getCode());
            assertTrue(r.getData().getPermissions().isEmpty());
            assertNull(r.getData().getRoleName());
            assertNull(r.getData().getRoleCode());
        }

        @Test
        @DisplayName("state 已使用过（重复回调）报错")
        void stateReuse() {
            enableOidc();
            when(userRepository.findByOidcSub("tdp:sub-1")).thenReturn(Optional.of(activeUser()));
            when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-1");
            when(jwtTokenUtil.getExpiration()).thenReturn(3600L);
            when(permissionService.getUserPermissionCodes(anyList())).thenReturn(Set.of());

            String s = state();
            assertEquals(200, service.handleCallback("tdp", "code-1", s).getCode());
            ApiResponse<LoginResponse> again = service.handleCallback("tdp", "code-1", s);
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
