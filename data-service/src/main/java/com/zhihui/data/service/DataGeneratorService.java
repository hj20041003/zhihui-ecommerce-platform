package com.zhihui.data.service;

import com.zhihui.data.entity.AppUser;
import com.zhihui.data.entity.OrderItem;
import com.zhihui.data.entity.Product;
import com.zhihui.data.entity.SalesOrder;
import com.zhihui.data.repository.AppUserRepository;
import com.zhihui.data.repository.OrderItemRepository;
import com.zhihui.data.repository.OrderRepository;
import com.zhihui.data.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全域模拟数据生成引擎。
 * 拟真特征：渠道/地域按真实电商权重分布、周末 GMV 提升、晚间下单峰值、
 * 订单量随时间缓慢上涨、85% 支付 / 8% 待支付 / 7% 退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGeneratorService implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 全局订单号序列，保证唯一 */
    private static final AtomicLong ORDER_SEQ = new AtomicLong(0);

    /** 渠道 -> 权重 */
    private static final Map<String, Double> CHANNELS = new java.util.LinkedHashMap<>();
    /** 大区 -> 权重 */
    private static final Map<String, Double> REGIONS = new java.util.LinkedHashMap<>();
    /** 品类 -> (商品名 -> [最低价, 最高价]) */
    private static final Map<String, Map<String, double[]>> CATALOG = new java.util.LinkedHashMap<>();

    static {
        CHANNELS.put("天猫", 0.22);
        CHANNELS.put("京东", 0.20);
        CHANNELS.put("抖音直播", 0.18);
        CHANNELS.put("拼多多", 0.12);
        CHANNELS.put("微信小程序", 0.16);
        CHANNELS.put("线下门店", 0.12);

        REGIONS.put("华东", 0.30);
        REGIONS.put("华南", 0.18);
        REGIONS.put("华北", 0.17);
        REGIONS.put("华中", 0.12);
        REGIONS.put("西南", 0.11);
        REGIONS.put("东北", 0.07);
        REGIONS.put("西北", 0.05);

        CATALOG.put("数码家电", Map.of(
                "智辉无线蓝牙耳机", new double[]{199, 299},
                "智辉智能手表Pro", new double[]{899, 1299},
                "便携蓝牙音箱", new double[]{149, 299},
                "65W氮化镓充电器", new double[]{89, 129},
                "智能扫地机器人", new double[]{1299, 1999},
                "4K运动相机", new double[]{1599, 2199}));
        CATALOG.put("服饰鞋包", Map.of(
                "轻奢真皮手提包", new double[]{399, 799},
                "夏季冰丝T恤", new double[]{59, 129},
                "轻便运动跑步鞋", new double[]{269, 499},
                "弹力牛仔休闲裤", new double[]{129, 259},
                "防晒遮阳帽", new double[]{39, 89},
                "商务真皮钱包", new double[]{159, 329}));
        CATALOG.put("美妆个护", Map.of(
                "烟酰胺美白精华", new double[]{219, 359},
                "玻尿酸补水面膜", new double[]{79, 129},
                "氨基酸温和洗面奶", new double[]{49, 89},
                "丝绒哑光口红礼盒", new double[]{169, 299},
                "控油蓬松洗发水", new double[]{69, 119},
                "花果调香水礼盒", new double[]{299, 599}));
        CATALOG.put("食品生鲜", Map.of(
                "进口谷饲牛排套餐", new double[]{129, 269},
                "每日坚果礼盒30包", new double[]{99, 159},
                "有机纯牛奶整箱", new double[]{59, 99},
                "五常长粒香大米10kg", new double[]{79, 129},
                "智利车厘子JJ级2kg", new double[]{139, 219},
                "手工黄油曲奇饼干", new double[]{39, 79}));
        CATALOG.put("家居日用", Map.of(
                "慢回弹记忆棉枕头", new double[]{99, 199},
                "冰丝乳胶凉席三件套", new double[]{199, 359},
                "无烟香薰蜡烛礼盒", new double[]{79, 139},
                "折叠收纳箱三件套", new double[]{59, 109},
                "智能感应小夜灯", new double[]{29, 69},
                "静音加湿器迷你款", new double[]{89, 159}));
        CATALOG.put("运动户外", Map.of(
                "可折叠静音跑步机", new double[]{1599, 2599},
                "加厚防滑瑜伽垫", new double[]{59, 119},
                "可调节哑铃套装20kg", new double[]{299, 499},
                "骑行安全头盔", new double[]{129, 259},
                "双人防雨露营帐篷", new double[]{299, 599},
                "智能计数跳绳", new double[]{29, 59}));
    }

    /** 首次启动：订单表为空则生成近 45 天数据 */
    @Override
    public void run(ApplicationArguments args) {
        if (orderRepository.count() == 0) {
            log.info("[data-service] 订单表为空，开始生成 45 天全域模拟数据 ...");
            long start = System.currentTimeMillis();
            generate(45, false);
            log.info("[data-service] 模拟数据生成完成，耗时 {} ms", System.currentTimeMillis() - start);
        }
    }

    /** 生成 days 天数据；reset=true 时先清空全部业务表 */
    @Transactional
    public void generate(int days, boolean reset) {
        if (reset) {
            orderItemRepository.deleteAllInBatch();
            orderRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            log.info("[data-service] 已清空历史数据");
        }

        List<Product> products = productRepository.count() == 0 ? createProducts() : productRepository.findAll();
        List<AppUser> users = userRepository.count() == 0 ? createUsers(300) : userRepository.findAll();
        if (products.isEmpty() || users.isEmpty()) {
            throw new IllegalStateException("商品/用户基础数据缺失，无法生成订单");
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDate today = LocalDate.now();
        List<OrderItem> itemBuffer = new ArrayList<>(1024);

        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            // 基础单量 + 缓慢上涨趋势 + 周末高峰 + 随机波动
            double base = 55 + (days - offset) * 0.35;
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            if (weekend) {
                base *= 1.4;
            }
            int orderCount = (int) Math.round(base * rnd.nextDouble(0.85, 1.15));
            // 今天只生成"已过去时段"的订单，避免凌晨启动时今日 KPI 为 0
            boolean isToday = offset == 0;
            if (isToday) {
                long elapsedSec = java.time.Duration.between(day.atStartOfDay(), LocalDateTime.now()).getSeconds();
                double dayFraction = Math.min(1.0, elapsedSec / 86400.0);
                orderCount = Math.max(3, (int) Math.round(orderCount * dayFraction));
            }

            for (int i = 0; i < orderCount; i++) {
                LocalDateTime time;
                if (isToday) {
                    long maxSec = Math.max(60, java.time.Duration.between(day.atStartOfDay(), LocalDateTime.now()).getSeconds());
                    time = day.atStartOfDay().plusSeconds(rnd.nextLong(maxSec));
                } else {
                    time = day.atTime(pickHour(rnd), rnd.nextInt(60), rnd.nextInt(60));
                }
                String status = pickStatus(rnd);

                SalesOrder order = new SalesOrder();
                order.setOrderNo("ZH" + day.format(DAY_FMT)
                        + String.format("%06d", ORDER_SEQ.incrementAndGet() % 1_000_000));
                order.setUserId(users.get(rnd.nextInt(users.size())).getId());
                order.setChannel(pickWeighted(CHANNELS, rnd));
                order.setRegion(pickWeighted(REGIONS, rnd));
                order.setStatus(status);
                order.setCreatedAt(time);
                if (SalesOrder.PAID.equals(status)) {
                    order.setPayTime(time.plusSeconds(rnd.nextLong(30, 600)));
                }

                BigDecimal amount = BigDecimal.ZERO;
                int itemCount = rnd.nextInt(1, 4);
                for (int k = 0; k < itemCount; k++) {
                    Product p = products.get(rnd.nextInt(products.size()));
                    int qty = rnd.nextDouble() < 0.8 ? 1 : 2;
                    OrderItem item = new OrderItem();
                    item.setProductId(p.getId());
                    item.setProductName(p.getName());
                    item.setCategory(p.getCategory());
                    item.setUnitPrice(p.getPrice());
                    item.setQuantity(qty);
                    item.setAmount(p.getPrice().multiply(BigDecimal.valueOf(qty)));
                    amount = amount.add(item.getAmount());
                    itemBuffer.add(item);
                }
                order.setAmount(amount);
                SalesOrder saved = orderRepository.save(order);
                itemBuffer.subList(itemBuffer.size() - itemCount, itemBuffer.size())
                        .forEach(it -> it.setOrderId(saved.getId()));

                if (itemBuffer.size() >= 1000) {
                    orderItemRepository.saveAll(itemBuffer);
                    itemBuffer.clear();
                }
            }
            if (offset % 10 == 0) {
                log.info("[data-service] 已生成 {} (剩余 {} 天)", day, offset);
            }
        }
        orderItemRepository.saveAll(itemBuffer);
        itemBuffer.clear();
        orderItemRepository.flush();
        log.info("[data-service] 数据生成完毕: products={}, users={}, orders={}",
                productRepository.count(), userRepository.count(), orderRepository.count());
    }

    /** 实时订单流：即时生成 count 笔"刚发生"的订单并入库，返回带主键的订单与其明细 */
    @Transactional
    public List<SalesOrder> generateLiveOrders(int count) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Product> products = productRepository.findAll();
        List<AppUser> users = userRepository.findAll();
        if (products.isEmpty() || users.isEmpty()) {
            return List.of();
        }
        List<SalesOrder> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LocalDateTime time = LocalDateTime.now().minusSeconds(rnd.nextLong(0, 20));
            String status = rnd.nextDouble() < 0.9 ? SalesOrder.PAID : SalesOrder.PENDING;

            SalesOrder order = new SalesOrder();
            order.setOrderNo("ZH" + time.format(TS_FMT)
                    + String.format("%04d", ORDER_SEQ.incrementAndGet() % 10_000));
            order.setUserId(users.get(rnd.nextInt(users.size())).getId());
            order.setChannel(pickWeighted(CHANNELS, rnd));
            order.setRegion(pickWeighted(REGIONS, rnd));
            order.setStatus(status);
            order.setCreatedAt(time);
            if (SalesOrder.PAID.equals(status)) {
                order.setPayTime(time.plusSeconds(rnd.nextLong(5, 120)));
            }

            BigDecimal amount = BigDecimal.ZERO;
            int itemCount = rnd.nextInt(1, 4);
            List<OrderItem> items = new ArrayList<>(itemCount);
            for (int k = 0; k < itemCount; k++) {
                Product p = products.get(rnd.nextInt(products.size()));
                int qty = rnd.nextDouble() < 0.8 ? 1 : 2;
                OrderItem item = new OrderItem();
                item.setProductId(p.getId());
                item.setProductName(p.getName());
                item.setCategory(p.getCategory());
                item.setUnitPrice(p.getPrice());
                item.setQuantity(qty);
                item.setAmount(p.getPrice().multiply(BigDecimal.valueOf(qty)));
                amount = amount.add(item.getAmount());
                items.add(item);
            }
            order.setAmount(amount);
            SalesOrder saved = orderRepository.save(order);
            items.forEach(it -> it.setOrderId(saved.getId()));
            orderItemRepository.saveAll(items);
            result.add(saved);
        }
        return result;
    }

    private List<Product> createProducts() {
        List<Product> list = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        CATALOG.forEach((category, productsMap) -> productsMap.forEach((name, range) -> {
            BigDecimal price = BigDecimal.valueOf(rnd.nextDouble(range[0], range[1]))
                    .setScale(2, RoundingMode.HALF_UP);
            Product p = new Product();
            p.setName(name);
            p.setCategory(category);
            p.setPrice(price);
            p.setCost(price.multiply(BigDecimal.valueOf(rnd.nextDouble(0.45, 0.7)))
                    .setScale(2, RoundingMode.HALF_UP));
            list.add(p);
        }));
        return productRepository.saveAll(list);
    }

    private List<AppUser> createUsers(int n) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<AppUser> users = new ArrayList<>(n);
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= n; i++) {
            AppUser u = new AppUser();
            u.setNickname("智粉" + String.format("%04d", i));
            u.setRegion(pickWeighted(REGIONS, rnd));
            u.setRegisterChannel(pickWeighted(CHANNELS, rnd));
            u.setRegisteredAt(today.minusDays(rnd.nextLong(1, 365)));
            users.add(u);
        }
        return userRepository.saveAll(users);
    }

    private static String pickWeighted(Map<String, Double> weights, ThreadLocalRandom rnd) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = rnd.nextDouble() * total;
        double acc = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            acc += e.getValue();
            if (roll < acc) {
                return e.getKey();
            }
        }
        return weights.keySet().iterator().next();
    }

    /** 下单小时分布：晚间 20-22 点为峰值 */
    private static int pickHour(ThreadLocalRandom rnd) {
        int[] hours = {9, 10, 10, 11, 11, 12, 14, 14, 15, 15, 16, 19, 20, 20, 20, 21, 21, 21, 22, 22, 23};
        return hours[rnd.nextInt(hours.length)];
    }

    private static String pickStatus(ThreadLocalRandom rnd) {
        double r = rnd.nextDouble();
        if (r < 0.85) {
            return SalesOrder.PAID;
        }
        if (r < 0.93) {
            return SalesOrder.PENDING;
        }
        return SalesOrder.REFUNDED;
    }
}
