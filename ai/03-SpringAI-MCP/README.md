# AI 系列 · 03 SpringAI MCP 🔌

> AI 学习第三天(2026-09-11):MCP(Model Context Protocol)——让大模型以标准协议连接外部工具与数据。
> 原则:面向 Java 初级工程师,大白话 + 可照敲配置;只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 MCP 概述与原理(是什么/与 Tool Calling 的区别/通信机制)](./01-MCP概述与原理.md)
- [x] [02 MCP Client 实战(接社区工具:控制浏览器/IP 归属地/操作 Redis)](./02-MCP-Client实战.md)
- [x] [03 MCP Server 实战与在线服务(自建天气服务/在线 SSE/MCP 市场)](./03-MCP-Server实战与在线服务.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-11 | MCP 概述与原理 | [01](./01-MCP概述与原理.md) |
| 2026-09-11 | MCP Client 实战 | [02](./02-MCP-Client实战.md) |
| 2026-09-11 | MCP Server 实战与在线服务 | [03](./03-MCP-Server实战与在线服务.md) |

## 一句话速记

1. MCP 是 Anthropic 推出的**标准化协议**,统一 LLM 与外部工具/数据源的通信;
2. 相比 Tool Calling 的优势:**跨语言、跨项目复用**(一次写好,处处可用);
3. 两种通信:**Stdio 本地进程**、**SSE 远程网络**;
4. SpringAI 内置 Client/Server 支持:接现成工具只需 `mcp-servers.json` + yaml + ToolCallbackProvider;
5. 自建服务:starter + `type: ASYNC` + 把 `@Tool` 注册成 `List<ToolCallback>`,对外暴露 `/sse`。
