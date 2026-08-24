# 01 Canal 实战:MySQL binlog 监听入门

- **日期**:2026-08-24
- **一句话总结**:Canal 把自己伪装成 MySQL 的从库,读取 binlog,于是"数据库里增删改了一条数据 → 程序立刻知道"这件事不用再写轮询。本篇把原理、环境准备、SpringBoot 集成一次讲透,全部带可运行代码。
- **配套代码**:SpringBoot 工程(包名 `com.example.canal`),见文末完整代码。

---

## 一、先搞清楚:Canal 是什么,为什么用它

**是什么**:阿里巴巴开源的 **MySQL binlog 增量订阅 & 消费组件**。简单说:监听你的数据库,数据一变它就告诉你。

**大白话**:你往表里 INSERT / UPDATE / DELETE 一条数据,Canal 秒级感知,然后回调你的 Java 代码。你的程序不用再"定时去数据库里查有没有新数据"。

**为什么用**(常见场景):

| 场景 | 大白话 | 典型例子 |
|------|--------|---------|
| **缓存同步** | 数据库改了,自动更新/删除 Redis 缓存 | 商品改价 → 缓存自动失效,不用手动清 |
| **数据异构** | 一份数据放多个地方,保持同步 | MySQL → ElasticSearch 搜索库 |
| **数据同步** | 主库变更实时搬到从库/数仓/其他系统 | 订单表 → 统计系统 |
| **事件驱动** | 数据库变更作为"事件"触发下游业务 | 用户注册 → 自动发欢迎短信 |

**不用 Canal 的土办法有什么问题**:
- **定时任务轮询**:每 5 秒查一次"有没有新数据",延迟高、浪费数据库资源、还可能漏数据
- **业务代码里双写**:写数据库的同时手动写缓存/ES,侵入业务代码,漏一处就数据不一致

**和 MQ 什么关系**(面试容易混):Canal 管的是"**感知数据库变化**",MQ 管的是"**把消息广播给一堆消费者**"。俩经常配合用:Canal 监听到 binlog → 扔进 MQ → 下游各系统消费。

---

## 二、核心原理:MySQL 主从复制 + binlog(重点)

### 2.1 先懂 MySQL 主从复制

MySQL 自带主从复制:主库(Master)把每一次数据变更写进 **binlog**,从库(Slave)拉取 binlog 并重放,数据就同步过去了。

```
Master(写 binlog) ──► Slave(拉取 binlog 重放)
```

### 2.2 binlog 的三种格式(重要!)

| 格式 | 记录内容 | 说明 |
|------|---------|------|
| **ROW** | 记录**每一行**数据的变化(改前/改后完整值) | ✅ **Canal 必须用这种**,能拿到字段级数据 |
| STATEMENT | 只记录执行的 SQL | 拿不到具体改了什么值 |
| MIXED | 混合,自动切换 | 不完全可控 |

> ⚠️ 坑:binlog_format 不是 ROW,Canal 拿不到"改前/改后"的完整数据。下面环境准备里会开。

### 2.3 Canal 原理:伪装从库

Canal Server 启动后,会假装自己是一个 MySQL 从库,向主库请求 binlog 流;拿到后解析成结构化的"增删改事件",再推给 Canal Client(你的程序)。

```
┌──────────────┐   binlog   ┌──────────────────┐   长连接   ┌──────────────────────┐
│ MySQL Master │ ─────────► │   Canal Server    │ ─────────► │ Canal Client 程序     │
│ (开启 binlog) │ ◄───────── │ (伪装成 MySQL 从库) │            │ (你的 SpringBoot 应用) │
└──────────────┘  伪装从库   └──────────────────┘            └──────────────────────┘
```

**关键点**:
- Canal 只**读** binlog,不碰业务表,对主库**零侵入**
- 主库不需要装任何插件,只要开了 binlog 就行
- Canal 解析出来的事件是结构化的(哪个库、哪张表、哪条数据、改前改后),不是原始 SQL

---

## 三、环境准备

### 3.1 MySQL 开启 binlog

改 MySQL 配置文件(`my.cnf` / `my.ini`,Linux 一般在 `/etc/my.cnf`):

```ini
[mysqld]
server-id=1          # 主库唯一 ID,不能和从库重复
log-bin=mysql-bin    # 开启 binlog,文件名前缀
binlog_format=ROW    # ⚠️ 必须 ROW,否则拿不到字段级数据
```

改完**重启 MySQL**。验证是否生效:

```sql
SHOW VARIABLES LIKE 'log_bin';       -- 应为 ON
SHOW VARIABLES LIKE 'binlog_format'; -- 应为 ROW
```

### 3.2 创建 canal 专用账号

Canal 需要以"从库"身份连 MySQL,所以要给一个账号授予复制权限(给最小权限,别用 root):

```sql
CREATE USER 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
```

### 3.3 启动 Canal Server

1. 下载 `canal.deployer`(GitHub: alibaba/canal releases),解压
2. 修改实例配置 `conf/example/instance.properties`:

```properties
# MySQL 主库地址(内网 IP 用占位符示例)
canal.instance.master.address=your-mysql-host:3306
# 刚创建的账号
canal.instance.dbUsername=canal
canal.instance.dbPassword=canal
```

3. 启动:`bin/startup.sh`(Windows 用 `bin/startup.bat`)
4. Canal Server 默认监听 **11111** 端口
5. 看日志确认成功:`logs/example/example.log` 里出现 `start successfully`

---

## 四、SpringBoot 集成(完整可运行代码)

用现成的 starter:`top.javatool:canal-spring-boot-starter`,加依赖 + 写一个监听器,完事。

### 4.1 pom.xml(注意 logback 排除,坑在第五节)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.1</version>
</parent>

<dependencies>
    <!-- canal 客户端 starter -->
    <dependency>
        <groupId>top.javatool</groupId>
        <artifactId>canal-spring-boot-starter</artifactId>
        <version>1.2.1-RELEASE</version>
        <exclusions>
            <!-- ⚠️ 排除它传递进来的老 logback(1.1.3),否则和 Spring Boot 的 logback 冲突 -->
            <exclusion>
                <groupId>ch.qos.logback</groupId>
                <artifactId>logback-core</artifactId>
            </exclusion>
            <exclusion>
                <groupId>ch.qos.logback</groupId>
                <artifactId>logback-classic</artifactId>
            </exclusion>
            <exclusion>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
            </exclusion>
            <exclusion>
                <groupId>org.slf4j</groupId>
                <artifactId>jcl-over-slf4j</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>

    <!-- 显式声明 logback,版本由 Spring Boot 统一管理(1.2.x) -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-core</artifactId>
    </dependency>
</dependencies>
```

### 4.2 application.yml

```yaml
canal:
  server: your-canal-host:11111   # canal server 地址
  destination: example            # 实例名,默认 example
  user-name: canal
  password: canal
logging:
  level:
    root: info
```

### 4.3 启动类 + 实体类

```java
package com.example.canal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CanalApp {
    public static void main(String[] args) {
        SpringApplication.run(CanalApp.class, args);
    }
}
```

实体类字段要和表字段**一一对应**(数据库下划线会自动映射成驼峰):

```java
package com.example.canal.domain;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String name;
    private String email;
}
```

对应的表:

```sql
CREATE TABLE t_user (
    id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(50),
    email VARCHAR(100)
);
```

### 4.4 监听器(核心)

实现 `EntryHandler<T>`,三个回调方法:增 / 改 / 删。**注意 update 回调有两个参数:改前、改后**。

```java
package com.example.canal.listener;

import com.example.canal.domain.User;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

@Component                // ⚠️ 必须交给 Spring 管理,否则不生效
@CanalTable("t_user")     // 监听哪张表
public class UserListener implements EntryHandler<User> {

    @Override
    public void insert(User user) {
        System.out.println("【新增】用户: " + user);
    }

    @Override
    public void update(User before, User after) {
        System.out.println("【修改】改前: " + before);
        System.out.println("【修改】改后: " + after);
    }

    @Override
    public void delete(User user) {
        System.out.println("【删除】用户: " + user);
    }
}
```

### 4.5 效果演示

1. 启动 CanalApp
2. 在 MySQL 里对 `t_user` 执行增删改:

```sql
INSERT INTO t_user (name, email) VALUES ('张三', 'zhangsan@example.com');
UPDATE t_user SET name = '李四' WHERE id = 1;
DELETE FROM t_user WHERE id = 1;
```

3. 控制台立刻打印:

```
【新增】用户: User(id=1, name=张三, email=zhangsan@example.com)
【修改】改前: User(id=1, name=张三, email=zhangsan@example.com)
【修改】改后: User(id=1, name=李四, email=zhangsan@example.com)
【删除】用户: User(id=1, name=李四, email=zhangsan@example.com)
```

到此,数据库任何变化你的程序都能感知到,后面想同步缓存、同步 ES,就在回调里写逻辑。

---

## 五、踩坑记录(今天真实遇到的)

### 坑 1:logback 版本冲突(最坑,报错启动失败)

**报错**:

```
java.lang.NoClassDefFoundError: ch/qos/logback/core/util/StatusListenerConfigHelper
```

**原因**:`canal-spring-boot-starter` 传递依赖了 **logback-core 1.1.3**(太老,没有 `StatusListenerConfigHelper` 这个类),和 Spring Boot 2.7 的 logback-classic 1.2.x 混在 classpath 里,加载到老版本就炸了。

**排查思路**:老库(canal 1.1.x 时代)拖旧依赖是常态,遇到 `NoClassDefFoundError` 先怀疑 classpath 里有多个版本冲突。可以用 `mvn dependency:tree` 看依赖树:

```bash
mvn dependency:tree -Dincludes=ch.qos.logback
```

**解决**:在 canal starter 上加 exclusions 排除传递的 logback,保留 Spring Boot 统一管理的 1.2.x(见 4.1 的 pom)。

### 坑 2:监听器没加 @Component,一点反应没有

`EntryHandler` 的实现类必须注册成 Spring Bean(加 `@Component` 或在配置类里 `@Bean`),starter 是通过 Spring 容器扫描到的。漏了注解,控制台啥都不打印,还不报错,排查半天。

### 坑 3:binlog_format 不是 ROW

Canal 解析 STATEMENT 格式的 binlog 拿不到行级数据(没有改前改后),表现为"能连上但回调里数据是空的/没触发"。确认 `SHOW VARIABLES LIKE 'binlog_format'` 是 `ROW`。

### 坑 4:表名写错不报错、就是没反应

`@CanalTable("t_user")` 表名写错,连接正常但不触发任何回调。把 canal client 的日志级别调到 debug(application.yml 里 `top.javatool.canal.client` 级别),能看到它到底订阅了哪些表。

---

## 六、总结 + 面试口径

**一句话总结**:Canal = 监听数据库变化的"哨兵",伪装成 MySQL 从库读 binlog,把增删改事件推给业务程序。

**面试三步走**:
1. **是什么**:阿里巴巴开源的 MySQL binlog 增量订阅/消费组件
2. **原理**:Canal Server 伪装成 MySQL 从库,请求并解析 binlog(ROW 格式),通过长连接把结构化事件推给 Canal Client
3. **场景**:缓存同步、数据异构(MySQL → ES)、数据同步、事件驱动

**进阶问题预判**(下次打卡):Canal + MQ 的经典架构(canal 监听 binlog → 发 MQ → 下游消费,解决"一个变化多系统要"的问题);Canal 高可用(多实例 + zookeeper);Canal 的 position(位点)机制——断点续传。

---

## 附:完整工程结构

```
canal-demo/
├── pom.xml
└── src/main/
    ├── java/com/example/canal/
    │   ├── CanalApp.java              # 启动类
    │   ├── domain/User.java           # 实体,对应 t_user 表
    │   └── listener/UserListener.java # 监听器,核心
    └── resources/application.yml
```
