# MCP Server 实战与在线服务(2026-09-11)

> 来源:AI 课程导图(20 Spring AI MCP)整理,面向 Java 初级工程师。
> 目标:自己写一个 MCP Server(天气查询)并让 Client 接入,再了解在线 MCP 服务与 MCP 市场。

---

## 一、自己写一个 MCP Server:天气查询服务

思路:把自己写的"查天气"能力按 MCP 标准暴露成服务,以后任何支持 MCP 的智能体都能直接接入。

### 1.1 创建工程 + 依赖

新建 `my-spring-ai-mcp-server`(JDK17),父工程坐标用通用占位(`com.example`):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
    </dependency>
</dependencies>
```

### 1.2 配置

```yaml
server:
  port: 8101
spring:
  application:
    name: my-spring-ai-mcp-server
  ai:
    mcp:
      server:
        enabled: true
        name: ${spring.application.name}
        version: 1.0.9
        type: ASYNC          # 服务类型:ASYNC 异步,也支持 SYNC
```

### 1.3 启动类(打印访问地址)

```java
@Slf4j
@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplicationBuilder(McpServerApplication.class).build(args);
        Environment env = app.run(args).getEnvironment();
        log.info("Application '{}' is running! Local: http://localhost:{}",
                env.getProperty("spring.application.name"), env.getProperty("server.port"));
    }
}
```

### 1.4 编写工具:天气 DTO + Service

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDTO {
    @JsonPropertyDescription("城市ID")
    private String cityId;
    @JsonPropertyDescription("城市名称")
    private String city;
    @JsonPropertyDescription("当前温度(单位:℃)")
    private String temperature;
    @JsonPropertyDescription("低温(单位:℃)")
    private String lowTemperature;
    @JsonPropertyDescription("高温(单位:℃)")
    private String highTemperature;
    @JsonPropertyDescription("数据日期(格式:YYYYMMDD)")
    private String date;
    @JsonPropertyDescription("空气质量指数")
    private String quality;
    @JsonPropertyDescription("PM2.5 浓度(单位:微克/立方米)")
    private double pm25;
}

@Service
public class WeatherService {

    @Tool(description = "根据城市id查询天气信息")
    public WeatherDTO getWeather(@ToolParam(description = "城市id") String cityId) {
        String url = "http://t.weather.itboy.net/api/weather/city/" + cityId;   // 公开天气接口示例
        String data = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(data);
        return WeatherDTO.builder()
                .cityId(json.getByPath("cityInfo.citykey", String.class))
                .city(json.getByPath("cityInfo.city", String.class))
                .date(json.getByPath("date", String.class))
                .temperature(json.getByPath("data.wendu", String.class))
                .lowTemperature(json.getByPath("data.forecast[0].low", String.class))
                .highTemperature(json.getByPath("data.forecast[0].high", String.class))
                .quality(json.getByPath("data.quality", String.class))
                .pm25(json.getByPath("data.pm25", Double.class))
                .build();
    }
}
```

> 常用城市 ID 示例:北京 101010100、上海 101020100、广州 101280101、成都 101270101、杭州 101210101。

### 1.5 把工具注册给 MCP Server

```java
@Configuration
public class McpConfig {
    /** 声明对外提供服务的工具 */
    @Bean
    public List<ToolCallback> tools(WeatherService weatherService) {
        return List.of(ToolCallbacks.from(weatherService));
    }
}
```

### 1.6 启动测试

浏览器访问 `http://localhost:8101/sse` → MCP Server 启动成功。

---

## 二、在 MCP Client 中接入自建服务

Client 侧只需在配置里**指定服务地址(SSE)**:

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: ${spring.application.name}
        version: 1.0.0
        request-timeout: 30s
        toolcallback:
          enabled: true
        type: ASYNC
        sse:
          connections:
            server1:
              url: http://localhost:8101     # ★ 自建 MCP Server 地址
```

启动后打断点可以看到天气工具已被发现,直接问"查询天气,城市id:101070101"即可。

---

## 三、在线 MCP 服务(主流趋势)

除了本地 stdio,还可以直接用**在线 MCP 服务**(SSE)。高德、百度都提供了在线 MCP Server。

### 3.1 以高德在线 MCP 为例

SSE 地址规则:`https://mcp.amap.com/sse?key=你在高德申请的key`

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${ALIYUN_API_KEY}
      chat:
        options:
          model: qwen-plus-latest
    mcp:
      client:
        enabled: true
        name: ${spring.application.name}
        version: 1.0.0
        toolcallback:
          enabled: true
        type: ASYNC
        # stdio:                              # 测试在线服务时先注释掉 stdio,避免干扰
        #   servers-configuration: classpath:mcp-servers.json
        sse:
          connections:
            server1:
              url: http://localhost:8101
```

**⚠️ 外部 SSE 服务要通过代码方式集成**(配置 url 可能不生效):

```java
@Configuration
public class McpConfig {
    /** 高德地图 MCP 服务(在线 SSE) */
    @Bean
    public List<NamedClientMcpTransport> amapMcpClientTransport() {
        McpClientTransport transport = HttpClientSseClientTransport
                .builder("https://mcp.amap.com")
                .sseEndpoint("/sse?key=your-amap-key")     // 换成自己的 key
                .build();
        return List.of(new NamedClientMcpTransport("amap", transport));
    }
}
```

测试:"驾车路线导航,从沈阳到西藏" → AI 调用高德服务返回路线。

---

## 四、MCP 服务市场(以后会越来越多)

| 平台 | 地址 |
|------|------|
| 阿里云 | https://bailian.console.aliyun.com/console?tab=mcp#/mcp-market |
| 百度 | https://sai.baidu.com/ai/mcp |
| 魔搭社区 | https://modelscope.cn/mcp |
| 社区导航 | https://mcp.so/zh/ |

> 意味着:以后要加能力,很多时候**不用写代码,装个 MCP 服务就行**。

---

## 五、本节速记

1. 自建 MCP Server = **starter + yaml(type: ASYNC)+ 把 `@Tool` 服务注册成 `List<ToolCallback>`**;
2. 外部访问:`http://host:port/sse`,Client 用 `sse.connections` 指过去即可;
3. 在线 MCP 服务(高德/百度)通过 **代码方式** 集成 SSE transport;
4. 服务市场是趋势:能力"插件化",按需安装;
5. 天气接口、城市 ID 都用公开/占位示例,不涉及私有数据。
