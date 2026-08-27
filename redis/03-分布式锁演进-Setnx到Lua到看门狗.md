# 03 深入:分布式锁演进(setnx → Lua → 看门狗)

- **日期**:2026-08-27
- **一句话总结**:从最简单的 `SETNX` 一步步演进到生产级分布式锁——每条命令解决一个真实痛点,演进过程本身就是面试官最爱问的"由浅入深"。
- **配套代码**:可运行示例在 [redis-demo](./redis-demo/) 的 `RedisLock`。

---

## 1. 为什么要分布式锁

单机 JVM 里用 `synchronized` / `ReentrantLock` 就够了——锁是 JVM 内存里的一个标记,同一个 JVM 的线程都看得见。

但微服务是**多台机器**部署的:

```
用户请求 → 网关 → 服务A(server-a) ─┐
               服务B(server-b) ─┼→ 同时扣库存
               服务C(server-c) ─┘   (三个 JVM,各抢各的 synchronized)
```

三个 JVM 的 `synchronized` 互相看不见,100 个请求同时扣库存,库存 100 能扣成负数。
**需要一个所有机器都能访问的"公共锁"** → Redis(所有服务都连同一个 Redis,天然共享)。

> 面试口径:单机锁管"一个 JVM 内的线程",分布式锁管"多个 JVM 之间的进程"。核心诉求:**互斥(同一时刻只有一个进程能拿到锁)+ 可重入(可选)+ 防死锁(锁必须能释放)**。

---

## 2. 演进第一步:SETNX(能锁,但一堆坑)

### 2.1 最原始版本

```java
// 伪代码:setnx 成功返回 1,失败返回 0
if (redis.setnx("lock:stock", "1") == 1) {
    // 拿到锁,执行业务
    doBiz();
    redis.del("lock:stock");  // 释放锁
} else {
    // 没拿到锁,重试或返回
}
```

**坑 1:死锁**——业务执行到一半**抛异常/宕机**,`del` 没执行,锁永远在 → 后面所有请求都拿不到锁。

### 2.2 加过期时间(解决死锁)

```java
redis.setnx("lock:stock", "1");
redis.expire("lock:stock", 10);  // 10 秒后自动释放,防止死锁
```

**坑 2:不是原子操作**——`setnx` 成功后、`expire` 执行前,进程宕机,锁照样永远在。
(面试官经典问题:"setnx + expire 两个命令组合有什么问题?"答案:非原子,中间宕机照样死锁。)

### 2.3 一条命令搞定:SET NX EX(里程碑)

```java
// 原子操作:key 不存在才设置,同时设过期时间
// NX = Not eXists(不存在才设),EX = 过期时间(秒)
Boolean locked = redis.opsForValue()
        .setIfAbsent("lock:stock", "1", 10, TimeUnit.SECONDS);
```

这就是现在最常用的写法。**但还有坑**:

**坑 3:误删别人的锁**——线程 A 的锁 10 秒到期,但 A 业务还没跑完;线程 B 抢到锁开始跑;A 跑完了执行 `del`,把 B 的锁删了;线程 C 又抢到锁……锁形同虚设。

---

## 3. 演进第二步:Lua 脚本(解决误删)

### 3.1 思路:给锁加"身份",删之前先比对

```java
// 拿锁:value 存一个唯一标识(身份证)
String requestId = UUID.randomUUID().toString();
redis.opsForValue().setIfAbsent(lockKey, requestId, 10, TimeUnit.SECONDS);

// 释放锁:先比对 value 是不是自己的,是才删(必须原子,用 Lua)
String lua = "if redis.call('get', KEYS[1]) == ARGV[1] " +
             "then return redis.call('del', KEYS[1]) " +
             "else return 0 end";
redis.execute(new DefaultRedisScript<>(lua, Long.class),
        Collections.singletonList(lockKey), requestId);
```

**为什么必须用 Lua?** "比对 + 删除"是两个操作,分两步走中间可能被其他线程插队(比对完、删除前,锁刚好过期被 B 抢走,然后你把 B 的锁删了)。Lua 脚本在 Redis 里**原子执行**,中间不会被插队。

> 面试口径:释放锁必须"比对 value + del"一步完成,Redis 的 Lua 脚本天然原子,这是唯一正确姿势。**setnx 阶段是"拿锁"的演进,Lua 阶段是"释放锁"的演进,两个都要讲。**

### 3.2 完整版(当前主流写法)

```java
// RedisLock.java(redis-demo 里有完整可运行版)
@Component
public class RedisLock {

    private final StringRedisTemplate redis;
    private static final String LOCK_PREFIX = "lock:";

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 加锁:成功返回 true,失败返回 false(不阻塞) */
    public boolean tryLock(String key, String requestId, long expireSeconds) {
        return Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(LOCK_PREFIX + key, requestId, expireSeconds, TimeUnit.SECONDS));
    }

    /** 释放锁:比对身份 + 删除,原子操作 */
    public boolean unlock(String key, String requestId) {
        String lua = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                     "then return redis.call('del', KEYS[1]) " +
                     "else return 0 end";
        Long result = redis.execute(new DefaultRedisScript<>(lua, Long.class),
                Collections.singletonList(LOCK_PREFIX + key), requestId);
        return Long.valueOf(1L).equals(result);
    }
}
```

**使用姿势**:

```java
String requestId = UUID.randomUUID().toString();
boolean locked = lock.tryLock("stock:1001", requestId, 10);
if (!locked) {
    throw new RuntimeException("系统繁忙,请稍后重试");
}
try {
    doBiz();   // 业务:扣库存、下单...
} finally {
    lock.unlock("stock:1001", requestId);  // finally 里释放,异常也不漏
}
```

---

## 4. 演进第三步:看门狗(解决"锁提前过期")

### 4.1 还剩什么坑

锁过期时间设 10 秒,但业务跑了 30 秒(比如调第三方接口慢):
- 10 秒时锁自动释放 → 线程 B 抢到锁 → 两个线程同时跑业务 → **互斥失效**。

调大过期时间?业务耗时不可控,设 1 小时又怕宕机后锁占太久。

### 4.2 解法:看门狗(自动续期)

**思路**:拿到锁的同时,启动一个后台线程,每过一段时间(比如锁过期时间的 1/3)检查锁还在不在——**还在就自动续期**;业务跑多久,锁就续多久;业务结束释放锁时,把看门狗线程停掉。

**这就是 Redisson 的 `watchDog` 机制**:

```java
// Redisson:生产直接用这个,看门狗是内置的(默认锁 30 秒,每 10 秒自动续期)
RLock lock = redissonClient.getLock("lock:stock");
// tryLock(waitTime, leaseTime, unit):leaseTime 不传就启用看门狗自动续期
boolean locked = lock.tryLock(3, TimeUnit.SECONDS);
if (locked) {
    try {
        doBiz();  // 跑多久都行,看门狗一直续期
    } finally {
        lock.unlock();
    }
}
```

**看门狗原理(面试要能讲出来)**:

```
拿锁成功
   ↓
启动定时任务(每 10 秒一次,锁默认 30 秒)
   ↓
检查锁是否还是自己的(Redis 里查 value 比对)
   ↓
是 → 重置过期时间为 30 秒(续期)
不是 → 说明锁已释放/被抢,停掉定时任务
```

**注意**:只有 `leaseTime`(锁的租期)没手动指定时才启用看门狗;手动指定了,到期就释放,不续期。

> 面试口径:锁的过期时间设短了业务没跑完就失效,设长了宕机占锁太久;看门狗方案=锁自动续期,业务多久锁多久,业务结束立刻释放。Redisson 已内置,不用自己写。

---

## 5. 完整演进路线图(背这个,面试直接画出来)

| 版本 | 写法 | 解决什么 | 遗留问题 |
|------|------|---------|---------|
| v1 | `SETNX key 1` | 多 JVM 互斥 | 宕机死锁 |
| v2 | `SETNX` + `EXPIRE` | 死锁 | 两步非原子,中间宕机仍死锁 |
| v3 | `SET key val NX EX 10` | 原子加锁 + 过期 | 误删别人锁 |
| v4 | `SET NX EX` + Lua 比对删除 | 误删锁 | 业务超时锁提前失效 |
| v5 | Redisson 看门狗(自动续期) | 锁提前失效 | 极端场景需要 RedLock(多主节点) |

**最后一个问题:RedLock 是什么?**

单台 Redis 挂了,锁就没了(所有服务都能拿锁,互斥失效)。RedLock = 向 **5 个独立的 Redis 主节点**同时申请锁,超过半数(3 个)成功才算拿到。生产上单机 Redis + 哨兵/集群的可用性通常够用,RedLock 复杂且有争议,知道概念即可。

---

## 6. 使用场景(什么时候真的需要)

1. **防止超卖/重复扣减**:库存、余额、号源。
2. **防重复提交**:同一用户短时间重复下单/点赞。
3. **缓存重建**(上一篇 02 的击穿互斥锁就是它)。
4. **分布式定时任务**:多实例部署时保证同一个任务同一时刻只在一台机器跑。

```java
// 防重复提交示例(简单版)
@PostMapping("/order")
public String createOrder(@RequestParam Long userId, @RequestParam Long productId) {
    String key = "order:dup:" + userId + ":" + productId;
    String requestId = UUID.randomUUID().toString();
    // 同一用户同一商品,10 秒内只能下一次单
    if (!lock.tryLock(key, requestId, 10)) {
        return "操作太快,请稍后再试";
    }
    try {
        return orderService.create(userId, productId);
    } finally {
        lock.unlock(key, requestId);
    }
}
```

---

## 7. 踩坑提醒

1. **锁的粒度要小**:能锁 `stock:1001` 就别锁 `stock`(锁全表 = 全局串行,性能崩)。
2. **拿到锁必须 try/finally 释放**:finally 保证异常也释放,否则一次异常 = 死锁到过期。
3. **锁的过期时间 > 业务预估最坏耗时**:拿不准就上 Redisson 看门狗,别拍脑袋设 10 秒。
4. **分布式锁不是银弹**:读多写少的场景、能用乐观锁(版本号/条件更新)解决的,别上锁,锁是最后手段。
5. **Redis 锁 ≠ 数据库唯一索引**:防重复下单还可以用数据库唯一索引兜底,双保险最稳。

---

## 8. 面试高频追问

**Q: setnx 和 set nx ex 有什么区别?**
A: setnx 只做"不存在才设置",过期时间要单独 expire,两步非原子;set nx ex 一条命令同时完成"不存在才设置 + 设置过期时间",原子。现在都用后者。

**Q: 为什么删锁要用 Lua?**
A: "比对 value"和"删除"必须作为一个原子操作。如果先比对再删,中间锁可能过期被别的线程抢走,然后你删掉的是别人的锁。Lua 脚本在 Redis 服务端原子执行,杜绝插队。

**Q: 锁过期了业务还没执行完怎么办?**
A: 两个方向:① 锁的过期时间设得足够长(粗糙);② 看门狗自动续期(Redisson,推荐)。续期逻辑:定时任务检查锁还是自己的就延长过期时间,业务结束就停掉。

**Q: 主从切换时锁丢了怎么办?**
A: 单机锁在主从切换瞬间可能失效(主库还没同步锁就挂了)。严格方案是 RedLock(多主节点过半成功),但实现复杂、性能差,一般业务用 Redis 哨兵/集群的可用性就够了,真追求强一致用 ZooKeeper 锁。
