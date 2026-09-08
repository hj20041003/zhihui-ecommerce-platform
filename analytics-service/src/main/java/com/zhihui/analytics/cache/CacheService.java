package com.zhihui.analytics.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Cache-Aside 缓存服务（Redis）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>读流程：命中缓存直接返回；未命中回源数据库并写回，带 TTL（避免永久脏数据）</li>
 *   <li>缓存空值不写 null：本项目的聚合接口恒有数据（补零），故不存在缓存穿透的经典空值问题；
 *       若某接口可能返回空列表，仍会缓存空列表而不是 null</li>
 *   <li>优雅降级：Redis 不可用时自动跳过缓存直连数据库，30 秒后重试探测，保证演示环境无 Redis 也能启动</li>
 *   <li>失效：数据重建后调用 {@link #evictAll()} 按前缀清理（演示规模用 KEYS，生产建议改 SCAN 遍历）</li>
 * </ul>
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final String PREFIX = "zhihui:analytics:";
    private static final long DEGRADE_COOLDOWN_MS = 30_000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    private volatile boolean available = true;
    private volatile long nextProbeAt = 0L;

    public CacheService(StringRedisTemplate redis, @Value("${cache.ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.mapper = new ObjectMapper();
        // 反序列化浮点用 BigDecimal，避免金额 1234.50 变成 1234.5
        this.mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** 读缓存，未命中则回源并写回。type 用于 JSON 反序列化还原泛型结构 */
    public <T> T getOrLoad(String key, TypeReference<T> type, Supplier<T> loader) {
        String full = PREFIX + key;
        if (usable()) {
            try {
                String cached = redis.opsForValue().get(full);
                if (cached != null) {
                    hits.incrementAndGet();
                    return mapper.readValue(cached, type);
                }
                misses.incrementAndGet();
            } catch (Exception e) {
                degrade("读取缓存失败，key=" + full, e);
            }
        }

        T value = loader.get();

        if (usable() && value != null) {
            try {
                redis.opsForValue().set(full, mapper.writeValueAsString(value), ttl);
            } catch (Exception e) {
                degrade("写入缓存失败，key=" + full, e);
            }
        }
        return value;
    }

    /** 按前缀清理全部缓存（数据重建后调用） */
    public int evictAll() {
        if (!usable()) {
            return 0;
        }
        try {
            Set<String> keys = redis.keys(PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long deleted = redis.delete(keys);
            log.info("[analytics-service] 已清理缓存 {} 个 key", deleted);
            return deleted == null ? 0 : deleted.intValue();
        } catch (Exception e) {
            degrade("清理缓存失败", e);
            return 0;
        }
    }

    /** 缓存健康与统计信息，供 /cache/stats 展示 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        long h = hits.get();
        long m2 = misses.get();
        long total = h + m2;
        m.put("available", available);
        m.put("ttlSeconds", ttl.getSeconds());
        m.put("hits", h);
        m.put("misses", m2);
        m.put("errors", errors.get());
        m.put("hitRate", total == 0 ? "0.00%" : String.format("%.2f%%", h * 100.0 / total));
        m.put("keyPrefix", PREFIX);
        return m;
    }

    /** 探测 Redis 连通性：能执行任意命令即视为可用（不可用时抛异常并降级） */
    public boolean probe() {
        try {
            redis.hasKey(PREFIX + "__probe__");
            available = true;
            return true;
        } catch (Exception e) {
            degrade("Redis 探测失败", e);
            return false;
        }
    }

    private boolean usable() {
        if (available) {
            return true;
        }
        if (System.currentTimeMillis() >= nextProbeAt) {
            available = true; // 冷却结束，允许再试一次
        }
        return available;
    }

    private void degrade(String msg, Throwable t) {
        available = false;
        nextProbeAt = System.currentTimeMillis() + DEGRADE_COOLDOWN_MS;
        errors.incrementAndGet();
        log.warn("[analytics-service] {}，缓存降级 {}ms 内直连数据库: {}", msg, DEGRADE_COOLDOWN_MS, t.toString());
    }
}
