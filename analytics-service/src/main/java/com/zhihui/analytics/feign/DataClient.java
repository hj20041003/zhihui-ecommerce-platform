package com.zhihui.analytics.feign;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 跨服务调用示例：通过注册中心负载均衡调用 data-service
 */
@FeignClient(name = "data-service", contextId = "dataClient")
public interface DataClient {

    @GetMapping("/api/data/orders/latest")
    List<Map<String, Object>> getLatestOrders(@RequestParam("limit") int limit);

    @PostMapping("/api/data/orders/live")
    List<Map<String, Object>> generateLiveOrders(@RequestParam("count") int count);
}
