package com.zhihui.auth.service;

import com.zhihui.auth.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 账号与令牌业务逻辑。角色：ADMIN（管理员，可建号）/ VIEWER（只读）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-ttl-seconds:1800}")
    private long accessTtlSeconds;

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jwtUtil = new JwtUtil(secret, accessTtlSeconds);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                  username      VARCHAR(32)  NOT NULL UNIQUE,
                  password_hash VARCHAR(100) NOT NULL,
                  role          VARCHAR(16)  NOT NULL DEFAULT 'VIEWER',
                  enabled       TINYINT      NOT NULL DEFAULT 1,
                  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号表'
                """);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        if (count != null && count == 0) {
            createUserInternal("admin", "admin123", "ADMIN");
            createUserInternal("viewer", "viewer123", "VIEWER");
            log.info("[auth-service] 已初始化内置账号: admin/admin123 (ADMIN), viewer/viewer123 (VIEWER)");
        }
    }

    public Map<String, Object> login(String username, String rawPassword) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, password_hash, role, enabled FROM sys_user WHERE username = ?", username);
        if (rows.isEmpty() || !encoder.matches(rawPassword, (String) rows.get(0).get("password_hash"))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        if (((Number) user.get("enabled")).intValue() != 1) {
            throw new IllegalArgumentException("账号已停用");
        }
        String role = (String) user.get("role");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", jwtUtil.generateAccessToken(username, role));
        result.put("refreshToken", jwtUtil.generateRefreshToken(username));
        result.put("username", username);
        result.put("role", role);
        return result;
    }

    /** 用刷新令牌换新访问令牌，返回 username/role 供前端恢复会话 */
    public Map<String, Object> refresh(String refreshToken) {
        var claims = jwtUtil.parseRefreshToken(refreshToken);
        String username = claims.getSubject();
        String role = jdbc.queryForObject(
                "SELECT role FROM sys_user WHERE username = ? AND enabled = 1", String.class, username);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", jwtUtil.generateAccessToken(username, role));
        result.put("username", username);
        result.put("role", role);
        return result;
    }

    public Map<String, Object> createUser(String username, String rawPassword, String role, String operator) {
        if (!"ADMIN".equals(role) && !"VIEWER".equals(role)) {
            throw new IllegalArgumentException("角色仅支持 ADMIN / VIEWER");
        }
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,32}$")) {
            throw new IllegalArgumentException("用户名须为 3-32 位字母/数字/下划线");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, username);
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        createUserInternal(username, rawPassword, role);
        log.info("[auth-service] 账号 {} 由 {} 创建，角色 {}", username, operator, role);
        return Map.of("username", username, "role", role);
    }

    public List<Map<String, Object>> listUsers() {
        return jdbc.queryForList(
                "SELECT username, role, enabled, created_at FROM sys_user ORDER BY id");
    }

    public void changePassword(String username, String oldPwd, String newPwd) {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = ?", String.class, username);
        if (hash == null || !encoder.matches(oldPwd, hash)) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (newPwd == null || newPwd.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE username = ?",
                encoder.encode(newPwd), username);
    }

    private void createUserInternal(String username, String rawPassword, String role) {
        jdbc.update("INSERT INTO sys_user(username, password_hash, role) VALUES (?, ?, ?)",
                username, encoder.encode(rawPassword), role);
    }
}
