# local-life-service

基于 Spring Boot + MyBatis-Plus + Redis 的本地生活服务后端。重点是把 Redis 用在真实业务问题上：多级缓存策略、分布式锁、Redis 消息队列异步下单、位图与基数统计、地理位置检索。

## 技术栈

Spring Boot 2.7.4 · JDK 17 · MyBatis-Plus 3.5.2 · MySQL 8 · Redis (Lettuce) · Redisson · Hutool · Lombok

## 功能

- **用户登录**：手机验证码，Token 存 Redis 做服务端会话；双拦截器分别负责 Token 续期与登录校验
- **商户查询**：`CacheClient` 统一封装缓存空值、互斥锁重建、逻辑过期三种策略
- **分布式锁**：手写 `SETNX` + Lua 解锁（解决误删），秒杀场景换 Redisson 可重入锁
- **优惠券秒杀**：`seckill.lua` 原子完成库存判断 / 一人一单 / 扣减 / 投递，订单经 Redis Stream 异步落库；`RedisIdWorker` 生成全局唯一 id
- **探店笔记与社交**：点赞用 Set + ZSet 排行，关注用 Set 交集，关注流用 ZSet 做滚动分页
- **签到与统计**：BitMap 存签到并统计连续天数，HyperLogLog 统计站点 UV
- **附近商户**：GEO 按坐标 + 半径检索并排序

## 快速开始

```bash
# 1. 建库并导入数据
mysql -uroot -p -e "CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p hmdp < hmdp.sql

# 2. 按本机环境改 application.yaml 里的 MySQL / Redis 地址与密码

# 3. 启动（默认端口 8081）
mvn clean package -DskipTests
java -jar target/local-life-service-1.0-SNAPSHOT.jar
```

或直接运行 `com.chen.locallife.LocalLifeApplication`。

使用秒杀功能前，需在 Redis 中创建订单 Stream 与消费组：

```text
XGROUP CREATE stream.orders g1 $ MKSTREAM
```

## Redis 键设计（节选）

| Key | 类型 | 用途 |
| --- | --- | --- |
| `login:token:{token}` | String | 登录会话 |
| `cache:shop:{id}` | String | 商户缓存（逻辑过期 / 空值） |
| `lock:shop:{id}` | String | 缓存重建互斥锁 |
| `seckill:stock:{voucherId}` / `seckill:order:{voucherId}` | String / Set | 秒杀库存 / 一人一单 |
| `stream.orders` | Stream | 异步订单队列 |
| `shop:geo:{typeId}` | GEO | 商户位置 |
| `sign:{userId}:{yyyyMM}` | BitMap | 月度签到 |
| `uv:{yyyyMMdd}` | HyperLogLog | 站点 UV |
| `feed:{userId}` | ZSet | 关注流收件箱 |

## 项目结构

```
src/main/java/com/chen/locallife
├── config        # MVC、MyBatis-Plus、Redisson、全局异常
├── controller    # 对外接口
├── entity / dto  # 实体与传输对象
├── interceptor   # Token 续期、登录校验
├── mapper        # MyBatis-Plus Mapper
├── service       # 业务接口与实现
└── utils         # 缓存客户端、分布式锁、id 生成器
src/main/resources
├── application.yaml
├── mapper        # 自定义 SQL
├── seckill.lua / unlock.lua
```
