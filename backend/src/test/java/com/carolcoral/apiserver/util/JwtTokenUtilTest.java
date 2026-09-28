/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenUtil 单元测试
 *
 * @author carolcoral
 */
class JwtTokenUtilTest {

    private static final String SECRET = "scbcnlsue#TVYbxka73bdlyabjj63glbod4vjb^FRIGBbja63jku^FXYZ123456789ABC";

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 3600_000L);
    }

    private UserDetails userDetails(String username) {
        return new User(username, "pwd", Collections.emptyList());
    }

    @Test
    @DisplayName("生成的令牌可解析出用户名、用户ID与角色")
    void generateTokenContainsClaims() {
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 42L, "ADMIN");

        assertNotNull(token);
        assertEquals("alice", jwtTokenUtil.getUsernameFromToken(token));
        assertEquals(42L, jwtTokenUtil.getUserIdFromToken(token));
        assertEquals("ADMIN", jwtTokenUtil.getUserRoleFromToken(token));
    }

    @Test
    @DisplayName("令牌有效期取自配置")
    void getExpirationReturnsConfiguredValue() {
        assertEquals(3600_000L, jwtTokenUtil.getExpiration());
    }

    @Test
    @DisplayName("令牌与用户信息匹配时校验通过")
    void validateTokenWithUserDetails() {
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 1L, "USER");
        assertTrue(jwtTokenUtil.validateToken(token, userDetails("alice")));
    }

    @Test
    @DisplayName("用户名不匹配时校验失败")
    void validateTokenRejectsOtherUser() {
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 1L, "USER");
        assertFalse(jwtTokenUtil.validateToken(token, userDetails("bob")));
    }

    @Test
    @DisplayName("未过期令牌单参数校验通过")
    void validateTokenWithoutUserDetails() {
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 1L, "USER");
        assertTrue(jwtTokenUtil.validateToken(token));
    }

    @Test
    @DisplayName("过期令牌单参数校验返回 false 而不抛异常")
    void validateExpiredTokenReturnsFalse() {
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", -1000L);
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 1L, "USER");
        assertFalse(jwtTokenUtil.validateToken(token));
    }

    @Test
    @DisplayName("解析非法令牌返回 null 而非抛异常")
    void parseInvalidTokenReturnsNull() {
        assertNull(jwtTokenUtil.getUsernameFromToken("not-a-jwt"));
        assertNull(jwtTokenUtil.getUserIdFromToken("not-a-jwt"));
        assertNull(jwtTokenUtil.getUserRoleFromToken("not-a-jwt"));
    }

    @Test
    @DisplayName("签名不匹配的令牌解析失败且不抛出")
    void parseTokenWithWrongSignature() {
        JwtTokenUtil other = new JwtTokenUtil();
        ReflectionTestUtils.setField(other, "secret", SECRET + "-different-suffix-value-for-hs512");
        ReflectionTestUtils.setField(other, "expiration", 3600_000L);
        String foreignToken = other.generateToken(userDetails("mallory"), 9L, "ADMIN");

        assertNull(jwtTokenUtil.getUsernameFromToken(foreignToken));
    }

    @Test
    @DisplayName("刷新令牌保留原有声明并延长有效期")
    void refreshTokenKeepsClaims() {
        String token = jwtTokenUtil.generateToken(userDetails("alice"), 7L, "USER");
        String refreshed = jwtTokenUtil.refreshToken(token);

        assertNotEquals(token, refreshed);
        assertEquals("alice", jwtTokenUtil.getUsernameFromToken(refreshed));
        assertEquals(7L, jwtTokenUtil.getUserIdFromToken(refreshed));
        assertEquals("USER", jwtTokenUtil.getUserRoleFromToken(refreshed));
        assertTrue(jwtTokenUtil.validateToken(refreshed));
    }

    @Test
    @DisplayName("过期令牌不可刷新，抛出 ExpiredJwtException")
    void refreshExpiredTokenThrows() {
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", -1000L);
        String expired = jwtTokenUtil.generateToken(userDetails("alice"), 1L, "USER");
        assertThrows(ExpiredJwtException.class, () -> jwtTokenUtil.refreshToken(expired));
    }
}
