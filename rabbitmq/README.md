# RabbitMQ 学习笔记 🐇

> 从"为什么用 MQ"到"SpringBoot 集成"一条线讲透:核心概念、四种交换机、原生客户端、Confirm 确认、@RabbitListener。
> 原则:每个知识点都能讲出"是什么 + 为什么"。本仓库只收录通用知识,不含任何项目业务代码。

## 学习路线

- [x] [01 RabbitMQ 实战:从原生客户端到 SpringBoot 集成](./01-RabbitMQ实战-从原生到SpringBoot.md)
- [ ] 02 深入:死信队列(DLX)+ 延迟队列
- [ ] 03 深入:消费端手动 ACK + 幂等性
- [ ] 04 深入:集群与镜像队列

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-08-21 | RabbitMQ 实战:从原生客户端到 SpringBoot 集成 | [01](./01-RabbitMQ实战-从原生到SpringBoot.md) |

## 配套代码

笔记内已包含全部可运行代码(原生客户端 + SpringBoot 集成)。完整工程在本地 `本地练习目录` 下:

- `rabbitmq-send-demo` / `rabbitmq-receive-demo`:原生 Java 客户端(Direct/Fanout/Topic、Confirm、事务)
- `springboot-send-demo` / `springboot-receive-demo`:SpringBoot 发送/接收工程
