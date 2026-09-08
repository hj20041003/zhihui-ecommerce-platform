package com.zhihui.data.service;

import com.zhihui.data.dto.OrderLiveDTO;
import com.zhihui.data.entity.OrderItem;
import com.zhihui.data.entity.SalesOrder;
import com.zhihui.data.repository.OrderItemRepository;
import com.zhihui.data.repository.OrderRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 实时订单流组装：订单 + 明细摘要 -> DTO */
@Service
@RequiredArgsConstructor
public class OrderFeedService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DataGeneratorService dataGeneratorService;

    /** 最新订单（默认取 20 条） */
    public List<OrderLiveDTO> latestOrders(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<SalesOrder> orders = orderRepository.findTop30ByOrderByCreatedAtDesc();
        List<Long> orderIds = orders.stream().map(SalesOrder::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .limit(safeLimit)
                .map(o -> toDTO(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    /** 生成一笔"刚发生"的实时订单并返回 DTO */
    public List<OrderLiveDTO> generateLive(int count) {
        return dataGeneratorService.generateLiveOrders(Math.min(Math.max(count, 1), 5)).stream()
                .map(o -> toDTO(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    private OrderLiveDTO toDTO(SalesOrder o, List<OrderItem> items) {
        String summary;
        if (items.isEmpty()) {
            summary = "（无明细）";
        } else if (items.size() == 1) {
            OrderItem it = items.get(0);
            summary = it.getQuantity() > 1
                    ? it.getProductName() + " ×" + it.getQuantity()
                    : it.getProductName();
        } else {
            summary = items.get(0).getProductName() + " 等" + items.size() + "件";
        }
        return new OrderLiveDTO(
                o.getOrderNo(),
                o.getChannel(),
                o.getRegion(),
                summary,
                items.stream().mapToInt(OrderItem::getQuantity).sum(),
                o.getAmount(),
                o.getStatus(),
                statusLabel(o.getStatus()),
                o.getCreatedAt().format(TS),
                o.getPayTime() == null ? null : o.getPayTime().format(TS));
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case SalesOrder.PAID -> "已支付";
            case SalesOrder.PENDING -> "待支付";
            case SalesOrder.REFUNDED -> "已退款";
            default -> status;
        };
    }
}
