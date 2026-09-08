package com.zhihui.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 订单事实表。状态：PAID 已支付 / PENDING 待支付 / REFUNDED 已退款 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_created_at", columnList = "created_at"),
        @Index(name = "idx_orders_channel", columnList = "channel")
})
public class SalesOrder {

    public static final String PAID = "PAID";
    public static final String PENDING = "PENDING";
    public static final String REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32, unique = true)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 下单渠道：天猫/京东/抖音直播/拼多多/微信小程序/线下门店 */
    @Column(nullable = false, length = 16)
    private String channel;

    /** 收货/门店所在大区 */
    @Column(nullable = false, length = 16)
    private String region;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "pay_time")
    private LocalDateTime payTime;
}
