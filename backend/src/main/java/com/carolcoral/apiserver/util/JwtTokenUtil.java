/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌工具类
 *
 * @author carolcoral
 */
@Tag(name = "JWT工具", description = "JWT令牌生成和验证工具")
@Component
public class JwtTokenUtil {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 从令牌中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    @Operation(summary = "从令牌中获取用户名")
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("从令牌中获取用户名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token 令牌
     * @return 用户ID
     */
    @Operation(summary = "从令牌中获取用户ID")
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("从令牌中获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中获取用户角色
     *
     * @param token 令牌
     * @return 用户角色
     */
    @Operation(summary = "从令牌中获取用户角色")
    public String getUserRoleFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("从令牌中获取用户角色失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌中获取声明
     *
     * @param token 令牌
     * @return 声明
     */
    @Operation(summary = "从令牌中获取声明")
    private Claims getClaimsFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 生成令牌
     *
     * @param userDetails 用户信息
     * @param userId      用户ID
     * @param role        用户角色
     * @return 令牌
     */
    @Operation(summary = "生成令牌")
    public String generateToken(UserDetails userDetails, Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        return generateToken(claims, userDetails.getUsername());
    }

    /**
     * 生成令牌
     *
     * @param claims  声明
     * @param subject 主题
     * @return 令牌
     */
    @Operation(summary = "生成令牌")
    private String generateToken(Map<String, Object> claims, String subject) {
        Date createdDate = new Date();
        Date expirationDate = new Date(createdDate.getTime() + expiration);
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(createdDate)
                .expiration(expirationDate)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 验证令牌
     *
     * @param token       令牌
     * @param userDetails 用户信息
     * @return 是否有效
     */
    @Operation(summary = "验证令牌")
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * 验证令牌（仅验证格式和过期）
     *
     * @param token 令牌
     * @return 是否有效
     */
    @Operation(summary = "验证令牌（仅验证格式和过期）")
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("验证令牌失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断令牌是否过期
     *
     * @param token 令牌
     * @return 是否过期
     */
    @Operation(summary = "判断令牌是否过期")
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 从令牌中获取过期时间
     *
     * @param token 令牌
     * @return 过期时间
     */
    @Operation(summary = "从令牌中获取过期时间")
    private Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }

    /**
     * 刷新令牌
     *
     * @param token 旧令牌
     * @return 新令牌
     */
    @Operation(summary = "刷新令牌")
    public String refreshToken(String token) {
        final Claims oldClaims = getClaimsFromToken(token);
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        // 创建新的令牌，使用新的签发时间
        return Jwts.builder()
                .subject(oldClaims.getSubject())
                .claim("username", oldClaims.get("username"))
                .claim("userId", oldClaims.get("userId"))
                .claim("role", oldClaims.get("role"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 获取令牌过期时间
     *
     * @return 过期时间（毫秒）
     */
    @Operation(summary = "获取令牌过期时间")
    public Long getExpiration() {
        return expiration;
    }
}
