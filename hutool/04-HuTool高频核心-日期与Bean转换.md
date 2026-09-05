# HuTool 高频核心:日期与 Bean 转换(2026-09-05)

> 来源:HuTool 课程思维导图整理
> 目标:掌握 date 包的 DateUtil/LocalDateTimeUtil 和 bean/convert 包的 BeanUtil/Convert——日常 CRUD 和接口开发天天用。
> 学习建议:日期部分区分"JDK8 前后两套 API 的 HuTool 封装";转换部分记牢 Convert 的"失败返回默认值不抛异常"。

---

## 一、date 包:日期时间工具

### 1.1 DateUtil(基于老 Date API)

```java
import cn.hutool.core.date.DateUtil;

DateUtil.now();                  // 当前时间字符串,格式:yyyy-MM-dd HH:mm:ss
DateUtil.nowDate();              // 当前时间,返回 Date 对象
DateUtil.today();                // 只有日期:yyyy-MM-dd
DateUtil.current();              // 当前时间戳(毫秒)
DateUtil.format(new Date(), "yyyy/MM/dd");   // Date → 指定格式字符串
```

### 1.2 LocalDateTimeUtil(JDK8+ 新时间 API,5.3.9+ 提供)

```java
import cn.hutool.core.date.LocalDateTimeUtil;

// 返回 LocalDateTime 对象(不是字符串!),系统默认时区当前时间
LocalDateTime now = LocalDateTimeUtil.now();
System.out.println(now);   // 2026-09-05T09:30:00.123
```

> 项目如果已全面用 JDK8 时间 API(LocalDate/LocalDateTime),优先用 LocalDateTimeUtil,配套方法更全。

---

## 二、bean 包:BeanUtil 对象转换

### 2.1 toBean:Map/Bean → 目标 Bean

```java
// 把消息对象(Map 或 Bean)转成目标 Bean
MyMessage msg = BeanUtil.toBean(message, MyMessage.class);
```

### 2.2 toBeanIgnoreError:转换失败不抛异常(5.4.0+)

单个字段转换失败**不抛异常,直接跳过该字段**,继续转其他字段:

```java
MyMessage msg = BeanUtil.toBeanIgnoreError(source, MyMessage.class);

// 内部等价于:
BeanUtil.toBean(source, clazz,
        CopyOptions.create().setIgnoreError(true));
```

> 适用:接收外部数据时,个别字段类型对不上不影响整体转换。

---

## 三、convert 包:Convert 类型转换

### 3.1 toStr:任意对象转字符串(不 NPE)

```java
// 重载1:obj 为 null → 返回 null
public static String toStr(Object value)
// 重载2:obj 为 null 或转换失败 → 返回默认值
public static String toStr(Object value, String defaultValue)

// null 场景
Object obj = null;
Convert.toStr(obj);          // null(不会 NPE)
Convert.toStr(obj, "");      // "" 空字符串(常用!)
Convert.toStr(obj, "-");     // "-"
```

### 3.2 toLong:任意类型转 Long(失败给默认值,不抛异常)

```java
import cn.hutool.core.convert.Convert;

Convert.toLong("12345");            // 12345(正常数字字符串)
Convert.toLong(999);                // 999(数字)
Convert.toLong("abc");              // null(转换失败,无默认值)
Convert.toLong("abc", 0L);          // 0(转换失败用默认值)
Convert.toLong(null, -1L);          // -1(null 输入用默认值)
```

### 3.3 其他常用转换

```java
Convert.toInt(...);      // 转 Integer
Convert.toStr(...);      // 转 String(上面讲过)
Convert.toBigDecimal(...);
// 套路都一样:转换失败 → 返回 null 或默认值,不抛异常
```

> 与原生 `Integer.parseInt("abc")` 不同:原生会抛 NumberFormatException,HuTool Convert 给默认值或 null,接口层容错更友好。

---

## 四、本节速记

| 工具 | 要点 |
|------|------|
| DateUtil | `now()` 字符串、`nowDate()` Date、`today()` 日期、`format(date, 格式)` |
| LocalDateTimeUtil | JDK8 时间 API 配套,`now()` 返回 **LocalDateTime 对象** |
| BeanUtil | `toBean(源, 目标.class)` 对象转换;`toBeanIgnoreError` 单字段失败跳过不抛异常 |
| Convert | `toStr/toLong/...`:**null/失败返回 null 或默认值,绝不抛异常**;带默认值重载最常用 |
