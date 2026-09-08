# AI 系列 · 01 认识 AI 与大模型部署 🤖

> AI 学习第一天(2026-09-08):先建立 AI 世界观,再动手把模型跑起来(云 API + 本地 Ollama),最后搞懂应用开发的四种技术架构。
> 原则:面向 Java 初级工程师,大白话 + 可照做步骤;只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 AI 时代与为什么选 SpringAI(AI 时间线 / Java 的机会 / 框架对比)](./01-AI时代与为什么选SpringAI.md)
- [x] [02 认识 AI 与大模型原理(AI 定义 / Transformer / LLM 持续生成)](./02-认识AI与大模型原理.md)
- [x] [03 大模型部署与 API 调用(云平台百炼 / Ollama 本地部署 / OpenAI 兼容接口 / 角色与会话记忆)](./03-大模型部署与API调用.md)
- [x] [04 大模型应用与技术架构(能力边界 / Hybrid AI / Prompt / Function Calling / RAG / Fine-tuning)](./04-大模型应用与技术架构.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-08 | AI 时代与 SpringAI 选型 | [01](./01-AI时代与为什么选SpringAI.md) |
| 2026-09-08 | 认识 AI 与 LLM 原理 | [02](./02-认识AI与大模型原理.md) |
| 2026-09-08 | 大模型部署与 API 调用 | [03](./03-大模型部署与API调用.md) |
| 2026-09-08 | 大模型应用与技术架构 | [04](./04-大模型应用与技术架构.md) |

## 一句话速记

1. ChatGPT(2022)让 AI 大众化,DeepSeek-R1(2025)低成本破局 → Java 程序员的机会;
2. LLM 本质 = Transformer + 注意力机制,**逐词预测**生成内容;
3. 模型三来源:开放 API / 云私有 / 本地私有;个人电脑别部署大模型;
4. 接口 OpenAI 兼容:POST + JSON,`system` 定人设,历史消息回传 = 记忆;
5. 架构成本:Prompt < Function Calling < RAG < Fine-tuning,够用就好。

## 下集预告

- [ ] [02 SpringAI 实战(明日)](../02-SpringAI实战/):用 Java(SpringAI)把大模型应用落地——快速入门、ChatClient、流式调用、RAG、MCP 等
