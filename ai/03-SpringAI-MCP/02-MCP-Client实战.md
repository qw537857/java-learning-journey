# MCP Client 实战(2026-09-11)

> 来源:AI 课程导图(20 Spring AI MCP)整理,面向 Java 初级工程师。
> 目标:在 SpringAI 项目里接入 MCP,**用现成的 MCP 工具**给大模型增强能力(控制浏览器、查 IP 归属地、操作 Redis)。

---

## 一、需求:给大模型加两个能力

```
1. 打开网站并总结内容:提问"打开网站 https://example.com/,总结下这个网站的内容"
2. 查 IP 归属地:提问"查询 ip 所在地:114.114.114.114"
```

不做增强的话大模型做不到(它不能上网、没有 IP 库),下面用 MCP 实现。

---

## 二、准备:基础工程与环境

- 基础代码用课程提供的 `my-spring-ai-mcp` 工程,已实现基本对话;启动后测试:

```json
POST http://localhost:8100/chat/stream
{ "question": "你好", "sessionId": "123" }
```

- 使用模型:qwen3.7-max;环境变量需有 `ALIYUN_API_KEY`;
- 版本:`spring-ai` 1.0.9。

### ⚠️ Stdio 本地运行环境依赖

很多 MCP 服务是别人用 **Python/TypeScript** 写好的,本地要能跑:

| 依赖 | 说明 |
|------|------|
| `uvx` | Python 编写的 MCP 服务的运行环境 |
| `npx` | TypeScript 编写的 MCP 服务运行环境,需要 **Node > 20** |

> **先确认 cmd 里能执行 `npx` 命令**,不行就重装 Node。

---

## 三、实战 1:让 AI 控制浏览器

用社区现成的 **playwright MCP 服务**。

```bash
# 第一步:全局安装
npm install -g @executeautomation/playwright-mcp-server --registry=https://registry.npmmirror.com
```

```xml
<!-- 第二步:引入 MCP 客户端依赖 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
</dependency>
```

```json
// 第三步:mcp-servers.json(声明要接入的 MCP 服务)
{
  "mcpServers": {
    "playwright": {
      "command": "npx.cmd",
      "args": ["-y", "@executeautomation/playwright-mcp-server"]
    }
  }
}
```

```yaml
# 第四步:application.yml
server:
  port: 8100
spring:
  application:
    name: my-spring-ai-mcp-client
  ai:
    dashscope:
      api-key: ${ALIYUN_API_KEY}      # 密钥只放环境变量
    chat:
      options:
        model: qwen-plus
    mcp:
      client:
        enabled: true
        name: ${spring.application.name}
        version: 1.0.9
        request-timeout: 30s
        toolcallback:
          enabled: true               # 开启工具回调
        stdio:
          servers-configuration: classpath:mcp-servers.json
```

```java
// 第五步:把 MCP 工具注册到 ChatClient
@Configuration
public class SpringAIConfig {
    private static final String SYSTEM_PROMPT = "你是一个全能助手,可以帮我解决各种问题。";

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider provider) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(provider.getToolCallbacks())   // ★ MCP 工具
                .build();
    }
}
```

第六步:重启服务,提问"打开网站 … 总结内容",MCP 服务会**自动拉起浏览器**抓取页面信息。

### 原理分析

底层还是 **Tool Calling**:

```
配置了 mcpServers → 就"拥有"了一批工具 → 注册进 SpringAI
这些工具不是自己写的、也不是 Java 写的(是别人按 MCP 协议用 TypeScript 写的)
→ 遵循同一协议,所以能直接集成 → AI 自然就能"控制浏览器"了
```

---

## 四、实战 2:查 IP 归属地(高德地图 MCP)

### 4.1 先解决"从哪找 MCP 服务"

- 社区市场:https://mcp.so/zh/ (可搜索现成 MCP 服务)

### 4.2 安装并集成高德 MCP

```bash
npm install -g @amap/amap-maps-mcp-server --registry=https://registry.npmmirror.com
```

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx.cmd",
      "args": ["-y", "@executeautomation/playwright-mcp-server"]
    },
    "amap-maps": {
      "command": "npx.cmd",
      "args": ["-y", "@amap/amap-maps-mcp-server"],
      "env": { "AMAP_MAPS_API_KEY": "your-amap-key" }
    }
  }
}
```

> ⚠️ `AMAP_MAPS_API_KEY` 填自己在高德开放平台申请的值(https://lbs.amap.com/dev/key),
> 申请时【服务平台】要选 **Web 服务**;密钥不要提交到仓库。

重启后提问:"查询 ip 所在地:114.114.114.114" → AI 通过高德 MCP 返回归属地。

---

## 五、实战 3:让 AI 操作 Redis

```bash
npm install -g @modelcontextprotocol/server-redis --registry=https://registry.npmmirror.com
```

```json
{
  "mcpServers": {
    "redis": {
      "command": "npx.cmd",
      "args": ["-y", "@modelcontextprotocol/server-redis", "redis://localhost:6379"]
    }
  }
}
```

> ⚠️ 确保 Redis 正在运行;连接串里**不要写明文密码**,更不要用内网 IP+密码的写法。

测试:"保存数据到 redis 中,key:test_ai,value:123" → 去 Redis 里能查到;再测读取。

---

## 六、本节速记

1. 用 MCP 就像"装插件":装服务 → 写 `mcp-servers.json` → 配 yaml → 注册 ToolCallbackProvider;
2. **不需要自己写工具**,社区(Python/TS)写的 MCP 服务拿来即用;
3. 本地 stdio 要准备好 `npx`(Node>20)/`uvx`;
4. 原理仍是 Tool Calling,区别是工具"别人写、按协议给";
5. 密钥/连接串只放环境变量或本地配置,**绝不进仓库**。
