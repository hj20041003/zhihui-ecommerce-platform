package com.zhihui.data.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 实时订单流推送对象（同时供 Feign 调用方反序列化） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderLiveDTO {

    private String orderNo;
    private String channel;
    private String region;
    /** 明细摘要：主商品名（多件时附 ×N 或"等M件"） */
    private String productSummary;
    private Integer itemCount;
    private BigDecimal amount;
    private String status;
    private String statusLabel;
    /** yyyy-MM-dd HH:mm:ss */
    private String time;
    /** yyyy-MM-dd HH:mm:ss */
    private String payTime;
}
