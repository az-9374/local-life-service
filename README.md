# local-life-service

基于 Spring Boot + MyBatis-Plus + Redis + RabbitMQ 的本地生活服务后端系统。重点解决高并发场景下的业务痛点，包括：多级缓存策略、分布式锁、基于 RabbitMQ 的异步秒杀削峰与可靠性保障、位图与基数统计、地理位置检索等。

## 技术栈

Spring Boot 2.7.4 · JDK 17 · MyBatis-Plus 3.5.2 · MySQL 8 · Redis (Lettuce) · RabbitMQ · Redisson · Hutool · Lombok

## 核心功能与优化点

- **用户登录**：手机验证码登录，Token 存 Redis 做服务端会话；双拦截器分别负责 Token 自动续期与登录校验，实现无状态认证。
- **商户查询（多级缓存）**：封装 `CacheClient` 工具类，统一处理缓存穿透、击穿、雪崩问题（空值缓存、互斥锁重建、逻辑过期策略）。
- **分布式锁**：手写 `SETNX` + Lua 脚本解锁（解决误删问题）；秒杀场景使用 Redisson 可重入锁保障并发安全。
- **优惠券秒杀（高并发削峰）**：
  - 基于 `seckill.lua` 脚本在 Redis 中原子完成库存判断、一人一单校验与扣减；基于 `limit.lua` 做用户维度的接口限流，防止恶意刷单。
  - 引入 **RabbitMQ** 替代原有 Stream 方案实现订单异步落库。基于 Direct 交换机精准路由，消费端采用**手动 ACK + 失败重新入队**保证消息不丢；生产环境可进一步配置死信队列与重试上限兜底。
  - **一致性保障**：设计本地补偿回滚机制（消息投递失败时自动恢复 Redis 库存），解决 Lua 扣减与 MQ 投递的非原子性问题。
- **探店笔记与社交**：点赞用 Set + ZSet 实现排行榜，关注用 Set 交集，关注流用 ZSet 实现滚动分页。
- **签到与统计**：BitMap 存储用户签到并统计连续天数，HyperLogLog 统计站点 UV。
- **附近商户**：基于 Redis GEO 数据结构，按坐标 + 半径检索并排序。

## 快速开始

```bash
# 1. 建库并导入数据
mysql -uroot -p -e "CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p hmdp < hmdp.sql

# 2. 修改配置文件
# 按本机环境修改 application.yaml 里的 MySQL、Redis、RabbitMQ 地址与账号密码

# 3. 启动项目（默认端口 8081）
mvn clean package -DskipTests
java -jar target/local-life-service-1.0-SNAPSHOT.jar
```

或直接在 IDE 中运行 `com.chen.locallife.LocalLifeApplication`。

> 注：秒杀功能依赖 RabbitMQ，请确保其正常运行；库存会在添加秒杀券时自动预热到 Redis。

## 核心组件设计（节选）

| Key / 组件 | 类型 | 用途 |
| --- | --- | --- |
| `login:token:{token}` | String | 登录会话 |
| `cache:shop:{id}` | String | 商户缓存（逻辑过期 / 空值缓存） |
| `lock:shop:{id}` | String | 缓存重建互斥锁 |
| `seckill:stock:{voucherId}` / `seckill:order:{voucherId}` | String / Set | 秒杀库存 / 一人一单标记 |
| `seckill.order.queue` | RabbitMQ Queue | 异步订单落库队列 |
| `shop:geo:{typeId}` | GEO | 商户位置信息 |
| `sign:{userId}:{yyyyMM}` | BitMap | 月度签到记录 |
| `uv:{yyyyMMdd}` | HyperLogLog | 站点 UV 统计 |
| `feed:{userId}` | ZSet | 关注流收件箱 |

## 项目结构

```text
src/main/java/com/chen/locallife
├── config        # MVC、MyBatis-Plus、Redisson、RabbitMQ、全局异常配置
├── controller    # 对外 API 接口
├── entity / dto  # 实体类与数据传输对象
├── interceptor   # Token 续期、登录校验拦截器
├── mapper        # MyBatis-Plus Mapper 接口
├── service       # 业务接口与实现类
└── utils         # 缓存客户端、分布式锁、全局 ID 生成器
src/main/resources
├── application.yaml
├── mapper        # 自定义 SQL 映射文件
├── seckill.lua   # 秒杀原子扣减脚本
├── unlock.lua    # 分布式锁解锁脚本
└── limit.lua     # 接口限流脚本
```