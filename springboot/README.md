# Spring Boot 学习笔记 🍃

> 面向初级工程师的 Spring Boot 面试专题笔记(2026-09-07):22 个高频问答,按"认知/配置/接口/数据/安全/异常AOP/消息批处理/监控"分组,大白话 + 新版写法修正。
> 原则:先讲是什么、为什么,再给关键配置;标注过时写法。本仓库只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [Spring Boot 面试专题笔记(22 问,初级视角)](./springboot.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-07 | Spring Boot 面试专题(什么是Boot/JavaConfig/DevTools/Profiles/Security/AOP/Actuator 等) | [springboot.md](./springboot.md) |

## 一句话速记

1. Spring Boot = starter 起步依赖 + 自动配置 + 内嵌容器,让 Spring 开箱即用;
2. 配置三件套:@Configuration/@Bean(JavaConfig)、application-{env}.yml(Profiles)、starter(管版本);
3. 安全新写法是 SecurityFilterChain Bean,`WebSecurityConfigurerAdapter` 已废弃;
4. 异常用 @RestControllerAdvice 兜底,日志/事务/权限是 AOP 三大场景;
5. 监控:单服务看 actuator 端点,集群用 Admin / Prometheus+Grafana 聚合。
