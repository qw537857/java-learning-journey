# RabbitMQ 学习笔记 🐇

> 从"为什么用 MQ"到"SpringBoot 集成"一条线讲透:核心概念、四种交换机、原生客户端、Confirm 确认、死信队列、延迟队列、手动 ACK、消息幂等。
> 原则:每个知识点都能讲出"是什么 + 为什么"。本仓库只收录通用知识,不含任何项目业务代码。

## 学习路线

- [x] [01 RabbitMQ 实战:从原生客户端到 SpringBoot 集成](./01-RabbitMQ实战-从原生到SpringBoot.md)
- [x] [01 补记:死信队列(DLX)+ 延迟队列(TTL 实现)](./01-RabbitMQ实战-从原生到SpringBoot.md)(已并入 01 第七、八节)
- [x] [01 补记:消费端手动 ACK + 消息幂等](./01-RabbitMQ实战-从原生到SpringBoot.md)(已并入 01 第九节)
- [ ] 02 深入:集群与镜像队列

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-08-21 | RabbitMQ 实战:从原生客户端到 SpringBoot 集成 | [01](./01-RabbitMQ实战-从原生到SpringBoot.md) |
| 2026-08-25 | 01 补记:死信队列 + 延迟队列 + 手动 ACK + 消息幂等 | [01](./01-RabbitMQ实战-从原生到SpringBoot.md) |

## 配套代码

笔记内已包含全部可运行代码(原生客户端 + SpringBoot 集成)。完整示例工程保留在本地练习目录下:

- 原生 Java 客户端工程(Direct/Fanout/Topic、Confirm、事务)
- SpringBoot 发送/接收工程
