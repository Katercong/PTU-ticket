-- PTU-Ticket 数据库表结构
-- 使用前请先创建数据库：CREATE DATABASE ptu_ticket DEFAULT CHARACTER SET utf8mb4;
-- 然后执行本脚本即可。

-- ----------------------------
-- 车次 / 余票 / 票价
-- ----------------------------
DROP TABLE IF EXISTS `ticket_route`;
CREATE TABLE `ticket_route` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `train_number` VARCHAR(32)  NOT NULL COMMENT '车次号，如 G123',
    `stock`        INT          NOT NULL DEFAULT 0 COMMENT '剩余票数',
    `base_price`   DOUBLE       NOT NULL DEFAULT 0 COMMENT '基础票价',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次信息表';

-- ----------------------------
-- 车票（按出发站-到达站-日期维度）
-- ----------------------------
DROP TABLE IF EXISTS `ticket`;
CREATE TABLE `ticket` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `from_station` VARCHAR(64)  NOT NULL COMMENT '出发站',
    `to_station`   VARCHAR(64)  NOT NULL COMMENT '到达站',
    `date`         DATE         NOT NULL COMMENT '发车日期',
    `price`        DOUBLE       NOT NULL DEFAULT 0,
    `stock`        INT          NOT NULL DEFAULT 0,
    `type`         VARCHAR(16)  NOT NULL DEFAULT 'ADULT' COMMENT '票种：ADULT/STUDENT',
    PRIMARY KEY (`id`),
    -- 联合索引：余票查询接口以 (from_station, to_station, date) 为等值条件。
    -- 由于查询只 SELECT 这三列 + price/stock/type 都在表内，
    -- 联合索引同时可作为覆盖索引，避免回表，降低核心查询扫描行数。
    INDEX `idx_route_date` (`from_station`, `to_station`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车票表';

-- ----------------------------
-- 订单
-- ----------------------------
DROP TABLE IF EXISTS `ticket_order`;
CREATE TABLE `ticket_order` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `order_no`     VARCHAR(64)  NOT NULL COMMENT '订单号',
    `user_id`      BIGINT       NOT NULL,
    `train_number` VARCHAR(32)  NOT NULL,
    `final_price`  DOUBLE       NOT NULL DEFAULT 0,
    `status`       INT          NOT NULL DEFAULT 0 COMMENT '0:未支付 1:已支付',
    `create_time`  DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    -- 唯一约束：order_no 唯一，用于 MQ 重复消费时的幂等控制，防范重复下单/重复扣款。
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 初始化演示数据
-- ----------------------------
INSERT INTO `ticket_route` (`id`, `train_number`, `stock`, `base_price`) VALUES
    (1, 'G101', 50, 100.0);

INSERT INTO `ticket` (`from_station`, `to_station`, `date`, `price`, `stock`, `type`) VALUES
    ('Beijing', 'Shanghai', '2026-06-17', 100.0, 50, 'ADULT'),
    ('Beijing', 'Shanghai', '2026-06-17', 75.0,  50, 'STUDENT'),
    ('Beijing', 'Shanghai', '2026-06-18', 100.0, 50, 'ADULT'),
    ('Beijing', 'Shanghai', '2026-06-18', 75.0,  50, 'STUDENT');
