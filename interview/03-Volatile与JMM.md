# Volatile 与 JMM(面试题精讲 · 2026-09-04)

> 来源:面试题精讲思维导图整理
> 目标:把 volatile 的"两个保证、一个不保证"讲透,并理解背后的 CPU 缓存一致性(MESI)、内存屏障和 happens-before。
> 学习建议:先背结论(可见性 + 禁重排,不保证原子性),再理解硬件层为什么需要这些机制。

---

## 一、volatile 的作用(背结论)

1. **保证多线程下变量的可见性**;
2. **禁止指令重排序**;
3. ⚠️ **不能保证原子性**(如 volatile int 的 `count++` 依然不是原子的)。

---

## 二、为什么需要 volatile:CPU 三级缓存与缓存一致性

### 2.1 三级缓存回顾

- L1、L2 在每个 CPU 核中,L3 被所有核共享;
- 越靠近 CPU 越小越快,越远越大越慢。

### 2.2 缓存一致性问题

> 如果数据 x 在 CPU 第 0 核的缓存里被更新了,其他核上 x 的值也必须跟着更新——这就是**缓存一致性**问题。

### 2.3 MESI 协议(缓存行四种状态)

| 状态 | 含义 |
|------|------|
| M(Modified 修改) | 缓存行只在本缓存中且是**脏的**(比主存新);写回主存前,其他读必须等待 |
| E(Exclusive 独占) | 只在本缓存中且**干净**(与主存一致);可响应读变 S,或被写变 M |
| S(Shared 共享) | 可能存在于多个缓存中且干净;可随时丢弃(变 I) |
| I(Invalid 无效) | 缓存行无效(未使用) |

> 核心思想:缓存行在各核之间通过状态同步,保证"谁改了,别人能看到"。

### 2.4 CPU 乱序执行与两个优化结构

为了保证一致性,CPU 需要等待其他核返回确认(Invalidate Ack),等待很耗时,于是:

- **Store Buffer(存储缓冲)**:CPU 写数据时不必死等别人确认,先把写操作放进 Store Buffer,异步处理——但也导致**写操作可能暂时对其他核不可见**;
- **Invalidate Queue(失效队列)**:缓存繁忙/收到大量 Invalidate 时,先把失效请求排队、立刻回 Ack,稍后再处理——导致**读操作可能读到旧数据**。

这两个结构正是"可见性/有序性问题"的硬件根源,于是需要**内存屏障**。

---

## 三、内存屏障(硬件层)

### 3.1 写屏障(Write Barrier / smp_wmb)

防止写操作被重排:屏障**之前**的写,必须先于屏障**之后**的写被其他 CPU 看到。

```c
// CPU 0 执行 foo():先写 a,再写 b(a 的写被 Store Buffer 延迟)
void foo(void) {
    a = 1;
    smp_wmb();   // 加写屏障:a=1 必须先于 b=1 对外可见
    b = 1;
}

// CPU 1 执行 bar()
void bar(void) {
    while (b == 0) continue;
    assert(a == 1);   // 有屏障后,a==1 一定成立
}
```

### 3.2 读屏障(Read Barrier / smp_rmb)

防止读操作被重排,并强制**先处理 Invalidate Queue** 再读,保证读到最新值。

```c
void bar(void) {
    while (b == 0) continue;
    smp_rmb();       // 先处理完失效队列,再读 a
    assert(a == 1);
}
```

---

## 四、JMM 中的四类内存屏障

| 屏障 | 语义 | 对应硬件 |
|------|------|----------|
| **LoadLoad** | Load1, LoadLoad, Load2:Load2 不能重排到 Load1 前 | 类似读屏障(先处理 Invalidate Queue) |
| **StoreStore** | Store1, StoreStore, Store2:Store1 的数据必须先于 Store2 被其他 CPU 看到 | 类似写屏障 |
| **LoadStore** | Load1, LoadStore, Store2:Store2 写出的数据被看到之前,Load1 必须先读完 | — |
| **StoreLoad** | Store1, StoreLoad, Load2:Store1 写出的数据被其他 CPU 看到后,才能执行 Load2;若读写同一地址,Load2 不能读 StoreBuffer 里的旧值 | 最强的屏障,能实现其他所有屏障的功能 |

> volatile 的实现本质:写 volatile 变量前插入 **StoreStore**,后插入 **StoreLoad**;读 volatile 变量前插入 **LoadLoad**,后插入 **LoadStore**——从而保证可见性和有序性。

---

## 五、Happens-before 原则(先行发生原则)

如果操作 A happens-before 操作 B,那么 A 的结果对 B **可见**,且 A 的执行顺序在 B 之前。8 条规则:

| 规则 | 内容 |
|------|------|
| 1. 程序次序规则 | 一个线程内,书写在前的操作先行发生于书写在后的操作(仅单线程有保障;多线程无法保障次序) |
| 2. 锁定规则 | 对同一把锁:unlock 先行发生于后面的 lock |
| 3. volatile 变量规则 | 对一个 volatile 变量的**写**先行发生于后面对它的**读** |
| 4. 传递规则 | A → B,B → C,则 A → C |
| 5. 线程启动规则 | Thread.start() 先行发生于该线程的每一个动作 |
| 6. 线程中断规则 | interrupt() 的调用先行发生于被中断线程检测到中断事件 |
| 7. 线程终结规则 | 线程中所有操作先行发生于线程终止检测(join() 返回 / isAlive() == false) |
| 8. 对象终结规则 | 对象初始化完成先行发生于它的 finalize() 开始 |

---

## 六、小结

- volatile 保**可见性**:写会强制刷主存(配合屏障),读会强制从主存拿最新;
- volatile 保**有序性**:volatile **写不往前排,读不往后排**;
- volatile 不保**原子性**:复合操作(如 i++)仍需 synchronized / Atomic 类 / Lock;
- 经典搭配:**volatile + CAS = 无锁并发**(如 AtomicInteger 内部就是 volatile value + CAS)。
