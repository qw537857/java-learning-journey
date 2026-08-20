package com.example.redisdemo;

import com.example.redisdemo.service.CacheService;
import com.example.redisdemo.service.LikeService;
import com.example.redisdemo.service.SearchService;
import com.example.redisdemo.service.StockService;
import com.example.redisdemo.service.TokenService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通用 Redis 场景演示入口
 * 启动后自动跑一遍所有场景并打印结果(mvn spring-boot:run)
 */
@SpringBootApplication
public class RedisDemoApplication implements CommandLineRunner {

    private final TokenService tokenService;
    private final CacheService cacheService;
    private final StockService stockService;
    private final LikeService likeService;
    private final SearchService searchService;

    public RedisDemoApplication(TokenService tokenService,
                                CacheService cacheService,
                                StockService stockService,
                                LikeService likeService,
                                SearchService searchService) {
        this.tokenService = tokenService;
        this.cacheService = cacheService;
        this.stockService = stockService;
        this.likeService = likeService;
        this.searchService = searchService;
    }

    public static void main(String[] args) {
        SpringApplication.run(RedisDemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("========== 场景1:登录 Token 存储 ==========");
        tokenService.saveToken("token-abc-123", 10001L, 60);
        System.out.println("登录成功,Token 已存 Redis,校验结果:" + tokenService.checkToken("token-abc-123"));
        tokenService.deleteToken("token-abc-123");
        System.out.println("退出登录删除 Token 后,校验结果:" + tokenService.checkToken("token-abc-123"));

        System.out.println("========== 场景2:热点缓存 ==========");
        System.out.println("首次查询(无缓存,模拟查库):" + cacheService.getArticleDetail(1L));
        System.out.println("二次查询(命中缓存):" + cacheService.getArticleDetail(1L));

        System.out.println("========== 场景3:号源扣减防超卖 ==========");
        stockService.initStock(888L, 3);
        for (int i = 1; i <= 4; i++) {
            try {
                System.out.println("第 " + i + " 次预约,剩余号源:" + stockService.deduct(888L));
            } catch (RuntimeException e) {
                System.out.println("第 " + i + " 次预约," + e.getMessage());
            }
        }

        System.out.println("========== 场景4+5:点赞(Set+分布式锁) ==========");
        System.out.println("用户1 点赞:" + likeService.like(66L, 1L));
        System.out.println("用户1 重复点赞:" + likeService.like(66L, 1L));
        System.out.println("用户2 点赞:" + likeService.like(66L, 2L));
        System.out.println("用户1 是否已点赞:" + likeService.isLiked(66L, 1L));
        System.out.println("文章66 点赞总数:" + likeService.likeCount(66L));

        System.out.println("========== 场景6:搜索记录 + 热搜词 ==========");
        searchService.search(10001L, "猫粮");
        searchService.search(10001L, "柯基");
        searchService.search(10001L, "猫粮");
        searchService.search(10002L, "猫粮");
        System.out.println("用户10001 最近搜索:" + searchService.history(10001L));
        System.out.println("全站热搜 Top5:" + searchService.hotKeywords(5));

        System.out.println("========== 演示结束,所有场景跑通 ✅ ==========");
    }
}
