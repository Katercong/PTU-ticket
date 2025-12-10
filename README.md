# PTU-Ticket 高并发铁路购票系统

本项目是一个基于 Spring Boot 的单体架构应用，模拟了 12306 铁路购票系统的高并发场景，重点解决**缓存击穿**与**超卖**问题。项目专为面试设计，剔除了微服务包袱，直击底层核心技术原理。

## 架构亮点

1. **Redis Cache-Aside 与 Redisson 分布式锁**：在查询余票时使用旁路缓存策略，并在缓存失效时通过分布式锁防止缓存击穿保护 MySQL。
2. **Redis Lua 脚本原子防超卖**：利用 Lua 脚本在 Redis 内存中原子性地扣减库存，从根本上解决高并发下的超卖问题。
3. **RocketMQ 异步流量削峰**：扣减库存成功后立即通过 MQ 发送异步消息，实现极速响应，保护数据库免受洪峰冲击。
4. **数据库唯一索引防重幂等**：订单表配置订单号唯一约束，防止 MQ 重复消费导致的重复扣款。
5. **设计模式实战**：结合了“责任链模式”进行订单校验拦截，以及“策略模式”进行票价动态计算。

## 环境依赖

本应用依赖于 MySQL、Redis 和 RocketMQ。服务均已配置可用：

### 1. MySQL (端口 3306)
- 运行在宿主机 3306 端口。
- 账号：`root`，密码：`123456`。
- 需手动创建数据库 `ptu_ticket` 以及相应表结构（`ticket_route` 和 `ticket_order`）。

### 2. Redis (端口 6379)
- 运行在宿主机 6379 端口。

### 3. RocketMQ (端口 9876, 10911)
- 通过 Docker Compose 运行，版本为 `4.9.4`（匹配 Spring Boot RocketMQ Starter 2.3.0）。
- 启动命令：
```bash
docker compose up -d
```
- NameServer 端口：`9876`
- Broker 端口：`10911`

## 编译与启动

使用 Java 17 与 Maven 3 进行构建。

编译项目：
```bash
mvn clean compile
```

启动项目：
```bash
mvn spring-boot:run
```
