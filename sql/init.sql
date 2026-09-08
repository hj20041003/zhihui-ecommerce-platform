-- ============================================================
-- 智辉电商全域运营数据分析平台 - 数据库参考 DDL
-- 说明：演示模式下 JPA ddl-auto=update 会自动建表，本文件仅作
--       手工初始化 / 生产迁移（如 Flyway）的参考基线。
-- ============================================================

CREATE DATABASE IF NOT EXISTS zhihui_mall
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zhihui_mall;

CREATE TABLE IF NOT EXISTS products (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  name      VARCHAR(64)  NOT NULL COMMENT '商品名',
  category  VARCHAR(32)  NOT NULL COMMENT '品类',
  price     DECIMAL(10,2) NOT NULL,
  cost      DECIMAL(10,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品维表';

CREATE TABLE IF NOT EXISTS users (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  nickname         VARCHAR(32) NOT NULL,
  region           VARCHAR(16) NOT NULL,
  register_channel VARCHAR(16) NOT NULL,
  registered_at    DATE        NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户维表';

CREATE TABLE IF NOT EXISTS orders (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no   VARCHAR(32)  NOT NULL UNIQUE,
  user_id    BIGINT       NOT NULL,
  channel    VARCHAR(16)  NOT NULL COMMENT '天猫/京东/抖音直播/拼多多/微信小程序/线下门店',
  region     VARCHAR(16)  NOT NULL,
  amount     DECIMAL(12,2) NOT NULL,
  status     VARCHAR(16)  NOT NULL COMMENT 'PAID/PENDING/REFUNDED',
  created_at DATETIME(6)  NOT NULL,
  pay_time   DATETIME(6)  NULL,
  INDEX idx_orders_created_at (created_at),
  INDEX idx_orders_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单事实表';

CREATE TABLE IF NOT EXISTS order_items (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id     BIGINT       NOT NULL,
  product_id   BIGINT       NOT NULL,
  product_name VARCHAR(64)  NOT NULL,
  category     VARCHAR(32)  NOT NULL,
  unit_price   DECIMAL(10,2) NOT NULL,
  quantity     INT          NOT NULL,
  amount       DECIMAL(12,2) NOT NULL,
  INDEX idx_items_order_id (order_id),
  INDEX idx_items_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 平台账号表（auth-service 首次启动也会自动创建，此处为参考基线）
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(32)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  role          VARCHAR(16)  NOT NULL DEFAULT 'VIEWER' COMMENT 'ADMIN/VIEWER',
  enabled       TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号表';
