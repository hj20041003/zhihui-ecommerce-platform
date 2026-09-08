package com.zhihui.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 全域数据采集服务
 * 负责模拟数据（商品/用户/订单）生成与持久化
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DataApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataApplication.class, args);
    }
}
