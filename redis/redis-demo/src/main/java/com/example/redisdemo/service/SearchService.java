package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 场景5:搜索记录 + 热搜词
 *  - 搜索记录:List,左进 + trim 只保留最近 N 条
 *  - 热搜词:ZSet,score = 搜索次数,zincrby 原子 +1,reverseRange 取 TopN
 */
@Service
public class SearchService {

    private static final String HISTORY_KEY = "search:history:";
    private static final String HOT_KEY = "search:hot";
    private static final int MAX_HISTORY = 10;

    private final StringRedisTemplate redis;

    public SearchService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 用户搜索:记录历史(去重后放最前)+ 热搜次数 +1 */
    public void search(Long userId, String keyword) {
        String historyKey = HISTORY_KEY + userId;
        // 先移除同名记录,再放到最前,避免重复
        redis.opsForList().remove(historyKey, 0, keyword);
        redis.opsForList().leftPush(historyKey, keyword);
        // 只保留最近 10 条
        redis.opsForList().trim(historyKey, 0, MAX_HISTORY - 1);
        // 热搜:搜索次数 +1
        redis.opsForZSet().incrementScore(HOT_KEY, keyword, 1);
    }

    /** 最近搜索记录(最新的在前) */
    public List<String> history(Long userId) {
        return redis.opsForList().range(HISTORY_KEY + userId, 0, -1);
    }

    /** 全站热搜 TopN(按搜索次数降序) */
    public Set<String> hotKeywords(int topN) {
        return redis.opsForZSet().reverseRange(HOT_KEY, 0, topN - 1);
    }
}
