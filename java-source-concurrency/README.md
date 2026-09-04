# Java 集合源码与并发(面试题精讲 · 一)📝

> 面向初级工程师的面试题精讲第一期:集合源码 + 并发原理高频考点,把课程导图整理成能"照着背、照着讲"的笔记。
> 原则:先大白话讲透,再给源码级答案 + 面试速记表。本仓库只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 HashMap 源码剖析(put/get/扩容/equals-hashCode/手写哈希表)](./01-HashMap源码剖析.md)
- [x] [02 Synchronized 原理(三大问题/JMM/monitor/锁升级)](./02-Synchronized原理.md)
- [x] [03 Volatile 与 JMM(可见性/内存屏障/MESI/happens-before)](./03-Volatile与JMM.md)
- [x] [04 线程池源码剖析(7 参数/拒绝策略/状态机/ctl/Executors)](./04-线程池源码剖析.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-04 | 面试题精讲(一):HashMap 源码 + Synchronized 原理 + Volatile/JMM + 线程池 | [01](./01-HashMap源码剖析.md) [02](./02-Synchronized原理.md) [03](./03-Volatile与JMM.md) [04](./04-线程池源码剖析.md) |

## 一句话速记

1. HashMap:数组+链表+红黑树,put 先散列、冲突挂链、超 8 看数组长度决定扩容还是树化;
2. 锁的本质:每个对象关联一个 monitor(owner + 进入数),synchronized 走 monitorenter/monitorexit;
3. 锁升级:无锁 → 偏向锁 → 轻量级锁 → 重量级锁,锁信息在对象头 Mark Word;
4. volatile:保证可见性 + 禁重排,**不保证原子性**,实现靠内存屏障;
5. 线程池执行链路:先核心 → 再队列 → 后最大 → 最后拒绝策略。
