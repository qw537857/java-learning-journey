# HuTool 高频核心:集合工具(2026-09-05)

> 来源:HuTool 课程思维导图整理
> 目标:掌握 collection 包三个高频类——CollStreamUtil(集合流式处理)、CollUtil(集合通用工具)、ListUtil(List 工具)。
> 学习建议:重点记 null 安全特性和坑点(返回 null 要判空、HashSet 无序),这些是平时最容易踩的。

---

## 一、CollStreamUtil:集合的 Stream 快捷封装

把"遍历 + map + collect"封装成一行,内部已做 null 判断,**传 null 直接返回空集合,不会空指针**。

```java
// 提取集合中对象的某个属性,生成新 List
// 原生写法要自己判 null:
// if (CollUtil.isNotEmpty(list)) {
//     List<String> nameList = list.stream().map(Student::getName).collect(Collectors.toList());
// }
List<String> nameList = CollStreamUtil.toList(list, Student::getName);   // null 安全

// 提取属性转 Set
CollStreamUtil.toSet(list, Student::getId);

// 集合转 Map:key=对象属性,value=对象本身
CollStreamUtil.toIdentityMap(list, Student::getId);

// 集合转 Map:key 和 value 都取自对象属性
CollStreamUtil.toMap(list, Student::getId, Student::getName);

// 按属性分组
CollStreamUtil.groupByKey(list, Student::getClassId);

// 转 Stream
CollStreamUtil.toStream(list);
```

> 价值:少写 `if (list != null && !list.isEmpty())` 和一堆 stream 样板代码。

---

## 二、CollUtil:集合通用工具(最高频)

### 2.1 判空三件套

```java
CollUtil.isEmpty(list);      // 是否为空(null 或 size=0)
CollUtil.isNotEmpty(list);   // 是否不为空
// 同样支持 Map
CollUtil.isEmpty(map);
```

> 一个方法同时判断 null 和空容器,不用再写 `list == null || list.isEmpty()`。

### 2.2 size:安全获取大小

```java
// 集合为 null 时返回 0,不抛空指针
public static int size(Collection<?> collection)
// Map 也能求 size
public static int size(Map<?, ?> map)
```

### 2.3 getFirst / getLast:安全取首尾元素

不用手写判空,**不会抛索引越界异常**:

```java
User user = CollUtil.getFirst(userList);
User last = CollUtil.getLast(userList);
```

⚠️ **坑点 1:返回值可能为 null,拿到后必须判空再调用方法**

```java
// ❌ 危险:集合为空时 getFirst 返回 null,直接 NPE
CollUtil.getFirst(list).toString();

// ✅ 正确姿势
User user = CollUtil.getFirst(userList);
if (user != null) {
    // 业务...
}
```

⚠️ **坑点 2:Set 是无序的!**

```java
HashSet<String> set = new HashSet<>();
set.add("a");
set.add("b");
// HashSet 拿到的不是插入顺序第一个,而是哈希顺序,结果不一定是 "a"
String s = CollUtil.getFirst(set);
// 想要插入顺序首元素,要用 LinkedHashSet
```

⚠️ **坑点 3:只支持 Collection,数组要用 ArrayUtil**

```java
String[] arr = {"x", "y"};
String val = ArrayUtil.getFirst(arr);   // 数组用 ArrayUtil
```

### 2.4 高频相近 API

```java
CollUtil.get(list, index);      // 安全取指定下标,越界返回 null
CollUtil.sub(list, start, end); // 安全截取集合
CollUtil.newArrayList();        // 快速建 ArrayList
CollUtil.join(list, ",");       // 集合拼接成字符串
CollUtil.isEmpty(list);
CollUtil.isNotEmpty(list);
```

---

## 三、ListUtil:List 工具

### 3.1 toList:各种来源转 ArrayList

把数组、单个对象、集合、Iterable 转成 ArrayList,兼容包装、null 安全。

```java
List<Long> list = ListUtil.toList(keys);   // 数组 → List
```

⚠️ **坑点 1:传单个 null 对象 → 得到包含 null 元素的 List `[null]`(不是空列表!)**

```java
// ❌ 以为得到空列表,实际是 [null]
List<Long> bad = ListUtil.toList((Long) null);
System.out.println(bad);               // [null]
CollUtil.isNotEmpty(bad);              // true!集合不为空,只是元素是 null
```

**集合变量本身为 null → 返回空集合 `[]`**:

```java
List<Long> source = null;
List<Long> ok = ListUtil.toList(source);
System.out.println(ok);                // []
```

⚠️ **坑点 2:返回的是 ArrayList,支持 add/remove;和 JDK `Arrays.asList()` 不同**

```java
// JDK 原生:固定大小列表,不能 add,会抛 UnsupportedOperationException
List<Long> jdkList = Arrays.asList(arr);

// HuTool:可以随意增删
List<Long> hutoolList = ListUtil.toList(arr);
hutoolList.add(1L);   // ✅ 没问题
```

---

## 四、本节速记

| 工具 | 一句话 + 坑点 |
|------|--------------|
| CollStreamUtil | 一行搞定 map/set/map/groupBy,内部判空,传 null 不 NPE |
| CollUtil | isEmpty/isNotEmpty/size/getFirst/getLast;getFirst 可能返回 **null 要判空**,HashSet **无序**,数组用 ArrayUtil |
| ListUtil | 各种来源转 **ArrayList**(可增删);传单个 null 对象得到 `[null]` 不是空表;集合 null 才是 `[]` |
