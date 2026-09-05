# HuTool 入门与断言工具(2026-09-05)

> 来源:HuTool 课程思维导图整理
> 目标:搞清 HuTool 是什么、依赖怎么引、核心包结构,并掌握 lang 包的断言工具 Assert。
> 学习建议:先配好依赖,把 Assert 两个方法敲一遍,体会"用断言替代一堆 if 判空"的写法。

---

## 一、HuTool 是什么

- HuTool 是一个 **Java 工具类库**,把日常开发常用的功能(字符串、集合、日期、JSON、加密、Excel 等)都封装成静态方法,开箱即用;
- 官网文档:https://hutool.cn/
- 一句话:**"一个烂代码的克星"——少写重复的工具方法,把精力留给业务。**

## 二、依赖引入

### 2.1 Maven(以 5.8.30 为例)

```xml
<!-- hutool-core 核心包,必引 -->
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-core</artifactId>
    <version>5.8.30</version>
</dependency>

<!-- 获取 Spring 容器 Bean(SpringUtil 在这个包里) -->
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-spring</artifactId>
    <version>5.8.30</version>
</dependency>

<!-- 集成 Jackson(SpringBoot 项目推荐) -->
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-json-jackson</artifactId>
    <version>5.8.30</version>
</dependency>
```

### 2.2 模块结构速览(按需引入)

| 模块 | 作用 | 必引? |
|------|------|-------|
| **hutool-core** | 核心工具(字符串/集合/日期/Bean/转换等) | ✅ 必选 |
| hutool-spring | SpringUtil 等 Spring 集成 | 按需 |
| hutool-json-jackson | JSON 序列化的 Jackson 适配 | 按需 |
| hutool-crypto | 加密解密 | 按需 |
| hutool-http | Http 请求工具 | 按需 |
| hutool-poi | Excel 读写 | 按需 |
| hutool-db | JDBC 数据库工具 | 按需 |

> 懒人方案:也可以直接引入 `hutool-all`(全量打包),但体积大,生产建议按需引。

---

## 三、lang 包:Assert 断言工具

做**参数校验**的利器:条件不满足直接抛异常,代替手写 if + throw。

### 3.1 notNull:对象为 null 直接抛异常

```java
import cn.hutool.core.lang.Assert;

// 如果 name 是 null,抛出 IllegalArgumentException("姓名不能为null")
Assert.notNull(name, "姓名不能为null");
```

### 3.2 notEmpty:null 或空就抛异常

支持集合 / 数组 / Map / 字符串:

```java
Assert.notEmpty(list3, "列表不能为空");
// 集合为 null 或 size==0 都会抛异常
```

> 用途:接口入口参数校验,一行搞定,不用再写 `if (x == null || x.isEmpty()) throw ...`。

---

## 四、本节速记

1. HuTool = Java 工具类库,静态方法开箱即用;
2. 依赖:**hutool-core 必引**,spring/json/crypto/http/poi/db 按需引;
3. `Assert.notNull(x, msg)`、`Assert.notEmpty(coll, msg)`:校验不过直接抛异常;
4. 断言让代码少一层 if 嵌套,参数校验更清爽。
