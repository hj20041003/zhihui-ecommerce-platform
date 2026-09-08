package com.zhihui.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 工具（HS256）。密钥须与网关侧一致（生产环境放配置中心/KMS）。
 */
public final class JwtUtil {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtUtil(String secret, long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String generateAccessToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(7 * 24 * 3600)))
                .signWith(key)
                .compact();
    }

    /** 验签 + 过期校验，失败抛 JwtException */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** 仅允许 refresh 类型令牌通过 */
    public Claims parseRefreshToken(String token) throws JwtException {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new JwtException("非刷新令牌");
        }
        return claims;
    }
}
