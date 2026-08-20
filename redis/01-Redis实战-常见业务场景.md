# 01 Redis 实战:从常见业务场景出发

- **日期**:2026-08-20
- **一句话总结**:Redis 在业务系统里最常见的 6 个场景,逐个讲透"怎么用 + 为什么",每个场景都带代码。
- **配套代码**:完整可运行工程在 [redis-demo](./redis-demo/),`mvn spring-boot:run` 一键跑通全部场景。

---

## Redis 常见的业务场景(先有个全貌)

在微服务架构下,Redis 常出现在这些地方:

| 场景 | 典型位置 | 用的什么能力 |
|------|-----------|-------------|
| 登录 Token 存储 | 用户服务 + 网关 | String + 过期时间 |
| 热点数据缓存 | 首页/详情等读多写少接口 | String + 过期时间 |
| 库存/号源扣减防超卖 | 交易/预约服务 | incr/decr 原子操作 |
| 分布式锁(防重复、缓存重建) | 点赞/下单/扣减等并发场景 | SET NX EX + Lua |
| 搜索记录(最近 N 条) | 搜索服务 | List |
| 热搜词 TopN | 搜索服务 | ZSet 排序 |
| 点赞状态集合 | 社区/内容服务 | Set 去重 |

下面按"为什么 Redis 快"→"数据类型"→"每个场景"的顺序展开。

---

## 一、先搞懂:Redis 为什么快(面试必问第一题)

四个原因,缺一不可:

1. **纯内存操作**:数据都在内存里,读写是纳秒级;MySQL 要磁盘 IO,慢几个数量级。
2. **单线程模型**:没有线程切换开销、没有锁竞争。这就是为什么很多面试官会问"单线程怎么还这么快"——**单线程避免了并发问题,把精力全放在 IO 上**。
3. **IO 多路复用(epoll)**:一个线程同时监听成千上万个连接,哪个连接有数据就处理哪个,不是傻等。相当于一个服务员同时接待几十桌客人,谁招手就服务谁。
4. **高效的数据结构**:字符串、跳表、压缩列表等都是为对应场景专门设计的。

> 面试口径:先答"内存 + 单线程 + IO 多路复用 + 数据结构",再展开单线程为什么不是劣势。

---

## 二、数据类型:每个场景用哪种、为什么

| 类型 | 典型用途 | 核心命令 | 为什么选它 |
|------|-------------|---------|-----------|
| **String** | Token、库存计数、缓存 JSON | set/get/setex/incr/decr | 最基础,支持原子自增(incr 扣库存不怕超卖) |
| **Hash** | 用户/商品详情缓存 | hset/hget/hgetall | 存对象,改一个字段不用整体重写 |
| **List** | 搜索记录最近 N 条 | lpush/ltrim/lrange | 天然"列表",左进右出 |
| **Set** | 点赞用户集合 | sadd/sismember/scard | 自动去重,判断"是否点过赞"是 O(1) |
| **ZSet** | 热搜词/排行榜 | zincrby/zrevrange | 带分数(次数/热度),天然可排序 |

**底层数据结构**(知道有这个维度即可,面试被追问能接住):
- String 底层是 **SDS**(简单动态字符串),比 C 字符串多了长度字段,取长度 O(1),且二进制安全。
- ZSet 底层是**跳表**,为什么用跳表?因为要支持"范围查询"(取 Top N),红黑树范围查询麻烦,跳表实现简单且查询 O(logN)。
- Hash/List 小数据量用**压缩列表**,数据多了转成哈希表/双向链表。

> 面试口径:类型 + 场景 + 核心命令 + 底层一句话,就比只背"五种类型"的候选人深一档。

---

## 三、场景 1:登录 Token 存储(JWT + Redis)

**典型流程:**

```
用户登录 → 用户服务校验账号密码 → 生成 JWT(里面放 userId)
        → 同时把 Token 存进 Redis(设过期时间)
        → 返回给前端
前端请求带 Token → 网关全局过滤器 → 解析 JWT → 查 Redis 确认有效 → 放行
```

**关键问题:JWT 本身无状态,为什么还要存 Redis?**

因为 JWT 一旦签发,服务端就"管不了它"了——**没法让一个有效的 Token 失效**。存了 Redis 之后:

1. **退出登录**:删掉 Redis 里的 Token,立刻失效(不然退出登录后 Token 还能用)。
2. **踢人下线**:同一账号新设备登录,删掉旧 Token,实现互踢。
3. **封号**:直接删 Token,该用户所有会话失效。

网关校验时:JWT 验签通过 + Redis 查得到 → 放行;查不到 → 拦截。

**核心代码**(完整类见 redis-demo 的 `TokenService`):

```java
@Service
public class TokenService {
    private static final String TOKEN_KEY = "***";
    private final StringRedisTemplate redis;

    // 登录成功后调用:存 Token,带过期时间
    public void saveToken(String token, Long userId, long expireSeconds) {
        redis.opsForValue().set(TOKEN_KEY + token, String.valueOf(userId),
                expireSeconds, TimeUnit.SECONDS);
    }

    // 网关过滤器调用:校验 Token 是否有效(存在且未过期)
    public boolean checkToken(String token) {
        return Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY + token));
    }

    // 退出登录 / 踢人 / 封号:删除 Token,立即失效
    public void deleteToken(String token) {
        redis.delete(TOKEN_KEY + token);
    }
}
```

> 面试追问"JWT 的 Payload 能放密码吗":不能。Payload 只是 Base64 编码,不是加密,解码就能看到,只能放 userId 这种非敏感信息。

---

## 四、场景 2:热点数据缓存 + 缓存一致性

**典型用途:** 首页、文章详情这类读多写少的数据——直接查数据库扛不住,先查 Redis,没有就查库再回填。

**缓存一致性策略:更新数据库 + 删缓存**

```
写操作:先更新 MySQL → 删除 Redis 里的缓存
读操作:先查 Redis → 没有 → 查 MySQL → 回填 Redis(设过期时间)
```

为什么是"删缓存"而不是"更新缓存"?并发下直接更新缓存容易和数据库不一致(两个线程同时更新,后写库的先写缓存,就脏了);删掉让下次查询重建,简单可靠。

**兜底**:所有缓存都设过期时间,即使删缓存失败,过期后自动重建 → 最终一致。

**核心代码**(完整类见 redis-demo 的 `CacheService`):

```java
// 读:先查缓存,没有再查库回填;空结果也缓存(防穿透),过期时间加随机值(防雪崩)
public String getArticleDetail(Long articleId) {
    String key = "cache:article:" + articleId;
    String cached = redis.opsForValue().get(key);
    if (cached != null) {
        return cached.isEmpty() ? null : cached;
    }
    String fromDb = queryDb(articleId);          // 模拟查 MySQL
    if (fromDb == null) {
        redis.opsForValue().set(key, "", 60, TimeUnit.SECONDS);   // 缓存空值,短过期
        return null;
    }
    long expire = 300 + new Random().nextInt(60);                 // 随机值防雪崩
    redis.opsForValue().set(key, fromDb, expire, TimeUnit.SECONDS);
    return fromDb;
}

// 写:先更新数据库,再删缓存(下次查询重建)
public void updateArticle(Long articleId, String content) {
    updateDb(articleId, content);
    redis.delete("cache:article:" + articleId);
}
```

**缓存三兄弟(必背,面试高频):**

| 问题 | 现象 | 常见解法 |
|------|------|-------------|
| **穿透** | 查一个不存在的数据,缓存和库都没有,每次打到 DB | 缓存空值(短过期);布隆过滤器拦截 |
| **击穿** | 某个热点 key 过期的瞬间,大量请求同时打 DB | 互斥锁:只让一个线程重建缓存(用的就是第五节的分布式锁);热点数据逻辑过期 |
| **雪崩** | 大量 key 同一时间过期,或 Redis 挂了 | 过期时间加随机值;Redis 集群;本地缓存兜底 |

> 穿透是"没有的东西反复查",击穿是"一个热点挂了",雪崩是"一片同时挂"——这个区分要能脱口而出。

---

## 五、场景 3:分布式锁(防重复提交、防超卖)

**为什么不用 synchronized?**

synchronized 只能锁住**单个 JVM**。微服务多实例部署时,每个实例有自己独立的锁——请求打到实例 A 和实例 B,两个锁各锁各的,照样并发。必须用**所有实例共享的 Redis** 做锁。

**核心实现:**

```java
// 加锁:key 是业务标识,value 是 UUID,只有 key 不存在时才能设置成功(NX),同时设过期时间(EX)
Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent("lock:like:文章id:用户id", uuid, 10, TimeUnit.SECONDS);

if (Boolean.TRUE.equals(locked)) {
    try {
        // 执行业务:点赞、扣库存...
    } finally {
        // 释放锁:先判断 value 是不是自己的,再删除(用 Lua 保证两步原子)
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Arrays.asList("lock:like:文章id:用户id"), uuid);
    }
}
```

三个关键点(面试就考这个):

1. **NX + 过期时间**:`setIfAbsent` 保证同一时刻只有一个实例拿到锁;过期时间兜底,防止拿到锁的实例挂了导致死锁。
2. **value 用 UUID**:释放锁时校验"是不是我加的锁"。不然锁过期后别的线程拿到了锁,我这边 finally 一删,把别人的锁删了。
3. **Lua 脚本保证原子性**:"判断 value 是自己的 + 删除"必须一步完成,不能先 get 再 del(中间可能被其他线程插进来)。

**配套的点赞代码**(完整类见 redis-demo 的 `LikeService`,用 Set 去重 + 分布式锁):

```java
// 点赞:返回是否点赞成功(已点过返回 false)
public boolean like(Long articleId, Long userId) {
    String lockKey = "lock:like:" + articleId + ":" + userId;
    String lockValue = UUID.randomUUID().toString();
    if (!redisLock.tryLock(lockKey, lockValue, 10)) {
        return false;   // 没拿到锁,说明正在处理中
    }
    try {
        Boolean added = redis.opsForSet().add("like:article:" + articleId, String.valueOf(userId));
        return Boolean.TRUE.equals(added);
    } finally {
        redisLock.unlock(lockKey, lockValue);
    }
}

// 判断是否已点赞:Set 的 isMember,O(1)
public boolean isLiked(Long articleId, Long userId) {
    return Boolean.TRUE.equals(redis.opsForSet()
            .isMember("like:article:" + articleId, String.valueOf(userId)));
}
```

**库存/号源扣减**(完整类见 redis-demo 的 `StockService`,incr/decr 原子操作,不用锁也能防超卖):

```java
public long deduct(Long scheduleId) {
    Long remain = redis.opsForValue().decrement("stock:schedule:" + scheduleId);
    if (remain != null && remain < 0) {
        redis.opsForValue().increment("stock:schedule:" + scheduleId); // 回滚
        throw new RuntimeException("库存不足");
    }
    return remain == null ? 0 : remain;
}
```

**应用场景回顾:** ① 点赞防重复(同一用户同一内容只生效一次);② 库存/号源扣减防超卖;③ 缓存击穿时只允许一个线程重建缓存。

> 追问"锁过期了业务还没执行完怎么办":Redis 官方客户端 Redisson 有"看门狗"机制自动续期,这块我了解原理,属于加分项。

---

## 六、场景 4:搜索记录 + 热搜词

**核心代码**(完整类见 redis-demo 的 `SearchService`):

```java
// 用户搜索:记录历史(去重后放最前)+ 热搜次数 +1
public void search(Long userId, String keyword) {
    String historyKey = "search:history:" + userId;
    // 先移除同名记录,再放到最前,避免重复
    redis.opsForList().remove(historyKey, 0, keyword);
    redis.opsForList().leftPush(historyKey, keyword);
    // 只保留最近 10 条
    redis.opsForList().trim(historyKey, 0, 9);
    // 热搜:搜索次数 +1
    redis.opsForZSet().incrementScore("search:hot", keyword, 1);
}

// 最近搜索记录(最新的在前)
public List<String> history(Long userId) {
    return redis.opsForList().range("search:history:" + userId, 0, -1);
}

// 全站热搜 TopN(按搜索次数降序)
public Set<String> hotKeywords(int topN) {
    return redis.opsForZSet().reverseRange("search:hot", 0, topN - 1);
}
```

**为什么这样设计:**
- **搜索记录**用 List:左进(最新在最前)+ `trim` 截断只留最近 N 条,天然是"最近搜索",不用自己写删除逻辑。
- **热搜词**用 ZSet:score 存搜索次数,`zincrby` 原子加一,`zrevrange` 直接取排行榜——比 MySQL `order by count` 快得多,还不占数据库压力。

---

## 七、工程必知:持久化(数据不能断电就没了)

Redis 是内存数据库,**不持久化的话,一重启数据全丢**(Token 全失效、缓存全没了还能忍,但点赞数据、搜索记录丢了就出事了)。

| 方案 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **RDB** | 定时把内存数据**快照**存成文件 | 文件小、恢复快、适合备份 | 两次快照之间的数据可能丢 |
| **AOF** | 把**每条写命令**追加到日志文件 | 丢数据少(每秒刷盘最多丢 1 秒) | 文件大,恢复慢,有重写机制 |

**一般怎么配:** 通常 AOF 开启(保证少丢数据)+ RDB 做定期备份。Redis 重启时优先用 AOF 恢复(数据更全)。

> 面试口径:能说出两者区别 + 各自的丢数据情况 + 重启恢复优先级,就够了。

---

## 八、工程必知:过期删除 + 内存淘汰

**为什么有过期机制:** Token、缓存都必须设过期时间,不然 Redis 内存迟早被撑爆。

**过期 key 怎么被删(两种策略配合):**

1. **惰性删除**:每次访问 key 时,发现过期了就删。省资源,但过期了没被访问就一直占内存。
2. **定期删除**:每隔一段时间,随机抽一批 key 检查,过期的删掉。兜底。

**内存满了怎么办(淘汰策略,8 种):**
- **noeviction**:默认,满了直接报错不写入(生产要改掉!)
- **allkeys-lru**:所有 key 按 LRU(最近最少使用)淘汰 ← 常用
- **volatile-lru**:只淘汰设置了过期时间的 key,按 LRU
- **LFU 系列**:按"使用频率"淘汰,适合热点明显的场景

> 面试口径:能说出"惰性 + 定期"两种删除策略,能说出 LRU 是"最近最少使用"、生产配 allkeys-lru 或 volatile-lru,就达标。

---

## 九、面试官可能问的(速查)

1. **Redis 为什么快?** → 内存 + 单线程 + IO 多路复用 + 高效数据结构
2. **五种数据类型各用在什么场景?** → 对应场景表格(第二节)
3. **缓存穿透/击穿/雪崩?** → 定义区分 + 三种解法(第四节)
4. **缓存一致性怎么保证?** → 更新数据库+删缓存,过期兜底
5. **分布式锁怎么实现?** → SET NX EX + UUID + Lua(第五节,必背)
6. **JWT 为什么还要存 Redis?** → 无状态无法主动失效,存了才能踢人/退出
7. **Redis 持久化?** → RDB 快照 vs AOF 日志,重启优先 AOF
8. **内存淘汰策略?** → 惰性+定期删除;满了 allkeys-lru

---

## 十、收获 / 待办

- [ ] 把 redis-demo 跑起来(`mvn spring-boot:run`),逐个场景看输出,加深印象
- [ ] 下一篇:深入缓存三兄弟,写代码级方案(互斥锁重建缓存、布隆过滤器)
