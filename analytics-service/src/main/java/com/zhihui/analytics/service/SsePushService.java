package com.zhihui.analytics.service;

import com.zhihui.analytics.feign.DataClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 实时订单流推送：
 * 每 2 秒通过 OpenFeign 调用 data-service 生成并拉取最新订单，
 * 推送给所有已连接的大屏客户端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsePushService {

    private final DataClient dataClient;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-push");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::push, 3, 2, TimeUnit.SECONDS);
        log.info("[analytics-service] SSE 推送调度已启动（2s/次）");
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    /** 注册新的大屏连接 */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void push() {
        if (emitters.isEmpty()) {
            return;
        }
        List<Map<String, Object>> orders;
        try {
            // Feign 跨服务调用 -> data-service 即时生成最新订单
            orders = dataClient.generateLiveOrders(2);
        } catch (Exception e) {
            log.debug("[analytics-service] 拉取实时订单失败: {}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("orders").data(orders));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
