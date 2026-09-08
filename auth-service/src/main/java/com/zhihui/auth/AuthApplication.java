package com.zhihui.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 认证服务：登录 / 刷新令牌 / 账号管理（RBAC）
 * 内置账号：admin/admin123（管理员）、viewer/viewer123（只读）
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
