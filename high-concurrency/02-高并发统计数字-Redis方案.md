# 高并发统计数字:Redis 方案(浏览量 / 评论量 / 点赞 / 分享)

> 适用场景:游记、文章、视频的浏览量 +1、评论数联动、点赞、分享数。核心思想:**热点计数先写 Redis,定时再落 MySQL**。

## 1. 是什么:需求长什么样

详情页要展示一组"统计数字",并且每次操作要 +1:

- 浏览量:进详情页 +1
- 评论量:发一条评论 +1
- 点赞数:点一下 +1(还限制每人每天最多 5 次)
- 分享数:分享一次 +1

新手最容易写出的代码:

```java
// 错误示范:直接更新数据库
@Transactional
public void viewnumIncr(Long id) {
    // UPDATE t_note SET viewnum = viewnum + 1 WHERE id = ?
    noteMapper.increaseViewnum(id);
}
```

## 2. 为什么不能直接 UPDATE 数据库

1. **热点行锁**:浏览量高的数据,大家都在抢同一行的锁。`UPDATE ... SET viewnum = viewnum + 1` 是行级锁,并发 1 万个人看同一篇,这一行就是瓶颈,后面的请求全排队。
2. **MySQL 扛不住高频写**:一次浏览一次 UPDATE,数据库 WAL 日志、刷盘全被占满,主从延迟也跟着飙。
3. **浪费**:浏览量这种"只加不减"的数据,根本不需要立刻落库,晚 5 分钟落库没人看得出来。

**正确姿势:先放 Redis 内存里加,攒一批再批量落库。** 这就是"高并发统计数字"这道题的核心考点。

## 3. 整体方案(一张图记住)

```
进详情页/点赞/评论/分享
        │  (INCR 操作,微秒级)
        ▼
   Redis Hash(每个业务对象一条记录)
   key: note:statis:{id}
   field: viewnum / replynum / favornum / thumbsupnum / sharenum
        │
        │ 定时任务(如每 5 分钟)
        ▼
   批量刷回 MySQL(t_note 表对应字段)
```

三个要点:
1. **读**:展示统计数字时,优先读 Redis 实时值(没有就回 MySQL);
2. **写**:所有 +1 操作只动 Redis;
3. **落库**:定时任务把 Redis hash 整体刷回 MySQL,保证重启不丢太多。

## 4. 代码实现(Spring Boot + Redis)

### 4.1 计数接口(Controller)

```java
package com.example.statis.controller;

import com.example.statis.service.NoteStatisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/notes")
public class NoteController {

    @Autowired
    private NoteStatisService statisService;

    /** 浏览量 +1(进详情页时调用) */
    @PostMapping("/viewnumIncr")
    public Map<String, Object> viewnumIncr(Long sid) {
        return statisService.viewnumIncr(sid);
    }

    /** 评论量 +1(评论成功后调用) */
    @PostMapping("/replynumIncr")
    public Map<String, Object> replynumIncr(Long sid) {
        return statisService.replynumIncr(sid);
    }

    /** 点赞(顶)+1,每人每天限 5 次 */
    @PostMapping("/star/{sid}")
    public Map<String, Object> star(@PathVariable Long sid) {
        return statisService.thumbsup(sid);
    }

    /** 分享数 +1 */
    @PostMapping("/sharenumIncr")
    public Map<String, Object> sharenumIncr(Long sid) {
        return statisService.sharenumIncr(sid);
    }
}
```

### 4.2 计数核心逻辑(Service)

```java
package com.example.statis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class NoteStatisService {

    @Autowired
    private StringRedisTemplate redis;

    /** Redis hash 的 key:一条游记对应一个 hash */
    private String hashKey(Long id) {
        return "note:statis:" + id;
    }

    /** 浏览量 +1 */
    public Map<String, Object> viewnumIncr(Long id) {
        // hash 里 viewnum 字段 +1,返回 +1 后的值
        Long value = redis.opsForHash().increment(hashKey(id), "viewnum", 1);
        return Map.of("viewnum", value);
    }

    /** 评论量 +1 */
    public Map<String, Object> replynumIncr(Long id) {
        Long value = redis.opsForHash().increment(hashKey(id), "replynum", 1);
        return Map.of("replynum", value);
    }

    /** 分享数 +1 */
    public Map<String, Object> sharenumIncr(Long id) {
        Long value = redis.opsForHash().increment(hashKey(id), "sharenum", 1);
        return Map.of("sharenum", value);
    }

    /**
     * 点赞:每人每天限 5 次
     * 思路:用 Redis 记录"用户当天点赞次数",先校验再 +1
     */
    public Map<String, Object> thumbsup(Long id, Long userId) {
        String today = LocalDate.now().toString();          // 2026-08-26
        String limitKey = "note:thumbsup:limit:" + userId + ":" + today;

        // 1. 次数 +1,并设置当天过期(第一次时设置)
        Long count = redis.opsForValue().increment(limitKey);
        if (count == 1L) {
            // 到当天 24 点过期,避免 key 堆积
            redis.expire(limitKey, java.time.Duration.ofHours(24));
        }

        // 2. 超过 5 次直接拒绝(注意:业务上可先查再增,避免浪费)
        if (count > 5L) {
            // 超过限额:把刚才 +1 减回去,返回失败
            redis.opsForValue().decrement(limitKey);
            throw new RuntimeException("今天点赞次数已达上限(5次)");
        }

        // 3. 正常点赞,统计数 +1
        Long value = redis.opsForHash().increment(hashKey(id), "thumbsupnum", 1);
        return Map.of("thumbsupnum", value);
    }
}
```

> 说明:示例里为了好读先自增再判断。生产上更严谨的做法是 `INCR` 之前先 `GET` 判断,或用 Lua 脚本保证原子性(判断+自增一条命令),防止极端并发下超限。

### 4.3 启动时初始化:把 DB 初值灌进 Redis(关键!)

为什么必须有这一步?

定时任务持久化时,是"用 Redis hash 覆盖 MySQL"。如果 Redis hash 里只有 `viewnum` 一个字段、没有 `id` 和 `replynum` 等字段,一刷就会把 MySQL 里的其他字段刷成 null/0 —— **数据被覆盖丢了**。

所以服务启动时,要先把 MySQL 的初始值全部灌进 Redis hash:

```java
package com.example.statis.listener;

import com.example.statis.mapper.NoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用启动后,把 MySQL 里的统计初始值灌入 Redis hash
 * 防止定时持久化时把 MySQL 字段覆盖成 null
 */
@Component
public class StatisHashInitListener implements ApplicationRunner {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> list = noteMapper.selectStatisList(); // 查 id/viewnum/replynum/... 初始值
        for (Map<String, Object> row : list) {
            Map<String, String> hash = new HashMap<>();
            row.forEach((k, v) -> hash.put(k, String.valueOf(v)));
            String key = "note:statis:" + row.get("id");
            redis.opsForHash().putAll(key, hash);
        }
    }
}
```

### 4.4 定时持久化:Redis 刷回 MySQL

```java
package com.example.statis.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@EnableScheduling
public class StatisPersistenceJob {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private com.example.statis.mapper.NoteMapper noteMapper;

    /** 每 5 分钟把 Redis 里所有 note:statis:* 刷回 MySQL */
    @Scheduled(cron = "0 */5 * * * ?")
    public void persistence() {
        // 用 SCAN 遍历所有统计 key(生产禁止 KEYS *,会阻塞 Redis)
        Cursor<Map.Entry<Object, Object>> cursor = redis.opsForHash()
                .scan("note:statis:*", ScanOptions.scanOptions().match("note:statis:*").count(100).build());

        Map<Long, Map<String, Object>> batch = new HashMap<>();
        while (cursor.hasNext()) {
            Map.Entry<Object, Object> entry = cursor.next();
            // entry 结构:key=field, value=value
            // 示例简化:这里按 id 分组,组装成 UPDATE 语句批量执行
        }
        // noteMapper.batchUpdateStatis(batch);  // 批量 UPDATE,减少数据库压力
    }
}
```

> 实际项目里更常见的做法:先 `HGETALL` 每个 key 拿到完整 hash,再把 `id/viewnum/replynum/favornum/thumbsupnum/sharenum` 组装成一条 `UPDATE t_note SET ... WHERE id = ?`,攒一批(比如 100 条)批量执行。

### 4.5 详情页读取:Redis 实时值优先

```java
public Map<String, Object> detail(Long id) {
    // 1. 查 MySQL 基础数据(标题、内容、作者等)
    Note note = noteMapper.selectById(id);

    // 2. 统计数字:Redis hash 有值就用 Redis(实时),没有就用 MySQL 初值
    Map<Object, Object> statis = redis.opsForHash().entries(hashKey(id));
    note.setViewnum(statis.get("viewnum") == null ? note.getViewnum() : Long.valueOf(statis.get("viewnum").toString()));
    note.setReplynum(statis.get("replynum") == null ? note.getReplynum() : Long.valueOf(statis.get("replynum").toString()));
    // ... 其他字段同理
    return Map.of("note", note);
}
```

> 为什么"实时值优先"而不是"MySQL 值 + Redis 增量"?因为如果定时持久化把 Redis 刷回 MySQL 后,Redis 不清零,再叠加就会**重复累加**。用"Redis 有值就展示 Redis"最不容易出错。

## 5. 面试口径

**Q: 高并发的浏览量/点赞数怎么做?为什么不用数据库直接 +1?**
A: 直接 UPDATE 是行锁,热点数据并发高会排队,数据库扛不住高频写。方案:Redis hash 存统计字段,`HINCRBY` 原子 +1,定时任务(如每 5 分钟)批量刷回 MySQL。读的时候 Redis 实时值优先。

**Q: Redis 计数怎么保证不丢?**
A: ① 启动时监听器把 MySQL 初值灌入 Redis,防止覆盖;② 定时持久化,最多丢一个周期(几分钟)的数据,浏览量这种数据可接受;③ 刷回 MySQL 后 Redis 不清零,以 Redis 为准继续累加,避免重复。

**Q: 点赞限次(每人每天 5 次)怎么实现?**
A: Redis key:`thumbsup:limit:{userId}:{yyyy-MM-dd}`,INCR 后判断是否超 5,超了拒绝;第一次设置时加过期时间(当天 24 点),避免 key 堆积。要防并发超限就用 Lua 脚本保证"判断+自增"原子性。

**Q: 为什么用 Hash 不用 String?**
A: 一个业务对象有 viewnum/replynum/favornum/thumbsupnum 多个字段,Hash 一个 key 存一组字段,`HINCRBY` 分别加,取详情一次 `HGETALL` 全拿到,管理方便、key 数量少。

## 6. 踩坑提醒

1. **一定要初始化 Redis hash**:不初始化,持久化时会把 MySQL 字段覆盖成 null(本项目真实踩过,考试演示时数字会消失)。
2. **禁止用 `KEYS *` 遍历**:会阻塞 Redis,用 `SCAN` 游标。
3. **刷回 MySQL 后 Redis 别清零**:清零再叠加会重复累加;以 Redis 为准,启动初始化只做一次。
4. **持久化用批量 UPDATE**:一条条 UPDATE 还是会有压力,攒批执行。
5. **限次 key 必须设过期**:否则每个用户每天一个 key 永久堆积,内存爆炸。
