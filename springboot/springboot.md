# Spring Boot 面试专题笔记(初级视角 · 2026-09-07)

> 来源:Spring Boot 面试专题问答整理(原文为英文面试题翻译,已按初级程序员视角重写:先大白话讲清"是什么、为什么",再给关键配置;并修正了原文里的过时写法,标注了版本差异)。
> 目标:22 个高频问答按主题分组,能看懂、能讲得出、面试够用。

---

## 一、认知篇:Spring Boot 是什么

### Q1 什么是 Spring Boot?

**一句话**:Spring Boot 是建立在 Spring 框架之上的"快速启动器"——它帮你把繁琐的配置和样板代码全部干掉,让你**用最少的工作量、更健壮地使用 Spring**。

**展开讲**:传统启动一个 Spring 项目很痛苦:要加一堆 Maven 依赖并处理版本冲突、要配置应用服务器(Tomcat)、要写一堆 XML/Java 配置。Spring Boot 出现后:

- 依赖:**starter 起步依赖**帮你打包好"一组相关依赖",还管好版本;
- 服务器:**内嵌 Tomcat/Jetty**,不用单独装、不用部署 war;
- 配置:**自动配置 + 默认值**,零配置就能跑起来。

### Q2 Spring Boot 有哪些优点?

```
1. 减少开发、测试的时间和精力
2. 用 JavaConfig 代替 XML 配置
3. 避免大量 Maven 导入和版本冲突(starter 统一管版本)
4. 提供"约定优于配置"的开发方式(给你默认值,快速起步)
5. 不需要单独的 Web 服务器(内嵌 Tomcat)
6. 配置少:没有 web.xml;@Configuration 类 + @Bean 方法即可,还能 @Autowired 自动注入
7. 环境化配置:通过 -Dspring.profiles.active=dev 切换不同环境的配置文件
```

### Q3 什么是 JavaConfig?

**一句话**:用**纯 Java 类**来配置 Spring IoC 容器,替代 XML 配置。

```java
@Configuration          // 声明这是一个配置类
public class AppConfig {
    @Bean               // 方法返回值交给 Spring 容器管理
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

好处:

- **面向对象**:配置就是类,可以继承、可以重写 @Bean 方法;
- **减少/消除 XML**:不用在 XML 和 Java 之间来回切;
- **类型安全 + 重构友好**:按类型取 Bean,不需要字符串查找和强转。

### Q17 你用过哪些 starter Maven 依赖?

starter = "起步依赖",一个 starter 帮你拉进一组配套依赖并管好版本,减少依赖声明、避免版本冲突。常用示例:

```xml
<!-- Web 开发(SpringMVC + 内嵌 Tomcat + Jackson 等) -->
spring-boot-starter-web

<!-- 安全(Spring Security) -->
spring-boot-starter-security

<!-- 消息队列:ActiveMQ / RabbitMQ / Kafka 各有对应 starter -->
spring-boot-starter-activemq

<!-- 数据访问:JPA / JDBC / MyBatis 等 -->
spring-boot-starter-data-jpa
```

> 💡 初级提示:需要什么功能就找对应的 `spring-boot-starter-xxx`,几乎不用自己拼依赖版本。

---

## 二、开发提效与配置

### Q4 如何不用重启服务器就加载改动?

用 **DevTools(开发者工具)**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

- 改动代码后,内嵌 Tomcat **自动重启**,省去手动部署;
- 该模块**在生产环境会自动禁用**(打包发布不影响);
- 还能提供 H2 内存数据库控制台方便测试。

> 💡 初级提示:idea 里改完代码等它自动重启即可;终极体验是配合 JRebel 热部署(不重启只热替换),但 devtools 免费够用。

### Q7 怎么改端口?

```properties
# application.properties
server.port=8090
```

### Q8 什么是 YAML?和 properties 比好在哪?

- YAML(yml)是**人类可读的数据序列化语言**,常用于配置文件;
- 和 properties 相比:配置复杂、有层级关系时,**YAML 更结构化、更不容易看晕**。

```yaml
# application.yml(注意冒号后有空格)
server:
  port: 8090
spring:
  datasource:
    url: jdbc:mysql://your-host:3306/demo
    username: your-username
```

```properties
# 等价 properties(扁平,层级多了很难读)
server.port=8090
spring.datasource.url=jdbc:mysql://your-host:3306/demo
spring.datasource.username=your-username
```

### Q13 什么是 Spring Profiles?

**一句话**:按环境(dev/test/prod)加载不同的 Bean 或配置。

```properties
# 启动时指定环境(方式一:启动参数)
-Dspring.profiles.active=dev

# application.properties 里写(方式二)
spring.profiles.active=dev
```

配合多环境配置文件:

```
application.properties          # 公共配置
application-dev.properties      # 开发环境(优先加载,覆盖公共配置)
application-prod.properties     # 生产环境
```

> 典型用途:Swagger 只在 dev/QA 环境开启,生产关闭——用 `@Profile("dev")` 标注即可。

---

## 三、接口文档与页面模板

### Q12 什么是 Swagger?你用过吗?

**一句话**:Swagger 是 RESTful 接口的**可视化文档 + 在线调试工具**。

- 用 Swagger UI,前端同学能直接在网页上"沙箱"式调试接口;
- 文档和代码同步更新(基于注解生成),不用手工维护文档;
- 消除了"调用接口全靠猜"的问题。

Spring Boot 集成(springdoc 是当前主流,替代老 swagger2):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.x</version>
</dependency>
```

启动后访问 `http://localhost:8080/swagger-ui.html` 即可。

> 💡 初级提示:老项目常见 `springfox-swagger2`,新项目用 springdoc;面试答出"注解生成文档 + 在线调试 + 前后端协作提效"就够。

### Q15 什么是 FreeMarker 模板?

FreeMarker 是 Java 的**模板引擎**,用于 MVC 架构里动态生成页面。

- 核心价值:**表示层和业务层完全分离**——程序员写 Java,页面设计师写 html 模板,最后用数据渲染出完整页面;
- 类似的技术还有 Thymeleaf(Spring Boot 官方更推荐)、JSP。

---

## 四、数据访问

### Q11 如何用 Spring Boot 实现分页和排序?

用 Spring Data JPA:给 Repository 方法传 **Pageable**,返回 `Page<T>` 即可。

```java
// Repository:方法名 + Pageable 参数,Spring Data 自动实现
Page<User> findByAgeGreaterThan(int age, Pageable pageable);

// Service 里调用
Page<User> page = userRepository.findByAgeGreaterThan(18,
        PageRequest.of(0, 10, Sort.by("id").descending()));  // 第1页(0起),每页10条,id倒序
```

> 💡 初级提示:MyBatis 系项目则常用 PageHelper 分页插件,原理是拦截 SQL 拼 limit;面试重点答 JPA 这种"方法名自动生成 SQL + Pageable"的思想。

---

## 五、安全

### Q9 如何给 Spring Boot 应用加安全?

引入安全 starter + 写安全配置(登录、放行、权限规则都在这里配):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

⚠️ **原文写法已过时**:老教程让你继承 `WebSecurityConfigurerAdapter` 并重写方法——该类在 Spring Security 5.7+ **已废弃**。现在的标准写法是声明 `SecurityFilterChain` Bean:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/public/**").permitAll()   // 放行
                .anyRequest().authenticated())                    // 其余要登录
            .formLogin(withDefaults());
        return http.build();
    }
}
```

> 💡 初级提示:面试见到 `WebSecurityConfigurerAdapter` 的写法,要能指出"新版本用 SecurityFilterChain Bean",这是加分项。

### Q18 什么是 CSRF 攻击?

- CSRF = **跨站请求伪造**(Cross-Site Request Forgery);
- 攻击方式:用户已登录你的网站,攻击者诱导用户在**不知情**下,向你的网站发出"改状态"的请求(如转账、改密码);
- 特点:**专门针对状态改变请求**(POST/PUT/DELETE),不是偷数据——因为攻击者看不到响应;
- 防御:CSRF Token(表单/请求头带随机 token,服务端校验)、SameSite Cookie、校验 Referer 等。
- Spring Security 默认开启 CSRF 防护;前后端分离用 token 认证时通常关闭。

> 💡 初级记忆:**CSRF = 借用户之手发请求;XSS = 往你页面里塞脚本偷数据**,别搞混。

---

## 六、异常处理与 AOP

### Q16 如何做统一异常处理?

用 `@ControllerAdvice` + `@ExceptionHandler` 集中处理 Controller 抛出的异常:

```java
@RestControllerAdvice          // 对全局 Controller 生效(rest 版 @ControllerAdvice)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.error(500, "系统繁忙,请稍后再试");
    }
}
```

好处:业务代码里只管 `throw new BusinessException(...)`,不用到处 try-catch 包一层。

### Q20 什么是 AOP?

- 软件开发中,有些功能会**横跨多个模块**(日志、事务、权限校验、性能统计),这叫**横切关注点**;
- AOP(面向切面编程)= 把这些横切逻辑**从业务代码里抽出来**,统一织入,和业务解耦;
- 典型实现:@Aspect + @Around/@Before/@After,Spring 默认用动态代理(AOP)。

```java
@Aspect
@Component
public class LogAspect {
    @Around("@annotation(operLog)")   // 切到打了 @OperLog 注解的方法
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();   // 执行原方法
        System.out.println("耗时:" + (System.currentTimeMillis() - start) + "ms");
        return result;
    }
}
```

> 💡 初级记忆:OOP 是纵向(按模块)组织代码,AOP 是横向(按关注点)切一刀——日志、事务、权限是 AOP 的三大经典场景。

---

## 七、消息与批处理

### Q10 如何集成 Spring Boot 和 ActiveMQ?

引入消息队列的 starter,配置连接信息,然后用 `JmsTemplate` 收发消息:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-activemq</artifactId>
</dependency>
```

```properties
spring.activemq.broker-url=tcp://your-host:61616
spring.activemq.user=your-username
spring.activemq.password=your-password
```

> 思路通用:RabbitMQ 用 `spring-boot-starter-amqp`,Kafka 用 `spring-boot-starter-kafka`,都是"starter + 配置 + 模板类"三步走。

### Q21 什么是 Apache Kafka?

- Kafka 是**分布式发布-订阅消息系统**(Apache 顶级项目),可扩展、容错;
- 适合**离线批量消费和在线实时消费**两种场景;
- 在微服务里常做:削峰填谷、异步解耦、日志收集、事件驱动。

> 💡 初级提示:面试对比 MQ 时记住一句话——ActiveMQ/RabbitMQ 偏"消息队列",Kafka 偏"分布式流平台",吞吐更高、能重放。

### Q14 什么是 Spring Batch?

Spring Batch 是**批处理框架**,处理大量记录时的可重用功能都内置了:

- 日志/跟踪、事务管理、作业统计、**作业重启、跳过(出错跳过)、资源管理**;
- 通过**优化和分区**技术,支撑大批量、高性能批处理作业(如每天凌晨跑报表、对账)。

---

## 八、监控

### Q5 什么是 Spring Boot 监视器(Actuator)?

- Actuator 是 Spring Boot 的重要功能,提供**生产环境运行状态监控**;
- 它暴露一组 **REST 端点**,可直接用 HTTP 访问检查状态:

```
/actuator/health     # 健康检查(是否活着、数据库等组件是否正常)
/actuator/info       # 自定义信息
/actuator/metrics    # 指标(内存、线程等)
/actuator/beans      # 容器里所有 Bean
```

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Q6 如何禁用 Actuator 端点的安全性?(老版本问题,需会辨别)

- 老版本:敏感端点默认只有 `ACTUATOR` 角色能访问,可用 `management.security.enabled=false` 关掉(原文答案)——**该配置在新版本已移除**;
- 新版本:Actuator 端点安全统一交给 Spring Security 管,放行写法:

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health").permitAll()   // 健康检查通常放行
        .requestMatchers("/actuator/**").hasRole("ADMIN")  // 其余端点限权限
        .anyRequest().authenticated());
```

> ⚠️ 安全提醒:关闭端点安全**只建议在端点仅内网可访问时**做;`/actuator/**` 裸奔公网等于把应用内部结构送给攻击者。

### Q22 如何监控所有 Spring Boot 微服务?

- Actuator 能监控**单个**微服务,但几十上百个服务时要一个个打开端点,不现实;
- 解法:**聚合监控**——用 Spring Boot Admin 或 Prometheus + Grafana 把各服务 /actuator 端点数据汇总到一个面板统一展示、告警。

> 💡 初级提示:这题答出"单体用 actuator,集群用聚合监控(Admin/Prometheus+Grafana)"就有深度了。

---

## 九、今日速记表

| 主题 | 一句话 |
|------|--------|
| Spring Boot | 干掉样板配置的内嵌容器全家桶,starter + 自动配置 + 默认值 |
| JavaConfig | @Configuration + @Bean 用 Java 配容器,替代 XML,类型安全 |
| DevTools | 开发期改动自动重启,生产自动禁用 |
| Profiles | application-{env}.yml + spring.profiles.active 按环境切换 |
| Actuator | /actuator/health 等 REST 端点暴露运行状态 |
| 安全 | starter-security + SecurityFilterChain(**别再用 WebSecurityConfigurerAdapter**) |
| CSRF | 诱导已登录用户发"改状态"请求;靠 token/SameSite 防 |
| AOP | 把日志/事务/权限等横切逻辑抽出来统一织入 |
| 分页 | JPA:Repository 方法传 Pageable 返回 Page |
| 异常 | @RestControllerAdvice + @ExceptionHandler 全局兜底 |
| MQ | starter-activemq/amqp/kafka + 配置 + 模板类 |
| 批处理 | Spring Batch:大数据量离线处理,支持重启/跳过/分区 |
| 监控集群 | 单服务 actuator,集群用 Admin/Prometheus+Grafana 聚合 |

> 复习建议:先遮住答案自己讲一遍 Q1/Q2/Q9/Q16/Q18/Q20 这几道"面试主菜",再补冷门的 Batch/FreeMarker。
