# HuTool 高频核心:基础工具类(2026-09-05)

> 来源:HuTool 课程思维导图整理
> 目标:掌握 util 包最高频的五个工具类——IdUtil(唯一 ID)、StrUtil(字符串)、EnumUtil(枚举)、RandomUtil(随机)、NumberUtil(数字精度)。
> 学习建议:每个 API 敲一遍示例,重点记"坑点"(返回值类型、null 行为),面试和写码都用得上。

---

## 一、IdUtil:唯一 ID 生成

### 1.1 fastSimpleUUID:无横线 UUID

```java
import cn.hutool.core.util.IdUtil;

// 生成不带横线 "-" 的 UUID 字符串,共 32 个字符
String id = IdUtil.fastSimpleUUID();
System.out.println(id);  // 例:9f8a1b2c3d4e5f60718293a4b5c6d7e8
```

- UUID 特点:**随机、无序**;
- 源码逻辑:生成带横杠的 UUID,再 `replace("-", "")` 去掉横线。

### 1.2 雪花算法:有序数字 ID

```java
long snowflake = IdUtil.getSnowflakeNextId();      // 雪花 ID,long 型
String snowStr = IdUtil.getSnowflakeNextIdStr();   // 雪花 ID,字符串型
```

- 雪花 ID **有序递增**,适合做主键(如数据库自增的替代品),UUID 则无序。

> 场景选择:不要求顺序(如日志 traceId)→ UUID;要求趋势递增、当主键 → 雪花。

---

## 二、StrUtil:字符串工具(最常用)

### 2.1 equals:安全的相等比较,自动处理 null

```java
StrUtil.equals(str1, str2);
```

判断规则:

1. 两个都为 null → true;
2. 一个 null 一个非 null → false;
3. 都不为 null → 调 `equals()` 比较内容。

> 替代 `str1.equals(str2)` 的 NPE 风险(JDK7+ 也可用 `Objects.equals`)。

### 2.2 sub:比 JDK substring 安全的截取

```java
public static String sub(CharSequence str, int start, int end)
```

**比 `String.substring` 安全**:越界自动兼容,**不会抛 `StringIndexOutOfBoundsException`**。区间 `[start, end)`,支持负索引(负数从尾部倒数)。

规则:

1. 索引可为负数:`-1` 代表倒数第一个字符;
2. start 超过字符串长度 → 返回空串;
3. end 超过字符串长度 → 截到末尾;
4. start > end → 返回空串;
5. str 为 null → 返回 `""`。

```java
String s = "abcdefg";

StrUtil.sub(s, 1, 3);      // "bc"(下标 1、2)
StrUtil.sub(s, -3, null);  // "efg"(从倒数第 3 位到末尾)
StrUtil.sub(s, 0, -1);     // "abcdef"(到倒数第一位之前)
StrUtil.sub(s, 10, 20);    // ""(越界不抛异常)
StrUtil.sub(null, 1, 2);   // ""
```

### 2.3 format:`{}` 占位符模板(不是 `%`)

底层封装,自动处理 null,比 JDK `String.format` 好用:

```java
StrUtil.format("姓名：{}，年龄：{}", "张三", 20);   // 姓名：张三,年龄：20
StrUtil.format("参数={}", null);                  // 参数=null(直接输出 null 字符串)
StrUtil.format("{}", "a", "b", "c");              // a(多余参数忽略)
StrUtil.format("{}，{}", "A");                    // A,{}(占位符多则原样保留)
```

> ⚠️ 占位符是 `{}`,**不是 `%s`**——别和 String.format 搞混。

### 2.4 lowerFirst:首字母转小写(类名转属性名)

```java
StrUtil.lowerFirst("UserName");  // userName
StrUtil.lowerFirst("ABC");       // aBC(只动首字母)
StrUtil.lowerFirst("name");      // name(本就小写,原样)
StrUtil.lowerFirst("");          // ""
StrUtil.lowerFirst(null);        // null
StrUtil.lowerFirst("123Abc");    // 123Abc(首字符非字母不处理)
```

### 2.5 扩展方法(自己查文档补全)

```java
StrUtil.isBlank(str);   // 是否空白(null/空串/纯空格)
StrUtil.isEmpty(str);   // 是否为空(null/空串)
StrUtil.trim(str);      // 去首尾空格
StrUtil.replace(...);   // 替换
StrUtil.split(...);     // 切割
StrUtil.contains(...);  // 包含判断
```

---

## 三、EnumUtil:枚举工具

### 3.1 getBy:按 getter 属性值反查枚举

```java
public static <E extends Enum<E>, T> E getBy(Function<E, T> mapper, T value)
```

逻辑:遍历枚举全部常量,调 mapper 拿属性值,`equals` 对比 value,匹配到第一个就返回;**找不到返回 null**。

```java
import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

@Getter
public enum OrderStatusEnum {
    WAIT_PAY(1, "待支付"),
    PAID(2, "已支付"),
    CANCEL(3, "已取消");

    private final Integer code;
    private final String desc;

    OrderStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

// 用法:根据 code=2 拿到 PAID 枚举
OrderStatusEnum status = EnumUtil.getBy(OrderStatusEnum::getCode, 2);
System.out.println(status);   // PAID

// 找不到返回 null!
OrderStatusEnum none = EnumUtil.getBy(OrderStatusEnum::getCode, 999);
System.out.println(none);     // null
```

### 3.2 其他常用方法

```java
// 不返回枚举对象,直接取另一个字段值(code=2 → "已支付")
String desc = EnumUtil.getFieldBy(OrderStatusEnum::getDesc, OrderStatusEnum::getCode, 2);

// 获取全部枚举 name 集合:[WAIT_PAY, PAID, CANCEL]
List<String> names = EnumUtil.getNames(OrderStatusEnum.class);

// 获取枚举某个字段的全部值:[1, 2, 3]
List<Object> codes = EnumUtil.getFieldValues(OrderStatusEnum.class, "code");
```

> 实际价值:前端传 code,后端一行反查枚举,不用再手写 switch/for 遍历。

---

## 四、RandomUtil:随机工具

```java
// 从集合中随机取 count 个元素,允许重复(可放回抽样)
public static <T> List<T> randomEleList(Collection<T> collection, int count)
```

- 作用:从原集合随机抽取 count 个元素,**允许重复选到同一个元素**;
- 返回新 List,**原集合不变**。

```java
List<String> pick = RandomUtil.randomEleList(nameList, 3);  // 随机抽 3 个(可重复)
```

---

## 五、NumberUtil:数字精度工具(重点)

### 5.1 round:安全四舍五入

```java
// 重载1:默认四舍五入 HALF_UP
public static BigDecimal round(Number number, int scale)
// 重载2:自定义舍入模式
public static BigDecimal round(Number number, int scale, RoundingMode roundingMode)
```

- `number`:double / Double / BigDecimal / Integer 都支持;
- `scale`:保留小数位数,小于 0 强制变 0;
- **返回 BigDecimal 对象,不是 String、不是 double!**

```java
import cn.hutool.core.util.NumberUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;

double num = 3.1456;

BigDecimal bigDec = NumberUtil.round(num, 2);
System.out.println(bigDec);                              // 3.15

double d = NumberUtil.round(num, 2).doubleValue();       // 需要 double 记得 .doubleValue()

// 自定义模式:直接截断(向下舍弃)
BigDecimal floorVal = NumberUtil.round(num, 2, RoundingMode.FLOOR);
System.out.println(floorVal);                            // 3.14

// roundStr:直接返回格式化字符串
String str = NumberUtil.roundStr(3.1456, 2);             // "3.15"

// 银行家舍入(四舍六入五成双,财务场景)
BigDecimal even = NumberUtil.roundHalfEven(3.145, 2);
```

### 5.2 ⚠️ 重要坑点

```java
// ❌ 编译报错!返回的是 BigDecimal,不能直接赋给 double
double v = NumberUtil.round(1.234, 2);

// ✅ 正确写法
double v = NumberUtil.round(1.234, 2).doubleValue();
```

1. **double 传入仍有浮点数先天缺陷**:金额计算尽量用 `BigDecimal(String)` 构造,不要直接传 double:

```java
// 不推荐(0.1+0.2 本身就不是精确的 0.3)
NumberUtil.round(0.1 + 0.2, 2);

// 推荐
NumberUtil.round(new BigDecimal("0.3"), 2);
```

2. **null 入参会抛空指针**,不会自动容错:

```java
NumberUtil.round(null, 2);   // NPE!用前记得判空
```

### 5.3 round() vs roundStr()

- `round()` → **BigDecimal**,适合继续参与数学计算;
- `roundStr()` → **String**,适合页面展示,不再运算。

### 5.4 其他方法

```java
NumberUtil.add(a, b);           // 加
NumberUtil.sub(a, b);           // 减
NumberUtil.mul(a, b);           // 乘
NumberUtil.div(a, b);           // 除(防除 0)
NumberUtil.isNumber(str);       // 判断字符串是否是数字
```

### 5.5 附:银行家舍入(四舍六入五成双)

别名:Round-Half-Even,IEEE 754 浮点数标准默认舍入模式。

- **目的**:传统四舍五入遇 0.5 永远进位,大量计算会累积正偏差;银行家舍入让一半 0.5 进位、一半舍去,统计上抵消累计误差,金融、科学计算常用。

**口诀**:四舍六入五考虑,五后非零就进一,五后为零看奇偶,五前为偶应舍去,五前为奇要进一。

**整数示例**:

| 原值 | 传统四舍五入 | 银行家舍入 |
|------|-------------|-----------|
| 2.5  | 3           | **2**(前位 2 偶数,舍去) |
| 3.5  | 4           | 4(前位 3 奇数,进位) |
| 4.5  | 5           | **4**(前位 4 偶数,舍去) |

**保留 2 位小数示例**:

| 原值 | 银行家舍入结果 | 说明 |
|------|--------------|------|
| 1.244 | 1.24 | 四舍 |
| 1.246 | 1.25 | 六入 |
| 1.2451 | 1.25 | 5 后面有 1 ≠ 0,直接进位 |
| 1.2350 | 1.24 | 5 后全 0,前位 3 奇数 → 进位成偶数 |
| 1.2450 | 1.24 | 5 后全 0,前位 4 偶数 → 舍去 |

---

## 六、本节速记

| 工具类 | 记住这三个点 |
|--------|-------------|
| IdUtil | `fastSimpleUUID()` 无横线 32 位;雪花 ID 有序,适合主键 |
| StrUtil | `equals` 防 NPE;`sub` 越界不抛异常、支持负索引;`format` 用 `{}`;`lowerFirst` 类名转属性 |
| EnumUtil | `getBy(枚举::getCode, 值)` 反查枚举,找不到返回 null |
| RandomUtil | `randomEleList(coll, n)` 随机取 n 个(可重复),原集合不变 |
| NumberUtil | `round` 返回 **BigDecimal** 要 `.doubleValue()`;null 入参 NPE;金额用 `new BigDecimal("...")`;财务精度用银行家舍入 |
