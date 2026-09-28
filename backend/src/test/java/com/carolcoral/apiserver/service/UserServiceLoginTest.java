/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.LoginRequest;
import com.carolcoral.apiserver.dto.LoginResponse;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.AiQuotaRepository;
import com.carolcoral.apiserver.repository.RoleRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UserService.login 单元测试：聚焦 OIDC（TDP）账号走本地密码登录的提示分支。
 *
 * <p>回归背景：OIDC 自动创建的账号没有可用的本地密码，用户用这些账号密码登录时
 * 原先只会得到「用户名或密码错误」，容易误判为密码记错。现在应给出明确指引。</p>
 *
 * @author carolcoral
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceLoginTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    @Mock
    private EmailService emailService;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private AiQuotaRepository quotaRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtTokenUtil,
                emailService, roleRepository, permissionService, quotaRepository);
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
    }

    private LoginRequest request(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private User user(String username, boolean oidcAccount) {
        User u = new User();
        u.setId(1L);
        u.setUsername(username);
        u.setPassword("$2a$10$placeholder");
        u.setEnabled(true);
        u.setRole(User.UserRole.USER);
        u.setOidcAccount(oidcAccount);
        return u;
    }

    @Test
    @DisplayName("OIDC 账号使用本地密码登录时，返回明确指引而非「用户名或密码错误」")
    void oidcAccountGetsExplicitHint() {
        when(userRepository.findByUsername("tdpuser")).thenReturn(Optional.of(user("tdpuser", true)));

        ApiResponse<LoginResponse> resp = userService.login(request("tdpuser", "whatever"));

        assertEquals(400, resp.getCode());
        assertTrue(resp.getMessage().contains("TDP"), "应提示使用 TDP 入口登录，实际: " + resp.getMessage());
    }

    @Test
    @DisplayName("本地账号密码错误仍返回 401 用户名或密码错误")
    void localAccountWrongPasswordStillUnauthorized() {
        User local = user("admin", false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(local));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ApiResponse<LoginResponse> resp = userService.login(request("admin", "wrong-pass"));

        assertEquals(401, resp.getCode());
        assertEquals("用户名或密码错误", resp.getMessage());
    }

    @Test
    @DisplayName("用户不存在返回 401")
    void unknownUserUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ApiResponse<LoginResponse> resp = userService.login(request("ghost", "x"));

        assertEquals(401, resp.getCode());
    }

    @Test
    @DisplayName("本地账号密码正确时签发 token")
    void localAccountSuccess() {
        User local = user("admin", false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(local));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenUtil.generateToken(any(), any(), anyString())).thenReturn("jwt-token");
        when(jwtTokenUtil.getExpiration()).thenReturn(86400000L);
        when(permissionService.getUserPermissionCodes(any())).thenReturn(java.util.Set.of());

        ApiResponse<LoginResponse> resp = userService.login(request("admin", "Admin@123!"));

        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals("jwt-token", resp.getData().getToken());
    }
}
