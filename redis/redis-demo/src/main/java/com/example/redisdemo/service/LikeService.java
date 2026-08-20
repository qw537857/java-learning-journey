package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 场景4:点赞(Set 去重 + 分布式锁)
 *  - Set 存点赞用户,自动去重,isMember 判断是否点过赞 O(1)
 *  - 分布式锁保证同一用户同一文章的点赞请求不会并发重复处理
 */
@Service
public class LikeService {

    private static final String LIKE_KEY = "like:article:";

    private final StringRedisTemplate redis;
    private final RedisLock redisLock;

    public LikeService(StringRedisTemplate redis, RedisLock redisLock) {
        this.redis = redis;
        this.redisLock = redisLock;
    }

    /**
     * 点赞,返回是否点赞成功(已点过返回 false)
     */
    public boolean like(Long articleId, Long userId) {
        String lockKey = "lock:like:" + articleId + ":" + userId;
        String lockValue = UUID.randomUUID().toString();
        if (!redisLock.tryLock(lockKey, lockValue, 10)) {
            return false; // 没拿到锁,说明正在处理中
        }
        try {
            Boolean added = redis.opsForSet()
                    .add(LIKE_KEY + articleId, String.valueOf(userId));
            return Boolean.TRUE.equals(added);
        } finally {
            redisLock.unlock(lockKey, lockValue);
        }
    }

    /** 判断是否已点赞 */
    public boolean isLiked(Long articleId, Long userId) {
        return Boolean.TRUE.equals(redis.opsForSet()
                .isMember(LIKE_KEY + articleId, String.valueOf(userId)));
    }

    /** 点赞总数 */
    public long likeCount(Long articleId) {
        Long size = redis.opsForSet().size(LIKE_KEY + articleId);
        return size == null ? 0 : size;
    }
}
