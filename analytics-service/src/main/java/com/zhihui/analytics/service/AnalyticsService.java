package com.zhihui.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 指标聚合分析（直接 SQL 聚合，避免全表加载）
 * 口径：GMV / 客单价只统计已支付订单；转化率 = 已支付 / 总订单；退款率 = 已退款 / 总订单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    /** KPI 总览：今日指标 + 昨日对比 + 累计 GMV */
    public Map<String, Object> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        Map<String, Object> today = windowStats(todayStart, LocalDateTime.now());
        Map<String, Object> yesterday = windowStats(yesterdayStart, todayStart);
        Map<String, Object> total = windowStats(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today);
        result.put("yesterday", yesterday);
        result.put("compare", compare(today, yesterday));
        result.put("totalGmv", total.get("gmv"));
        result.put("totalOrders", total.get("orders"));
        return result;
    }

    /** 近 N 天 GMV 与订单量趋势（缺数日期补零） */
    public List<Map<String, Object>> trend(int days) {
        int safeDays = Math.min(Math.max(days, 7), 90);
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        String sql = """
                SELECT DATE(created_at) d,
                       SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) gmv,
                       COUNT(*) orders
                FROM orders
                WHERE created_at >= ?
                GROUP BY DATE(created_at)
                """;
        Map<LocalDate, Object[]> byDay = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            LocalDate d = rs.getDate("d").toLocalDate();
            byDay.put(d, new Object[]{rs.getBigDecimal("gmv"), rs.getLong("orders")});
        }, Timestamp.valueOf(start.atStartOfDay()));

        List<Map<String, Object>> list = new ArrayList<>(safeDays);
        for (int i = 0; i < safeDays; i++) {
            LocalDate d = start.plusDays(i);
            Object[] v = byDay.getOrDefault(d, new Object[]{BigDecimal.ZERO, 0L});
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString().substring(5));
            row.put("gmv", round2((BigDecimal) v[0]));
            row.put("orders", v[1]);
            list.add(row);
        }
        return list;
    }

    /** 渠道销售占比（全量） */
    public List<Map<String, Object>> channelStats() {
        String sql = """
                SELECT channel,
                       SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) gmv,
                       COUNT(*) orders
                FROM orders
                GROUP BY channel
                ORDER BY gmv DESC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channel", rs.getString("channel"));
            row.put("gmv", round2(rs.getBigDecimal("gmv")));
            row.put("orders", rs.getLong("orders"));
            rows.add(row);
        });
        BigDecimal total = rows.stream().map(r -> (BigDecimal) r.get("gmv"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        rows.forEach(r -> r.put("pct", total.signum() == 0 ? 0.0
                : ((BigDecimal) r.get("gmv")).multiply(BigDecimal.valueOf(100))
                        .divide(total, 1, RoundingMode.HALF_UP).doubleValue()));
        return rows;
    }

    /** 品类销售 TOP（近 30 天，已支付） */
    public List<Map<String, Object>> categoryTop(int limit) {
        String sql = """
                SELECT oi.category, SUM(oi.amount) gmv, SUM(oi.quantity) qty
                FROM order_items oi
                JOIN orders o ON oi.order_id = o.id
                WHERE o.status = 'PAID' AND o.created_at >= ?
                GROUP BY oi.category
                ORDER BY gmv DESC
                LIMIT ?
                """;
        return queryTop(sql, Math.min(Math.max(limit, 1), 20), "category");
    }

    /** 商品销售 TOP10（近 30 天，已支付） */
    public List<Map<String, Object>> productTop(int limit) {
        String sql = """
                SELECT oi.product_name name, SUM(oi.amount) gmv, SUM(oi.quantity) qty
                FROM order_items oi
                JOIN orders o ON oi.order_id = o.id
                WHERE o.status = 'PAID' AND o.created_at >= ?
                GROUP BY oi.product_name
                ORDER BY gmv DESC
                LIMIT ?
                """;
        return queryTop(sql, Math.min(Math.max(limit, 1), 20), "name");
    }

    /** 地域分布（全量） */
    public List<Map<String, Object>> regionStats() {
        String sql = """
                SELECT region,
                       SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) gmv,
                       COUNT(*) orders
                FROM orders
                GROUP BY region
                ORDER BY gmv DESC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", rs.getString("region"));
            row.put("gmv", round2(rs.getBigDecimal("gmv")));
            row.put("orders", rs.getLong("orders"));
            rows.add(row);
        });
        return rows;
    }

    private Map<String, Object> windowStats(LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT COUNT(*) orders,
                       SUM(CASE WHEN status = 'PAID' THEN amount ELSE 0 END) gmv,
                       SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) paid,
                       SUM(CASE WHEN status = 'REFUNDED' THEN 1 ELSE 0 END) refunded
                FROM orders
                WHERE created_at >= ? AND created_at < ?
                """;
        Map<String, Object> row = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long orders = rs.getLong("orders");
            long paid = rs.getLong("paid");
            long refunded = rs.getLong("refunded");
            BigDecimal gmv = rs.getBigDecimal("gmv") == null ? BigDecimal.ZERO : rs.getBigDecimal("gmv");
            row.put("gmv", round2(gmv));
            row.put("orders", orders);
            row.put("aov", paid == 0 ? BigDecimal.ZERO
                    .doubleValue() : round2(gmv.divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_UP)));
            row.put("conversion", orders == 0 ? 0.0 : round1(paid * 100.0 / orders));
            row.put("refundRate", orders == 0 ? 0.0 : round1(refunded * 100.0 / orders));
        }, Timestamp.valueOf(from), Timestamp.valueOf(to));
        if (row.isEmpty()) {
            row.put("gmv", 0);
            row.put("orders", 0L);
            row.put("aov", 0);
            row.put("conversion", 0.0);
            row.put("refundRate", 0.0);
        }
        return row;
    }

    /** 今日 vs 昨日 环比变化（百分比，保留 1 位） */
    private Map<String, Object> compare(Map<String, Object> cur, Map<String, Object> prev) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("gmvDelta", pctChange(((BigDecimal) cur.get("gmv")).doubleValue(),
                ((BigDecimal) prev.get("gmv")).doubleValue()));
        map.put("ordersDelta", pctChange(((Number) cur.get("orders")).doubleValue(),
                ((Number) prev.get("orders")).doubleValue()));
        map.put("aovDelta", pctChange(((Number) cur.get("aov")).doubleValue(),
                ((Number) prev.get("aov")).doubleValue()));
        return map;
    }

    private static double pctChange(double cur, double prev) {
        if (prev == 0) {
            return cur == 0 ? 0.0 : 100.0;
        }
        return round1((cur - prev) / prev * 100.0);
    }

    private List<Map<String, Object>> queryTop(String sql, int limit, String nameKey) {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(nameKey, rs.getString(1));
            row.put("gmv", round2(rs.getBigDecimal("gmv")));
            row.put("qty", rs.getLong("qty"));
            rows.add(row);
        }, Timestamp.valueOf(LocalDate.now().minusDays(29).atStartOfDay()), limit);
        return rows;
    }

    private static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
