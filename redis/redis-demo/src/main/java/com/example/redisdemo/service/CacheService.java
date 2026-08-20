package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 场景2:热点缓存(首页文章详情)
 * 读:先查 Redis,没有再查库回填;
 * 写:先更新数据库,再删缓存。
 */
@Service
public class CacheService {

    private static final String CACHE_KEY = "cache:article:";
    private static final Random RANDOM = new Random();

    private final StringRedisTemplate redis;

    public CacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 读缓存:没有则模拟查库回填;空结果也缓存(防穿透),过期时间加随机值(防雪崩) */
    public String getArticleDetail(Long articleId) {
        String key = CACHE_KEY + articleId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String fromDb = queryDb(articleId);   // 模拟查 MySQL
        if (fromDb == null) {
            // 缓存空值,短过期,防止"不存在的数据"反复打库
            redis.opsForValue().set(key, "", 60, TimeUnit.SECONDS);
            return null;
        }
        // 基础过期时间 + 随机值,避免大量 key 同时过期导致雪崩
        long expire = 300 + RANDOM.nextInt(60);
        redis.opsForValue().set(key, fromDb, expire, TimeUnit.SECONDS);
        return fromDb;
    }

    /** 更新数据库后删缓存(下次查询重建),保证一致性 */
    public void updateArticle(Long articleId, String content) {
        updateDb(articleId, content);         // 模拟更新 MySQL
        redis.delete(CACHE_KEY + articleId);
    }

    private String queryDb(Long articleId) {
        // 模拟:第一次查库返回数据,之后走缓存
        return "宠物科普文章-" + articleId + "-内容";
    }

    private void updateDb(Long articleId, String content) {
        // 模拟数据库更新
    }
}
