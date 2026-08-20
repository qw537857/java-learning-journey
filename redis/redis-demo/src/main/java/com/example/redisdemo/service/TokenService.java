package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 场景1:登录 Token 存储
 * 登录成功后把 JWT 存 Redis 并设过期时间;
 * 网关校验时查 Redis,查不到就拦截 → 实现"能踢人、能退出"。
 */
@Service
public class TokenService {

    private static final String TOKEN_KEY = "login:token:";

    private final StringRedisTemplate redis;

    public TokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 登录成功后调用:存 Token,带过期时间 */
    public void saveToken(String token, Long userId, long expireSeconds) {
        redis.opsForValue().set(TOKEN_KEY + token, String.valueOf(userId), expireSeconds, TimeUnit.SECONDS);
    }

    /** 网关过滤器调用:校验 Token 是否有效(存在且未过期) */
    public boolean checkToken(String token) {
        return Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY + token));
    }

    /** 退出登录 / 踢人下线 / 封号:删除 Token,立即失效 */
    public void deleteToken(String token) {
        redis.delete(TOKEN_KEY + token);
    }
}
