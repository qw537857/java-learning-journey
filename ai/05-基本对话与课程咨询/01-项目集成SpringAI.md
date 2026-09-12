# 项目集成 SpringAI:AI 微服务接入(2026-09-12)

> 来源:AI 课程导图(10 基本对话与课程咨询)整理,面向 Java 初级工程师。
> 目标:在一个既有微服务项目里**新增 AI 能力**——建 AI 微服务、引依赖、配配置、接网关,并跑通登录测试。
> 说明:项目背景已做通用化处理(域名/IP/账号均用占位符)。

---

## 一、整体目标(今天要完成的清单)

```
1. 部署一份示例环境(虚拟机里跑着整套微服务)
2. 在原有项目中集成 SpringAI(新增 aigc 微服务)
3. 新建对话功能
4. 流式对话功能
5. 停止生成功能
6. 基于 Redis 的会话记忆
7. 修一个 bug:停止生成后历史记录不保存
```

---

## 二、环境准备(了解套路即可)

示例项目是**微服务架构**,本地通过"域名 + 虚拟机"模拟真实环境:

- 改本地 **hosts**,把一串域名指向虚拟机 IP(如 `your-server-ip api.your-domain.com`);
- 虚拟机里的 **Nginx** 按域名反向代理到各组件:

| 组件 | 域名示例 | 端口 |
|------|----------|------|
| Git 私服 | git.your-domain.com | 10880 |
| Jenkins | jenkins.your-domain.com | 18080 |
| RabbitMQ 控制台 | mq.your-domain.com | 15672 |
| Nacos 控制台 | nacos.your-domain.com | 8848 |
| xxl-job 控制台 | xxljob.your-domain.com | 8880 |
| ES Kibana | es.your-domain.com | 5601 |
| 微服务网关 | api.your-domain.com | 10010 |
| 用户端入口 | www.your-domain.com | 18081 |
| 管理端入口 | manage.your-domain.com | 18082 |

> ⚠️ 表格里的账号密码属于示例环境配置,**不写进笔记**;真实项目中这类信息走配置中心/密钥管理。

**微服务一览**(知道有这些服务即可):父工程 + 通用模块,以及 **消息、网关、权限、用户、支付、课程、考试、搜索、交易、学习、促销、媒资、数据、评价** 等业务服务。

**运行代码**:JDK 17 → 刷新 Maven → 全选启动,去 Nacos 看服务是否注册成功;用户端/管理端能登录即环境 OK。

---

## 三、新增 AI 微服务(aigc)

### 3.1 为什么单独建微服务

- 项目是微服务架构,加 AI 能力就**新增一个 AI 服务**(AIGC = AI Generated Content,人工智能生成内容);
- 独立服务的好处:依赖隔离、独立部署、不污染老业务。

### 3.2 核心依赖(pom)

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring AI BOM:统一管 Spring AI 版本 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- 阿里云 AI BOM:提供 DashScope(百炼)集成 -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-bom</artifactId>
            <version>1.0.0.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- web / redis / nacos 注册与配置 / 负载均衡 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <!-- 百炼(DashScope)AI 集成 -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    </dependency>
    <!-- MyBatis-Plus + MySQL + 接口文档 -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

> 记住这套组合:**SpringAI BOM + 平台 starter(dashscope)+ 项目基础设施(web/redis/nacos/mybatis)**。

### 3.3 配置文件分层

每个微服务 4 个配置:

| 文件 | 作用 |
|------|------|
| `application.yml` | 主配置:端口、服务名、Swagger 等 |
| `application-local.yml` | 本地环境:Nacos 地址、日志级别 |
| `application-dev.yml` | 开发环境 |
| `application-test.yml` | 测试环境 |

```yaml
# application.yml(节选)
server:
  port: 8094
spring:
  profiles:
    active: local
  application:
    name: aigc-service
```

```yaml
# application-local.yml(节选)
spring:
  cloud:
    nacos:
      server-addr: your-nacos:8848
      discovery:
        group: DEFAULT_GROUP
      config:
        import:                      # 从 Nacos 导入共享配置
          - nacos:${spring.application.name}.yaml
          - nacos:shared-spring.yaml
          - nacos:shared-redis.yaml
          - nacos:shared-logs.yaml
          - nacos:shared-mybatis.yaml
```

```yaml
# Nacos 中本服务的配置 aigc-service.yaml(AI 相关)
spring:
  ai:
    dashscope:
      api-key: ${ALIYUN_API_KEY}     # 只从环境变量读,不写死
      chat:
        enabled: true
        options:
          model: qwen-max-latest
      embedding:
        enabled: true
        options:
          model: text-embedding-v4   # 向量模型
          dimensions: 1024
```

> 💡 服务名 = 配置文件名(Nacos 按 `服务名.yaml` 拉配置),这是"配置中心按服务隔离"的常见做法。

### 3.4 接网关

所有请求都要过网关,给 AI 服务加一条路由:

```yaml
# gateway 配置(节选)
- id: aigc
  uri: lb://aigc-service          # 负载均衡到服务名
  predicates:
    - Path=/ais/**                # 以 /ais/ 开头的请求转发给 AI 服务
```

> 改完**记得重启网关**。约定好路径前缀(如 `/ais/**`)后,前端就统一走网关访问。

### 3.5 用接口工具调试

- 把接口文档导入接口调试工具(如 Apifox),配置**全局地址 = 网关地址**;
- 加**全局请求头 token**(Authorization):登录用户端后,从浏览器开发者工具里拿 token 填进去;
- 这样每个接口都会自动带上 token,避免 401。

---

## 四、本节速记

1. 给老项目加 AI = **新增一个 AI 微服务**,依赖隔离、独立部署;
2. 依赖三件套:**SpringAI BOM + 平台 starter(百炼/OpenAI/Ollama)+ 项目原有基础设施**;
3. 配置:**application.yml 只管本机**,环境与共享配置放 **Nacos**,密钥走**环境变量**;
4. 网关加一条 `Path=/ais/**` 的路由,外部统一从网关进;
5. 调试:接口工具里配**全局地址 + 全局 token**,效率翻倍。
