# Bug 复盘:停止生成后没有保存记录(2026-09-12)

> 来源:AI 课程导图(10 基本对话与课程咨询)整理,面向 Java 初级工程师。
> 目标:复现并解决"停止生成 + 会话记忆"两个功能**单独正常、合起来就丢记录**的问题。

---

## 一、Bug 现象

**单独测**都正常:停止生成能停、会话记忆能存。
**合起来测**就有问题:

```
1. 正常对话 → Redis 里能看到:用户提问 + AI 回答
2. 中途点"停止生成" → 流断了,但 Redis 里【没有】AI 已生成的那段内容
```

结果:用户重新打开这个会话,发现**自己的问题在、AI 的回答没了**——历史记录断片。

---

## 二、定位过程(利用断点)

```
1. 在 RedisChatMemoryRepository#saveAll 打断点
   → 第一次进入:保存"用户提问" ✅
2. 放行,在 ChatServiceImpl#chat 打断点
   → 大模型开始返回数据 ✅
3. 点"停止生成"
   → 数据停止输出,Redis 也没有新数据 ❌
```

---

## 三、根因

```
"停止生成" = 中断 Flux 流
        ↓
Flux 被中断 → SpringAI 不会触发 ChatMemory 的 add()
        ↓
也就不会调用 ChatMemoryRepository#saveAll()
        ↓
→ 已生成但没存的内容,丢了
```

> 换句话说:**SpringAI 只在"流正常跑完"时才把消息写进记忆**;流被截断,它就不写了。
> (这是框架的设计/版本行为,别纠结"为什么",重点是知道**中断路径要自己兜底**。)

---

## 四、解决:在 `doOnCancel` 里自己保存

关键 API:**Flux 的 `doOnCancel`** —— 流被取消时触发。

```java
@Override
public Flux<ChatEventVO> chat(String question, String sessionId) {
    String conversationId = ChatService.getConversationId(sessionId);
    StringBuilder outputBuilder = new StringBuilder();      // ★ 缓存已生成内容

    return chatClient.prompt()
            .system(s -> s.text(systemPromptConfig.getChatSystemMessage().get())
                          .param("now", DateUtil.now()))
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(question)
            .stream()
            .chatResponse()
            .doFirst(() -> GENERATE_STATUS.put(sessionId, true))
            .doOnError(t -> GENERATE_STATUS.remove(sessionId))
            .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))
            .doOnCancel(() -> {                              // ★ 流被取消 → 手动补存
                saveStopHistoryRecord(conversationId, outputBuilder.toString());
            })
            .takeWhile(r -> GENERATE_STATUS.getOrDefault(sessionId, false))
            .map(r -> {
                String text = r.getResult().getOutput().getText();
                outputBuilder.append(text);                  // ★ 边输出边缓存
                return ChatEventVO.builder()
                        .eventData(text).eventType(ChatEventTypeEnum.DATA.getValue()).build();
            })
            .concatWith(Flux.just(STOP_EVENT));
}

/** 停止生成时,把已输出的内容存进会话记忆 */
private void saveStopHistoryRecord(String conversationId, String content) {
    redisChatMemory.add(conversationId, new AssistantMessage(content));
}
```

### ⚠️ 一个致命顺序问题

```
doOnCancel 一定要写在 takeWhile 【上面(之前)】
```

原因:算子顺序 = 数据流经顺序。如果 `doOnCancel` 在 `takeWhile` 之后,
`takeWhile` 先终止了流,后面的 `doOnCancel` **根本不会被执行**。

```java
// ❌ 错误:doOnCancel 在后面,不生效
.takeWhile(...)
.doOnCancel(...)

// ✅ 正确:doOnCancel 在前面
.doOnCancel(...)
.takeWhile(...)
```

---

## 五、验证

按"Bug 重现"步骤再走一遍:

```
1. 正常聊天 → Redis 有记录 ✅
2. 中途停止 → Redis 里出现【已生成的部分内容】✅
3. 重新打开会话 → 用户问题 + AI 部分回答都在 ✅
```

---

## 六、这个 Bug 教给我们什么

| 经验 | 说明 |
|------|------|
| **框架只处理"正常路径"** | 异常/取消等边界路径,框架常不兜底,要自己补 |
| **认准生命周期回调** | Flux 的 `doOnCancel`/`doOnError`/`doFinally` 是兜底三兄弟 |
| **算子的顺序就是执行顺序** | `doOnCancel` 放错位置直接失效,这类坑调试时最费时间 |
| **缓存已产出内容** | 流式场景下,想"事后补存"就必须边输出边缓存(`StringBuilder`) |
| **测组合,不要只测单点** | 两个功能各自没问题 ≠ 组合没问题,集成测试必须覆盖交叉场景 |

---

## 七、本节速记

1. 现象:停止生成后,AI 已输出的内容**没进会话记忆**;
2. 根因:流被中断 → SpringAI 不触发保存(`saveAll` 不被调用);
3. 解法:`doOnCancel` 中**手动调用** `chatMemory.add(...)` 补存;
4. 关键细节:`doOnCancel` 必须放在 `takeWhile` **之前**,否则不执行;
5. 通用经验:**流式 + 可中断场景,永远准备一个"兜底保存"**。
