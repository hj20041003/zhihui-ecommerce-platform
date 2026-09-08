package com.zhihui.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/**
 * 网关侧 JWT 验签工具，密钥须与 auth-service 一致。
 */
public final class GatewayJwtUtil {

    private final SecretKey key;

    public GatewayJwtUtil(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 验签 + 过期校验，失败抛 JwtException */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
