package com.zhihui.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 数据分析服务
 * KPI 聚合 + 趋势/渠道/品类/地域分析 + SSE 实时订单流
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
