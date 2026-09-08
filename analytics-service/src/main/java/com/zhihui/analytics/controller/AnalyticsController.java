package com.zhihui.analytics.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhihui.analytics.cache.CacheService;
import com.zhihui.analytics.service.AnalyticsService;
import com.zhihui.analytics.service.SsePushService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 数据分析 API（经网关前缀 /api/analytics）
 *
 * <p>聚合查询（overview/trend/channel/category/product/region）走 Redis Cache-Aside 缓存，
 * Redis 不可用时由 CacheService 自动降级为直连数据库，接口行为不受影响。
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SsePushService ssePushService;
    private final CacheService cacheService;

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return cacheService.getOrLoad("overview", new TypeReference<Map<String, Object>>() {
        }, analyticsService::overview);
    }

    @GetMapping("/trend")
    public Object trend(@RequestParam(defaultValue = "30") int days) {
        int safeDays = days;
        return cacheService.getOrLoad("trend:" + safeDays, new TypeReference<List<Map<String, Object>>>() {
        }, () -> analyticsService.trend(safeDays));
    }

    @GetMapping("/channel")
    public Object channel() {
        return cacheService.getOrLoad("channel", new TypeReference<List<Map<String, Object>>>() {
        }, analyticsService::channelStats);
    }

    @GetMapping("/category")
    public Object category(@RequestParam(defaultValue = "8") int limit) {
        int safeLimit = limit;
        return cacheService.getOrLoad("category:" + safeLimit, new TypeReference<List<Map<String, Object>>>() {
        }, () -> analyticsService.categoryTop(safeLimit));
    }

    @GetMapping("/product")
    public Object product(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = limit;
        return cacheService.getOrLoad("product:" + safeLimit, new TypeReference<List<Map<String, Object>>>() {
        }, () -> analyticsService.productTop(safeLimit));
    }

    @GetMapping("/region")
    public Object region() {
        return cacheService.getOrLoad("region", new TypeReference<List<Map<String, Object>>>() {
        }, analyticsService::regionStats);
    }

    /** SSE 实时订单流：GET /api/analytics/stream */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return ssePushService.register();
    }

    /** 缓存统计：命中率 / TTL / 是否降级 */
    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return cacheService.stats();
    }

    /** 清理全部指标缓存（数据重建后调用，保证不读到旧数据） */
    @PostMapping("/cache/clear")
    public Map<String, Object> cacheClear() {
        int deleted = cacheService.evictAll();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", "缓存已清理");
        map.put("deletedKeys", deleted);
        return map;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("service", "analytics-service");
        map.put("redis", cacheService.probe() ? "UP" : "DEGRADED");
        return map;
    }
}
