# HuTool 数据处理与 Spring 集成(2026-09-05)

> 来源:HuTool 课程思维导图整理
> 目标:掌握 map 包的 MapUtil、json 包的 JSONUtil,以及扩展包里的 SpringUtil。
> 学习建议:JSONUtil 是接口开发的常客;SpringUtil 属于 hutool-spring 扩展包,别加到 core 的依赖里找不到类。

---

## 一、MapUtil:Map 工具

### 1.1 isNotEmpty / isEmpty

```java
MapUtil.isNotEmpty(map);
MapUtil.isEmpty(map);
```

判断规则(以 isNotEmpty 为例):

1. `map == null` → isEmpty=true,isNotEmpty=false;
2. `map.size() == 0` 空 Map → isEmpty=true,isNotEmpty=false;
3. map 里有 key(**哪怕 value 是 null**)→ isNotEmpty=true。

> 注意:value 为 null 但 key 存在,也算"非空 Map"。

---

## 二、JSONUtil:JSON 序列化/反序列化

### 2.1 toJsonStr:对象 → JSON 字符串

```java
// 将对象(Bean、Map、集合)序列化为 JSON 字符串
String json = JSONUtil.toJsonStr(myMessage);
System.out.println(json);
// {"name":"张三","age":20}
```

> ⚠️ 底层默认依赖 **Jackson**,没有引入 Jackson 会报错;SpringBoot 项目天然带 Jackson,放心用。

### 2.2 toBean:JSON 字符串 → Java Bean

```java
// JSON 字符串 → 实体 Bean(底层封装 Jackson 反序列化)
MyMessage msg = JSONUtil.toBean(json, MyMessage.class);
```

### 2.3 其他常用

```java
JSONUtil.parseObj(json);      // 转 JSONObject,按 key 取值
JSONUtil.parseArray(json);    // 转 JSONArray
JSONUtil.toJsonStr(map);      // Map → JSON
```

> 日常开发三板斧:`对象 → JSON 字符串(存库/传前端)`、`JSON 字符串 → Bean(接前端/调接口)`、`parseObj 取个别字段`。

---

## 三、SpringUtil:从 Spring 容器拿 Bean

### 3.1 ⚠️ 先引对包

SpringUtil 属于 **hutool-spring(extra-spring)扩展包,不是 hutool-core**:

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-spring</artifactId>
    <version>5.8.30</version>
</dependency>
```

### 3.2 getBeansOfType:获取指定类型的所有 Bean

```java
// 从 Spring 容器获取所有指定类型(包括子类、实现类)的 Bean 实例
// 返回 Map<beanName, Bean实例>
public static <T> Map<String, T> getBeansOfType(Class<T> type)

Map<String, Agent> agents = SpringUtil.getBeansOfType(Agent.class);
```

> 典型场景:拿到某个接口的**全部实现类 Bean**,批量分发/策略路由。

### 3.3 其他常用方法

```java
SpringUtil.getBean("beanName");          // 按名字拿
SpringUtil.getBean(Xxx.class);           // 按类型拿
SpringUtil.getApplicationContext();      // 直接拿 ApplicationContext
```

> 价值:在**非 Spring 管理的类**(如工具类、普通 new 出来的对象)里也能拿到容器 Bean,摆脱"必须注入才能用"的限制。

---

## 四、本节速记

1. `MapUtil.isNotEmpty(map)`:null 或 size=0 才算空,key 存在(value 为 null)不算空;
2. `JSONUtil.toJsonStr(obj)` 序列化、`JSONUtil.toBean(json, X.class)` 反序列化,底层默认 Jackson;
3. `SpringUtil.getBeansOfType(X.class)` 拿某类型全部 Bean(**包括子类/实现类**),用于批量分发/策略路由;
4. SpringUtil 在 **hutool-spring** 包,别漏依赖。

## 五、HuTool 使用总纲(全篇回顾)

```
语言基础:Assert 断言(参数校验)
高频核心:IdUtil(唯一ID) / StrUtil(字符串) / EnumUtil(枚举) / RandomUtil(随机) / NumberUtil(数字精度)
          CollStreamUtil + CollUtil + ListUtil(集合)
          DateUtil + LocalDateTimeUtil(日期) / BeanUtil(对象转换) / Convert(类型转换)
数据处理:MapUtil(Map) / JSONUtil(JSON)
扩展集成:SpringUtil(Spring 容器 Bean,需 hutool-spring 包)
```

**通用心法**:

1. HuTool 全家桶**自动处理 null**,判空代码大幅减少,但**返回值可能是 null**,拿到要判空;
2. 转换类方法失败返回 null/默认值,**不抛异常**(和 JDK 原生不同);
3. 注意**返回类型**:如 `NumberUtil.round` 返回 BigDecimal、`LocalDateTimeUtil.now` 返回 LocalDateTime,不是字符串;
4. 用哪个功能引哪个包(core 必引),版本看官方最新稳定版。
