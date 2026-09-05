# HashMap 源码剖析(面试题精讲 · 2026-09-04)

> 来源:面试题精讲思维导图整理
> 目标:把 HashMap 面试高频题讲透——put/get 流程、hash 设计、扩容、equals/hashCode 约定、红黑树,以及"手写一个 O(1) 哈希表"的设计思想。
> 学习建议:先画一遍"数组 + 链表 + 红黑树"的结构图,再对着源码(JDK8)把每条流程走一遍。

---

## 一、HashMap 基础(JDK8)

- 底层结构:**数组 + 单向链表 + 红黑树**;
- 默认容量 **16**,负载因子 **0.75**,扩容阈值 = 16 × 0.75 = **12**;
- 链表长度 **≥ 8** 且数组长度 **≥ 64** → 链表转红黑树;链表长度 **≤ 6** → 红黑树退化为链表;
- 数组长度 **< 64** 时,链表再长也**先扩容**,不树化。

> 一句话:**先散列到数组,冲突了挂链表,链表太长(且数组够大)就升级成红黑树。**

---

## 二、面试题:put 是怎么实现的?

源码流程拆成 7 步:

```
1. 首次 put?→ 先初始化:容量 16、负载因子 0.75、扩容阈值 16*0.75=12
2. 根据 key 算 hash → 定位数组索引
3. 索引位置为 null → 直接放入新元素
4. 索引位置不为 null → 判断新旧元素"地址相同 or key 相等(equals)"
   → 是同一个 key:新 value 覆盖旧 value,返回旧 value
5. 不是同一个 key → 判断该位置节点类型:
   - 是 treeNode → 走红黑树插入
   - 是链表节点 → 尾插法新增节点
6. 新增后链表长度 ≥ 8 且数组长度 < 64 → 先扩容(resize)
7. 链表长度 ≥ 8 且数组长度 ≥ 64 → 链表转红黑树
   (先单向链表转双向链表,再转红黑树)
```

---

## 三、面试题:hash 的设计

### 3.1 为什么不直接用 key.hashCode(),而是要与高 16 位异或?

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**目的:扰动**。让 key 的所有位都参与运算,使 hash 分布更均匀,减少碰撞。

> 原因:数组索引 = hash & (n-1),n 默认 16 时只用到 hash 的**低 4 位**,高位完全不参与。让高 16 位与低 16 位异或,把高位信息"混"进低位,分布更均匀。

### 3.2 为什么容量必须是 2 的幂?输入 10 会怎样?

1. **自动修正**:构造 HashMap 时传入非 2 的幂(如 10),`tableSizeFor()` 会把它转成**大于等于它的最小 2 的幂**(10 → 16);
2. **避免漏位**:如果不处理,数据分布不均匀。比如容量 10,`hash & 9` 只会得到 0~9,但二进制下**奇数位下标可能永远用不到**,浪费一半桶;
3. **用位运算代替取模**:只有容量是 2 的幂时,`hash % n` 才等价于 `hash & (n-1)`,位运算比取模快得多。

```java
// 两个公式只有在 2 的幂次时才相等
hash % 16      // 取模 → [0,15]
hash & (16-1)  // 位运算 → [0,15]
```

> 面试延伸:为什么默认是 16?太小容易频繁扩容,太浪费;2 的幂兼顾了分布均匀和位运算优化,16 是经验折中值。

### 3.3 两个对象的 hashCode 相等会怎样?

就是**哈希碰撞**:它们会落到同一个桶里,用链表或红黑树串起来。

- 数组长度 < 64 且链表长度 ≤ 8 → 单向链表;
- 否则 → 红黑树。

### 3.4 两个 key 的 hash 相同,get 时怎么找到正确的值?

```
1. 先判断索引处首元素是否就是要找的元素(== 或 equals)→ 是则返回
2. 不是 → 看 next 是否为 null
3. next 不为 null → 判断是否为 treeNode
   - 是 → 走红黑树查找
   - 否 → 链表,循环遍历 equals 比较,找到返回
4. 都没有 → 返回 null
```

---

## 四、面试题:扩容 resize() 是怎么实现的?

### 4.1 什么时候扩容?

1. map 元素个数 **> 扩容阈值**(容量 × 负载因子);
2. 链表长度 **≥ 8 且数组长度 < 64**(此时用扩容代替树化,让数据散得更开)。

### 4.2 扩容过程

```
1. 容量翻倍(如 16 → 32),阈值同步翻倍
2. 遍历旧数组每个索引位置:
   - 为 null → 啥也不做
   - 只有一个元素 → 重新算 hash,放入新数组对应索引
   - 是红黑树 → 做红黑树分割(拆成低位树/高位树,节点 ≤ 6 时退化成链表)
   - 是链表 → 按 (e.hash & oldCap) 拆成低位链表和高位链表
3. 低位链表放回原位 oldIndex,高位链表放到 oldIndex + oldCap
```

> 亮点:JDK8 扩容不用重新 hash,只看 `e.hash & oldCap` 是 0 还是 1,0 留低位、1 去高位(原下标 + 旧容量),效率极高。

---

## 五、面试题:equals 和 hashCode 的约定

### 5.1 为什么重写 equals 必须重写 hashCode?

hashCode 不重写时基于**内存地址**生成,同一个逻辑对象每次 new 出来 hash 不同:

```java
Person p1 = new Person(18, "Tom");  // hash=18 → index 2
Person p2 = new Person(18, "Tom");  // hash=19 → index 3  ← 内容相同却散到不同桶
```

- equals 相同而 hashCode 不同 → 相同对象放进不同桶,HashMap 无法识别"同一个 key",**代码不稳定**;
- hashCode 相同而 equals 不同 → 不同对象挤同一个桶(只是碰撞,equals 能区分),但碰撞多影响性能;
- **约定:equals 相等的对象,hashCode 必须相等**(反过来不要求)。

### 5.2 手写规范示例

```java
public class Person {
    private int age;
    private float height;
    private String name;

    // equals:先比地址,再比类型,最后逐个比成员变量
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        Person person = (Person) obj;
        return person.age == age
            && person.height == height
            && (name == null ? person.name == null : name.equals(person.name));
    }

    // hashCode:所有参与 equals 的字段都要参与运算(参考 String 的 31 倍累加)
    @Override
    public int hashCode() {
        int h = Integer.hashCode(age);
        h = h * 31 + Float.hashCode(height);
        h = h * 31 + (name != null ? name.hashCode() : 0);
        return h;
    }
}
```

---

## 六、为什么引入红黑树?(传统 HashMap 的缺点)

- 单向链表查找是 **O(n)**,红黑树查找是 **O(log n)**;
- 当 hash 冲突严重时,链表会退化成"一条长串",性能崩坏;
- 红黑树用**空间换时间**,访问路径更短、效率更高。

---

## 七、设计思想:如何手写一个 O(1) 的哈希表

### 7.1 场景题:写字楼通讯录

> 需求:存放所有公司通讯信息;座机号(最长 8 位)作 key,公司详情作 value;增删查都要 O(1)。

最朴素实现:开一个大数组,**座机号直接当下标**:

```java
public class PhoneList {
    private Company[] companies = new Company[10000000];

    public void add(int phone, Company company) {
        companies[phone] = company;   // O(1)
    }
    public void remove(int phone) {
        companies[phone] = null;      // O(1)
    }
    public Company get(int phone) {
        return companies[phone];      // O(1)
    }
}
```

**存在的问题**:① 空间浪费巨大(10 个号也要 1000 万数组);② 号码范围不连续时没法当下标。→ 所以才需要 **hash 函数把任意 key 映射到数组下标**,并靠"哈希函数质量 + 扩容"控制碰撞。

### 7.2 什么是良好的哈希函数?

- **让哈希值更均匀分布** → 减少哈希冲突 → 提升哈希表性能;
- 两个目标:① 尽量让每个 key 的哈希值唯一;② 尽量让 key 的所有信息参与运算。

### 7.3 各类型 key 的哈希值怎么生成?(源码思路)

| key 类型 | 生成方式 |
|----------|----------|
| int | 整数值直接当哈希值(`hashCode(int) { return value; }`) |
| float | 把存储的二进制格式转成整数值 |
| long / double | `(int)(value ^ (value >>> 32))`,高 32 位与低 32 位混合 |
| String | 多项式累加:`h = 31 * h + c` |
| 自定义对象 | 参考 String,把所有成员都乘 31 累加 |

字符串 `"jack"` 的哈希值原理(本质是多项式展开):

```
普通整数:5489 = 5*10^3 + 4*10^2 + 8*10^1 + 9*10^0
字符串:  j*a^3 + a*a^2 + c*a^1 + k*a^0  =  [(j*a + a)*a + c]*a + k
JDK 乘数 n 取 31:① 31 是奇素数,统计上分布更均匀;
               ② JVM 会把 31*i 优化成 (i<<5)-i,速度快
```

```java
String s = "jack";
int h = 0;
for (int i = 0; i < s.length(); i++) {
    h = 31 * h + s.charAt(i);
}
System.out.println(h);
System.out.println(s.hashCode());   // 两者相等
```

---

## 八、高频问题速记

| 问题 | 一句话答案 |
|------|-----------|
| put 怎么实现 | 算 hash 定位桶:空→直放;同 key→覆盖;链表→尾插;超 8 且数组≥64→树化,数组<64→扩容 |
| 为什么高 16 位异或 | 扰动,让高位参与定位,分布更均匀 |
| 为什么容量是 2 的幂 | 防漏位 + 用 `&(n-1)` 代替取模 |
| 什么时候扩容 | 元素数 > 阈值;或链表≥8 且数组<64 |
| 扩容怎么搬数据 | 只看 `e.hash & oldCap`:0 留原下标,1 去 原下标+oldCap |
| 为什么重写 equals 要重写 hashCode | 保证"相等对象进同一桶",否则 HashMap 认不出同一个 key |
| 链表为什么变红黑树 | 查找 O(n)→O(log n),空间换时间 |
| 什么情况不能保证 O(1) | 哈希函数差/不扩容导致大量碰撞时退化 |
