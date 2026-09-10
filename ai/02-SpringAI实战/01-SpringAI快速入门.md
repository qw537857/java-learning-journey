# SpringAI 快速入门:第一个对话机器人(2026-09-10)

> 来源:AI 课程导图(05 SpringAI)整理,面向 Java 初级工程师。
> 目标:搭好 SpringAI 工程,用 ChatClient 完成第一次大模型对话(同步 + 流式),并学会用 System 设定 AI 人设。

---

## 〇、前言:SpringAI 是什么

- SpringAI 整合了全球(主要是国外)大多数大模型,对**三种技术架构(Prompt / Tool Calling / RAG)**都有封装,开发很方便;
- 不同模型能接收的输入/输出类型不同,SpringAI 按类型把模型分类,**日常用的是对话模型(Chat Model)**——输出自然语言或代码;
- 目前支持约 19 种对话模型,**功能最完整的是 OpenAI 和 Ollama**,课程就以这两个平台为例。

---

## 一、工程准备

### 1.1 创建工程

SpringBoot 工程(Boot 3.4.3 + JDK 17),勾选 Web、MySQL 驱动即可。

### 1.2 引入依赖

SpringAI 适配了 SpringBoot 自动装配,**不同模型/平台有各自的 starter**:

| 平台 | starter artifactId |
|------|-------------------|
| Anthropic | `spring-ai-starter-model-anthropic` |
| Azure OpenAI | `spring-ai-starter-model-azure-openai` |
| DeepSeek | `spring-ai-starter-model-deepseek` |
| Hugging Face | `spring-ai-starter-model-huggingface` |
| Ollama | `spring-ai-starter-model-ollama` |
| OpenAI | `spring-ai-starter-model-openai` |

三步引入(以 Ollama 为例):

```xml
<!-- 1. 版本属性 -->
<properties>
    <java.version>17</java.version>
    <spring-ai.version>1.0.9</spring-ai.version>
</properties>

<!-- 2. 依赖管理(BOM 统一管版本) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 3. 具体模型的 starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>

<!-- Lombok 建议手动引入固定版本 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.22</version>
</dependency>
```

> ⚠️ 坑:**不要用 start.spring.io 生成的 lombok 依赖,有 bug**。

### 1.3 配置模型信息

```yaml
spring:
  application:
    name: ai-demo
  ai:
    ollama:
      base-url: http://localhost:11434   # ollama 服务地址(默认值)
      chat:
        model: qwen3:1.7b                # 模型名称
        options:
          temperature: 0.8               # 温度:越小越稳定,越大越随机
```

---

## 二、ChatClient:对话的核心 API

### 2.1 先声明 ChatClient Bean

```java
@Configuration
public class CommonConfiguration {

    // 参数 model 就是使用的模型:用 Ollama 就注入 OllamaChatModel
    @Bean
    public ChatClient chatClient(OllamaChatModel model) {
        return ChatClient.builder(model)   // builder 拿到工厂对象,可自由选模型/加配置
                .build();                  // 构建 ChatClient 实例
    }
}
```

> 引入哪个 starter,就能自动注入对应的 ChatModel(Ollama/OpenAI 用法一致)。

### 2.2 同步调用:等 AI 全部说完才返回

```java
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;

    @RequestMapping("/chat")
    public String chat(@RequestParam(defaultValue = "讲个笑话") String prompt) {
        return chatClient
                .prompt()      // 开启一次对话
                .user(prompt)  // user 提示词
                .call()        // 同步调用:等全部输出完才返回
                .content();    // 取响应文本
    }
}
```

访问:`http://localhost:8080/ai/chat?prompt=你好`

### 2.3 流式调用:像打字机一样逐字返回

同步调用要等很久页面才有反应,体验差 → 改成流式。**SpringAI 用 WebFlux 实现流式**:

```java
// 返回值变成 Flux<String>(流式结果)
// 必须设置 produces 响应类型和编码,否则前端乱码
@RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
public Flux<String> chat(@RequestParam(defaultValue = "讲个笑话") String prompt) {
    return chatClient
            .prompt(prompt)
            .stream()     // 流式调用
            .content();
}
```

### 2.4 System 设定:给 AI 换个"人设"

问 AI"你是谁",它回答自己是底层模型的设定。想改人设,就设置 **System 信息**。

SpringAI 里不用每次拼 Message,**建 ChatClient 时指定即可**:

```java
@Bean
public ChatClient chatClient(OllamaChatModel model) {
    return ChatClient.builder(model)
            .defaultSystem("你是一位友好、乐于助人的智能助手,名字叫小助手,请以热情的语气解答问题。")
            .build();
}
```

> 再问"你是谁",AI 就会按新设定回答了(System 的作用后面在 Tool Calling 里会更大)。

---

## 三、本节速记

1. SpringAI = 多模型整合 + 三种架构封装,日常用 **Chat Model**;OpenAI/Ollama 功能最全;
2. 依赖三步:**版本属性 → spring-ai-bom → 对应 starter**;lombok 手动引固定版本;
3. ChatClient:`builder(model).build()`,`call()` 同步、`stream()` 流式(Flux + 设置编码);
4. `defaultSystem(...)` 一句话定人设,不用手拼 Message。
