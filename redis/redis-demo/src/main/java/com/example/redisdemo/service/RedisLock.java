package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工具
 * 为什么不用 synchronized:只能锁单个 JVM,微服务多实例下管不住跨实例并发。
 * 实现:SET key value NX EX
 *  - NX:key 不存在才能设置成功(保证同时只有一个实例拿到锁)
 *  - EX:过期时间兜底,防止拿到锁的实例挂了导致死锁
 *  - value 用 UUID:释放时校验是不是自己加的锁
 *  - 释放用 Lua 脚本:"判断 value + 删除"一步完成,保证原子性
 */
@Component
public class RedisLock {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private final StringRedisTemplate redis;

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 尝试加锁,成功返回 true */
    public boolean tryLock(String key, String value, long expireSeconds) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS));
    }

    /** 释放锁:只有 value 匹配(是自己的锁)才删除,全程原子 */
    public void unlock(String key, String value) {
        redis.execute(new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                Collections.singletonList(key), value);
    }
}
