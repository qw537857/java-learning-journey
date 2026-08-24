# Canal 学习笔记 🚢

> 从"为什么需要监听数据库变化"到"SpringBoot 集成"一条线讲透:binlog 原理、伪装从库、环境准备、EntryHandler 回调、真实踩坑。
> 原则:每个知识点都能讲出"是什么 + 为什么"。本仓库只收录通用知识,不含任何项目业务代码。

## 学习路线

- [x] [01 Canal 实战:MySQL binlog 监听入门](./01-Canal实战-MySQL-binlog监听入门.md)
- [ ] 02 深入:Canal + MQ 架构(监听 binlog → 广播给多系统)
- [ ] 03 深入:position 位点与断点续传
- [ ] 04 深入:Canal 高可用(多实例 + ZooKeeper)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-08-24 | Canal 实战:MySQL binlog 监听入门 | [01](./01-Canal实战-MySQL-binlog监听入门.md) |

## 配套代码

笔记内已包含全部可运行代码(启动类 + 实体 + 监听器)。完整示例工程保留在本地练习目录下:

- SpringBoot 工程(`com.example.canal`):@CanalTable + EntryHandler 监听 t_user 表增删改
