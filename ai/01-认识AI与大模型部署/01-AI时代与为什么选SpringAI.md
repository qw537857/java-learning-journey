# AI 时代与为什么选 SpringAI(2026-09-08)

> 来源:AI 课程导图(01 AI 介绍)整理,面向 Java 初级工程师。
> 目标:先看清"AI 时代 Java 程序员的机会在哪",再搞懂"为什么大模型应用开发要用 SpringAI"。

---

## 一、AI 时代,Java 程序员的出路在哪里?

### 1.1 两个关键时间点

- **2022-11-30**:OpenAI 发布 GPT3.5,同时开放 ChatGPT——AI 突然进入普通人生活,各种 AI 应用雨后春笋般出现;
- **2025-01-20**:杭州 DeepSeek 发布 **DeepSeek-R1**,数学、代码、自然语言推理比肩 OpenAI o1,而**训练成本仅约 560 万美元**(美国巨头动辄数亿~数十亿美元)。

> 意义:大模型研发成本极高,中小企业原本望而却步;DeepSeek 用极低成本证明"大模型不是巨头专利",**让中小企业也有资格参与 AI 开发**,AI 应用化由此进入快车道。

### 1.2 Java 的机会

- 全球 **25 亿+ 个 Java 应用**在运行,**90%+ 的服务端应用**是 Java;
- 传统应用要 AI 化,自然希望用 Java 技术栈完成;
- 但 AI 开发长期是 Python 的强项(LangChain 等生态),**"既懂 Java、又懂 AI"的人才稀缺——这正是 Java 程序员最大的机会**。

---

## 二、为什么是 SpringAI?

### 2.1 三个候选框架

| 框架 | 语言 | 特点 |
|------|------|------|
| LangChain | Python | 大模型应用开发最常见的框架,但 Java 项目接起来别扭 |
| LangChain4j | Java | LangChain 的 Java 版,老项目可以考虑(最低 **JDK 8**) |
| **SpringAI** | Java | **Spring 官方出品**,充分利用 AOP、IoC 能力,和现有 Java 项目无缝融合 |

### 2.2 SpringAI 的硬性要求

```
JDK 版本 ≥ 17
SpringBoot 版本 ≥ 3.x
```

> 想用 SpringAI,先把老项目的 JDK 和 SpringBoot 升上来;升不动(还是 JDK8)就退而求其次用 LangChain4j。

### 2.3 学习主线

```
Chap01 认识 AI        → AI/LLM 到底是什么
Chap02 大模型应用开发 → 模型从哪来、怎么调 API、应用怎么做
Chap03 SpringAI       → 用 Java 写大模型应用(明天的内容)
```

官方文档:https://spring.io/projects/spring-ai(常用文档和链接里最有用的一份)

---

## 三、本节速记

1. ChatGPT(2022)让 AI 大众化,DeepSeek-R1(2025)让 AI **中小企业化**;
2. Java 存量巨大 + 懂 Java 又懂 AI 的人少 = Java 程序员的机会;
3. 用 Java 做大模型应用,首选 **SpringAI**(JDK17+ / Boot3.x);老项目用 LangChain4j(JDK8+)。
