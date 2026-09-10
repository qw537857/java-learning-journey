# AI 系列 · 02 SpringAI 实战 🤖

> AI 学习第二天(2026-09-10):用 Java(SpringAI)把大模型应用落地——从第一次对话到 Prompt、Tool Calling、RAG、多模态。
> 原则:面向 Java 初级工程师,大白话 + 可照敲代码;只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 SpringAI 快速入门(工程/依赖/ChatClient/同步流式/System 人设)](./01-SpringAI快速入门.md)
- [x] [02 日志与会话记忆(Advisor/前端对接/ChatMemory/会话历史)](./02-日志与会话记忆.md)
- [x] [03 纯 Prompt 开发与提示词工程(六策/攻击防范/哄哄模拟器)](./03-纯Prompt开发与提示词工程.md)
- [x] [04 Tool Calling 智能客服(CRUD/`@Tool` 定义/System/ChatClient)](./04-ToolCalling智能客服.md)
- [x] [05 RAG 知识库 ChatPDF(向量/向量库/PDF 读取/上传下载/对话)](./05-RAG知识库ChatPDF.md)
- [x] [06 多模态与拓展(图片对话/记忆持久化/RedisVectorStore)](./06-多模态与拓展.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-10 | SpringAI 快速入门 | [01](./01-SpringAI快速入门.md) |
| 2026-09-10 | 日志与会话记忆 | [02](./02-日志与会话记忆.md) |
| 2026-09-10 | 纯 Prompt 与提示词工程 | [03](./03-纯Prompt开发与提示词工程.md) |
| 2026-09-10 | Tool Calling 智能客服 | [04](./04-ToolCalling智能客服.md) |
| 2026-09-10 | RAG 知识库 ChatPDF | [05](./05-RAG知识库ChatPDF.md) |
| 2026-09-10 | 多模态与拓展 | [06](./06-多模态与拓展.md) |

## 一句话速记

1. SpringAI 三步走:引 starter(BOM 管版本)→ 配模型信息 → `ChatClient.builder(model).build()`;
2. `call()` 同步、`stream()` 流式(Flux);`defaultSystem` 定人设、Advisor 加日志与记忆;
3. 会话记忆靠 `ChatMemory` + chatId;会话历史另记 chatId 列表,两者别混;
4. Tool Calling:`@Tool` 定义能力,`defaultTools()` 挂载,AI 自动决定何时查库/下单;
5. RAG:切片 → 向量化入库 → 检索 → 拼提示词;`QuestionAnswerAdvisor` 一句话接入;
6. 多模态用 `Media`;生产把会话记忆与向量库换 Redis 等持久化实现。
