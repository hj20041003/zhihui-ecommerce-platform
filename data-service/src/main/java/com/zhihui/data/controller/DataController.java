package com.zhihui.data.controller;

import com.zhihui.data.dto.OrderLiveDTO;
import com.zhihui.data.entity.SalesOrder;
import com.zhihui.data.repository.AppUserRepository;
import com.zhihui.data.repository.OrderRepository;
import com.zhihui.data.repository.ProductRepository;
import com.zhihui.data.service.DataGeneratorService;
import com.zhihui.data.service.OrderFeedService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据采集服务对外 API（经网关前缀 /api/data）
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final DataGeneratorService dataGeneratorService;
    private final OrderFeedService orderFeedService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("service", "data-service");
        map.put("orders", orderRepository.count());
        map.put("products", productRepository.count());
        map.put("users", userRepository.count());
        return map;
    }

    @GetMapping("/stats/summary")
    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("products", productRepository.count());
        map.put("users", userRepository.count());
        map.put("orders", orderRepository.count());
        BigDecimal gmv = orderRepository.findAll().stream()
                .filter(o -> SalesOrder.PAID.equals(o.getStatus()))
                .map(SalesOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        map.put("totalGmv", gmv);
        return map;
    }

    @GetMapping("/orders/latest")
    public List<OrderLiveDTO> latest(@RequestParam(defaultValue = "10") int limit) {
        return orderFeedService.latestOrders(limit);
    }

    /** 实时订单流数据源：每次调用即时生成 1-3 笔订单 */
    @PostMapping("/orders/live")
    public List<OrderLiveDTO> live(@RequestParam(defaultValue = "2") int count) {
        return orderFeedService.generateLive(count);
    }

    /** 重新生成模拟数据（reset=true 清空重建） */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam(defaultValue = "45") int days,
            @RequestParam(defaultValue = "false") boolean reset) {
        if (days < 1 || days > 365) {
            return ResponseEntity.badRequest().body(Map.of("error", "days 取值应为 1-365"));
        }
        log.info("[data-service] 收到数据重建请求: days={}, reset={}", days, reset);
        dataGeneratorService.generate(days, reset);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", "数据生成完成");
        map.put("days", days);
        map.put("orders", orderRepository.count());
        return ResponseEntity.ok(map);
    }

    @GetMapping("/products")
    public Object products() {
        return productRepository.findAll();
    }
}
