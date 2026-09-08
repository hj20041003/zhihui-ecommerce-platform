package com.zhihui.gateway.filter;

import com.zhihui.gateway.util.GatewayJwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局鉴权：
 * 1. 白名单放行（登录/刷新/健康检查/actuator）
 * 2. 其余请求校验 Bearer JWT（验签 + 过期）
 * 3. 剥离客户端伪造的 X-User-Name/X-User-Role，注入解析后的真实身份
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/health", "/actuator");

    private final GatewayJwtUtil jwtUtil;

    public AuthGlobalFilter(@Value("${jwt.secret}") String secret) {
        this.jwtUtil = new GatewayJwtUtil(secret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "未登录");
        }
        try {
            Claims claims = jwtUtil.parse(auth.substring(7).trim());
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Name");
                        h.remove("X-User-Role");
                        h.set("X-User-Name", claims.getSubject());
                        h.set("X-User-Role", String.valueOf(claims.get("role", String.class)));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "登录已过期，请重新登录");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
