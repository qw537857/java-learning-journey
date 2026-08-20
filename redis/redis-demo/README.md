# redis-demo:Redis 常用业务场景代码示例

配套笔记 [../01-Redis实战-常见业务场景.md](../01-Redis实战-常见业务场景.md),演示 6 个通用场景。

## 环境要求

- JDK 1.8+
- Maven 3.6+
- 本机 Redis(默认 localhost:6379,无密码;有密码改 `application.yml`)

## 怎么跑

```bash
# 启动本机 Redis 后,执行:
mvn spring-boot:run
```

启动后会自动依次演示所有场景并打印结果(演示的是模拟数据,不会影响业务库)。

## 场景对照

| Service | 对应场景 | 关键点 |
|---------|---------|--------|
| TokenService | 登录 Token 存储 | String + 过期时间,可主动失效(踢人/退出) |
| CacheService | 热点缓存 | 缓存空值防穿透、随机过期防雪崩、更新库后删缓存 |
| StockService | 号源扣减防超卖 | incr/decr 原子操作 |
| RedisLock | 分布式锁工具 | SET NX EX + Lua 原子释放 |
| LikeService | 点赞防重复 | Set 去重 + 分布式锁 |
| SearchService | 搜索记录 + 热搜词 | List 最近 N 条、ZSet 排行榜 |

## 注意

- 用 `StringRedisTemplate` 保证"复制就能跑";生产环境一般自定义序列化(如 JSON),原理相同。
- 每个 Service 都是独立的,可以单独拿走去业务里用。
