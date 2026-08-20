package com.example.redisdemo.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 场景3:号源扣减防超卖(预约挂号)
 * 用 incr/decr 原子操作扣减,天然防并发超卖。
 */
@Service
public class StockService {

    private static final String STOCK_KEY = "stock:schedule:";

    private final StringRedisTemplate redis;

    public StockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 排班初始化号源总数 */
    public void initStock(Long scheduleId, int total) {
        redis.opsForValue().set(STOCK_KEY + scheduleId, String.valueOf(total));
    }

    /**
     * 扣减一个号源,返回剩余数量
     * decrement 是原子操作,高并发也不会超卖;
     * 剩余为负说明被抢完了,回滚并抛异常。
     */
    public long deduct(Long scheduleId) {
        Long remain = redis.opsForValue().decrement(STOCK_KEY + scheduleId);
        if (remain != null && remain < 0) {
            redis.opsForValue().increment(STOCK_KEY + scheduleId); // 回滚
            throw new RuntimeException("号源不足");
        }
        return remain == null ? 0 : remain;
    }
}
