package com.zhihui.auth.controller;

import com.zhihui.auth.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 API（网关前缀 /api/auth）。
 * 刷新令牌通过 HttpOnly Cookie 下发，不进入 localStorage/URL。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "zh_refresh";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
                                    @NotBlank String role) {}

    public record ChangePwdRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        try {
            Map<String, Object> result = authService.login(req.username(), req.password());
            ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, (String) result.remove("refreshToken"))
                    .httpOnly(true)
                    .path("/api/auth")
                    .maxAge(Duration.ofDays(7))
                    .sameSite("Strict")
                    .build();
            return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestParam(value = "refreshToken", required = false) String paramToken,
            @RequestHeader(value = "Cookie", required = false) String cookieHeader) {
        String token = paramToken != null ? paramToken : extractCookie(cookieHeader);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "缺少刷新令牌"));
        }
        try {
            return ResponseEntity.ok(authService.refresh(token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "登录已过期，请重新登录"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).path("/api/auth").maxAge(0).sameSite("Strict").build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString())
                .body(Map.of("message", "已退出登录"));
    }

    /** 网关注入 X-User-Name / X-User-Role（已剥离客户端伪造头） */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("X-User-Name") String username,
                                  @RequestHeader("X-User-Role") String role) {
        return Map.of("username", username, "role", role);
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestHeader("X-User-Role") String role) {
        ResponseEntity<?> denied = requireAdmin(role);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(authService.listUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestHeader("X-User-Role") String role,
                                        @RequestHeader("X-User-Name") String operator,
                                        @RequestBody CreateUserRequest req) {
        ResponseEntity<?> denied = requireAdmin(role);
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(authService.createUser(req.username(), req.password(), req.role(), operator));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@RequestHeader("X-User-Name") String username,
                                            @RequestBody ChangePwdRequest req) {
        try {
            authService.changePassword(username, req.oldPassword(), req.newPassword());
            return ResponseEntity.ok(Map.of("message", "密码已修改"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("service", "auth-service");
        return map;
    }

    private ResponseEntity<?> requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }
        return null;
    }

    private String extractCookie(String cookieHeader) {
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && REFRESH_COOKIE.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
