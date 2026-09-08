# 智辉电商全域运营数据分析平台

基于 **Spring Cloud 微服务** 的电商全域运营数据分析平台演示项目。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 微服务框架 | Java 17 · Spring Boot 3.2.12 · Spring Cloud 2023.0.5 |
| 注册中心 | Netflix Eureka |
| API 网关 | Spring Cloud Gateway（lb:// 负载均衡路由） |
| 跨服务调用 | OpenFeign |
| 认证鉴权 | JWT（HS256，jjwt 0.12.6）+ BCrypt 密码 + RBAC（ADMIN/VIEWER）+ 网关全局过滤 |
| 实时推送 | SSE（Server-Sent Events，2 秒/帧） |
| 数据库 | MySQL 8.0（库名 `zhihui_mall`） |
| 缓存 | Redis（Cache-Aside + TTL 60s，不可用时自动降级直连数据库） |
| 前端 | Vue 3 + Vite 5 + ECharts 5 深色数据大屏 |

## 架构

```
                        ┌────────────────────┐
                        │   浏览器数据大屏     │  http://localhost:5173
                        └─────────┬──────────┘
                                  │ /api/**（Vite 代理）
                        ┌─────────▼──────────┐
                        │  gateway-server    │  :19080
                        │ 网关 + 全局JWT鉴权  │
                        └───┬───────────┬────┘
              /api/data/**  │           │  /api/analytics/**
              ┌─────────────▼──┐   ┌────▼───────────────┐
              │  data-service  │   │ analytics-service  │
              │  :8101         │◄──│ :8102              │
              │ 模拟数据采集/存储 │ Feign │ 指标聚合 + SSE  │
              └───────┬────────┘   └────┬───────────────┘
                      │    JPA / JDBC   │
                      └────────┬────────┘
                        ┌──────▼───────┐
                        │ MySQL 8.0    │  zhihui_mall
                        └──────────────┘

              eureka-server :8761（服务注册与发现，所有服务经 lb:// 互访）
```

## 快速开始

### 前置条件
- MySQL 8.0 运行在本机 3306 端口，账号密码见各服务 `application.yml`
- Node.js ≥ 18（前端 dev server）

### 一键启动
```bat
:: 1. 先构建（如 target 里已有 jar 可跳过）
scripts\build.bat   :: 或手工: mvn -DskipTests package

:: 2. 启动全部服务（依次拉起 6 个窗口）
scripts\start-all.bat

:: 3. 打开大屏
:: http://localhost:5173
:: 注册中心面板: http://localhost:8761
```

停止：`scripts\stop-all.bat`

### 手工构建（本仓库自带便携工具链）
```bash
# Git Bash 下
export JAVA_HOME="C:/Users/hongj/WorkBuddy/2026-09-08-02-20-00/tools/jdk-17.0.2"
mvn -s ../tools/mvn-settings.xml -DskipTests package
```

## 服务与接口清单

| 服务 | 端口 | 职责 |
| --- | --- | --- |
| eureka-server | 8761 | 服务注册与发现（控制台即首页） |
| gateway-server | 19080 | 统一入口、CORS、lb:// 路由、全局 JWT 鉴权 |
| auth-service | 8103 | 账号体系：JWT 签发/刷新、用户管理（RBAC） |
| data-service | 8101 | 全域渠道模拟数据生成与存储 |
| analytics-service | 8102 | 指标聚合分析、OpenFeign 跨服务调用、SSE 推送 |

### 内置账号
| 账号 | 密码 | 角色 | 权限 |
| --- | --- | --- | --- |
| admin | admin123 | ADMIN | 查看大屏 + 账号管理（建号/改密/停用） |
| viewer | viewer123 | VIEWER | 只读查看大屏 |

> 首次启动 auth-service 自动建表并初始化内置账号（表 `sys_user`，BCrypt 加密）。

### 认证机制
- 登录 `POST /api/auth/login` 签发 **30 分钟 accessToken**（前端内存持有，不落 localStorage）+ **7 天 refreshToken**（HttpOnly Cookie `zh_refresh` 下发，不进 JS/URL）
- 网关全局过滤器统一验签：白名单（login/refresh/health/actuator）外的请求必须携带 `Authorization: Bearer <token>`，401 拒绝
- 网关剥离客户端伪造的 `X-User-*` 头，验签通过后注入真实 `X-User-Name` / `X-User-Role`，下游服务只信任网关注入的身份
- 前端 401 自动用 refreshToken 静默续期并重放请求；SSE 因 EventSource 无法带认证头，改用 fetch 流读取实现
- RBAC：账号管理接口（`GET/POST /api/auth/users`）仅 ADMIN 可访问，其余角色 403

### auth-service API（网关前缀 /api/auth）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /login | 登录，返回 accessToken（refreshToken 走 HttpOnly Cookie） |
| POST | /refresh | 用 Cookie 中的 refreshToken 换新 accessToken |
| POST | /logout | 清除刷新 Cookie |
| GET | /me | 当前登录用户信息 |
| GET | /users | 账号列表（仅 ADMIN） |
| POST | /users | 创建账号（仅 ADMIN，角色 ADMIN/VIEWER） |
| POST | /password | 修改本人密码 |
| GET | /health | 健康检查（免鉴权） |

### data-service API（网关前缀 /api/data）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /health | 健康检查 + 数据量统计 |
| GET | /stats/summary | 累计 GMV / 订单 / 用户 / 商品 |
| GET | /orders/latest?limit=10 | 最新订单 |
| POST | /orders/live?count=2 | 即时生成实时订单 |
| POST | /generate?days=45&reset=false | 重建模拟数据（reset=true 清空重来） |
| GET | /products | 商品列表 |

### analytics-service API（网关前缀 /api/analytics）
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /overview | 今日 KPI + 昨日环比 + 累计 GMV |
| GET | /trend?days=30 | 近 N 天 GMV / 订单趋势 |
| GET | /channel | 渠道销售占比 |
| GET | /category?limit=8 | 品类销售 TOP |
| GET | /product?limit=10 | 热销商品 TOP10 |
| GET | /region | 地域（七大区）分布 |
| GET | /stream | **SSE** 实时订单流（text/event-stream） |
| GET | /cache/stats | 缓存命中率 / TTL / 是否降级 |
| POST | /cache/clear | 清理全部指标缓存（数据重建后调用） |

### Redis 缓存设计（analytics-service）
- **模式**：Cache-Aside。读请求先查 Redis，命中直接返回；未命中回源 MySQL 后写回，TTL 60s（`cache.ttl-seconds` 可调）
- **key 规范**：`zhihui:analytics:{接口}[:参数]`，如 `zhihui:analytics:overview`、`zhihui:analytics:trend:30`
- **优雅降级**：Redis 不可用时自动跳过缓存直连数据库，冷却 30 秒后重新探测，接口行为不受影响（无 Redis 环境照常启动）
- **一致性**：聚合指标为"读多写少 + 允许短暂陈旧"，采用 TTL 兜底 + 数据重建后手动失效（`POST /api/analytics/cache/clear`）
- **金额精度**：反序列化开启 `USE_BIG_DECIMAL_FOR_FLOATS`，避免 1234.50 变成 1234.5
- **实测**（本机 Redis 3.2.100）：预热 6 次 MISS，随后 12 次 HIT，命中率 66.67%；命中响应 ~9ms vs 回源 ~29ms（演示数据量小，生产聚合越重收益越大）
- **生产提示**：`KEYS` 前缀清理在演示规模可用，key 量大时改 `SCAN` 渐进遍历；热点 key 可加随机抖动 TTL 防雪崩，或加互斥锁/逻辑过期防击穿

## 数据口径
- GMV / 客单价只统计 **已支付（PAID）** 订单
- 支付转化率 = 已支付订单 / 总订单
- 退款率 = 已退款 / 总订单
- 模拟数据特征：6 大渠道加权分布、7 大区、周末峰值、晚间下单高峰、订单量随时间缓慢上涨

## 生产化改造建议（本次为演示版）
1. **配置安全**：数据库口令改用环境变量注入（`${DB_PASSWORD}`），接入配置中心
2. **数据库迁移**：`ddl-auto=update` 换成 Flyway/Liquibase 版本化迁移（参考 `sql/init.sql`）
3. **认证加固**：已实现 JWT + 刷新令牌 + RBAC 基础版；生产需更换 `jwt.secret`、refreshToken 改服务端存储（Redis 可吊销）、增加登录限流与失败锁定
4. **可观测性**：引入 Micrometer + Prometheus + Grafana，链路追踪（Micrometer Tracing）
5. **SSE 扩展**：多实例部署时用 Redis Pub/Sub 广播事件
6. **CORS 收敛**：网关 `allowedOriginPatterns` 收紧为正式域名
