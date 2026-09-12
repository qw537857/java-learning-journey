# Redis 会话记忆与会话详情(2026-09-12)

> 来源:AI 课程导图(10 基本对话与课程咨询)整理,面向 Java 初级工程师。
> 目标:自定义 Redis 版 `ChatMemoryRepository`(含 **Message 序列化这个大坑**),并实现"查询会话详情"接口。

---

## 一、为什么自定义会话记忆

- SpringAI 官方**没有** Redis 存储实现(只有内存版),要持久化就自己写;
- 实现接口:`org.springframework.ai.chat.memory.ChatMemoryRepository`:

```java
public interface ChatMemoryRepository {
    List<String> findConversationIds();                          // 所有对话 id
    List<Message> findByConversationId(String conversationId);   // 按对话 id 查消息
    void saveAll(String conversationId, List<Message> messages); // 覆盖保存
    void deleteByConversationId(String conversationId);          // 删除
}
```

- 数据结构选 **Redis List**:天然有序,适合聊天记录(按顺序 push/range)。

---

## 二、实现 RedisChatMemoryRepository

```java
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    public static final String DEFAULT_PREFIX = "CHAT:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = stringRedisTemplate.keys(DEFAULT_PREFIX + "*");
        return ListUtil.toList(keys);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "消息列表不能为空");
        String redisKey = getKey(conversationId);
        BoundListOperations<String, String> listOps = stringRedisTemplate.boundListOps(redisKey);
        // ⚠️ 传进来的是"全量消息",所以要先删再整体写入,避免重复
        deleteByConversationId(conversationId);
        messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        BoundListOperations<String, String> listOps = stringRedisTemplate.boundListOps(getKey(conversationId));
        return CollStreamUtil.toList(listOps.range(0, -1), MessageUtil::toMessage);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        stringRedisTemplate.delete(getKey(conversationId));
    }

    private String getKey(String conversationId) { return DEFAULT_PREFIX + conversationId; }
}
```

### 2.1 装配进 ChatClient

```java
@Configuration
public class SpringAIConfig {

    @Value("${demo.ai.memory.max:100}")
    private Integer maxMessages;

    @Bean
    public ChatClient chatClient(DashScopeChatModel model, ChatMemory redisChatMemory) {
        return ChatClient.builder(model)
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().build(),
                        MessageChatMemoryAdvisor.builder(redisChatMemory).build())   // 会话记忆
                .build();
    }

    @Bean
    public ChatMemoryRepository redisChatMemoryRepository() {
        return new RedisChatMemoryRepository();
    }

    @Bean
    public ChatMemory redisChatMemory(ChatMemoryRepository repo) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repo)
                .maxMessages(maxMessages)     // 最多 100 条,超了自动丢最旧的
                .build();
    }
}
```

> `maxMessages` 很重要:不限制的话上下文会越滚越大,既费 token 又可能超模型上限。

### 2.2 用 conversationId 隔离会话

会话记忆按 **conversationId** 隔离,本项目规则:**`用户id_会话id`**:

```java
public interface ChatService {
    /** 对话 id 规则:用户id_会话id */
    static String getConversationId(String sessionId) {
        return UserContext.getUser() + "_" + sessionId;
    }
}
```

```java
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))   // 发请求时带上
```

> 💡 `sessionId` 是"业务会话",`conversationId` 是"记忆键";**加用户 id 前缀**可防止不同用户撞 sessionId。

---

## 三、大坑:Message 序列化

### 3.1 两个诡异现象

1. Redis 里**只剩一条**大模型回复,用户提问没了;
2. 存进去了,但**没有文本内容**。

### 3.2 原因

| 现象 | 原因 |
|------|------|
| 只剩一条 | `findByConversationId` 当时没实现 → 每次 `saveAll` 前删除,但读不回来,导致"覆盖式"只剩一条 |
| 没有文本 | SpringAI 的 `Message` 实现里 `textContent` **没有 getter**(只有 `getText()`),直接 JSON 序列化取不到值 |

### 3.3 解决:自定义可序列化的消息类

思路:**不直接序列化 SpringAI 的 Message,而是自己定义一个"搬运工"类**。

```java
@Data
public class MyMessage {
    private String messageType;                                   // 区分消息类型
    private Map<String, Object> metadata = Map.of();
    private List<Media> media = List.of();
    private List<AssistantMessage.ToolCall> toolCalls = List.of();
    private String textContent;                                   // 文本内容
    private List<ToolResponseMessage.ToolResponse> toolResponses = List.of();
    private Map<String, Object> params = Map.of();                // 额外参数(卡片数据,后面用)
}
```

```java
public class MessageUtil {

    /** Message → JSON 字符串(存 Redis) */
    public static String toJson(Message message) {
        MyMessage my = BeanUtil.toBean(message, MyMessage.class);
        my.setTextContent(message.getText());                     // 手动补文字
        if (message instanceof AssistantMessage am) {
            my.setToolCalls(am.getToolCalls());
        }
        if (message instanceof ToolResponseMessage trm) {
            my.setToolResponses(trm.getResponses());
        }
        return JSONUtil.toJsonStr(my);
    }

    /** JSON 字符串 → Message(从 Redis 读) */
    public static Message toMessage(String json) {
        MyMessage my = JSONUtil.toBean(json, MyMessage.class);
        return switch (MessageType.valueOf(my.getMessageType())) {
            case SYSTEM -> new SystemMessage(my.getTextContent());
            case USER -> UserMessage.builder()
                    .text(my.getTextContent()).metadata(my.getMetadata()).media(my.getMedia()).build();
            case ASSISTANT -> new AssistantMessage(my.getTextContent(), my.getMetadata(), my.getToolCalls());
            case TOOL -> new ToolResponseMessage(my.getToolResponses(), my.getMetadata());
        };
    }
}
```

> 记住这个套路:**第三方类不能序列化 → 自己写个镜像类,来回搬运**。这是处理"框架对象要持久化"的通用解法。

---

## 四、查询会话详情

### 4.1 接口

```
GET /session/{sessionId}
```

```json
[ { "type": "USER", "content": "你好" },
  { "type": "ASSISTANT", "content": "你好!我是你的 AI 学习助理…" } ]
```

### 4.2 实现

```java
@Override
public List<MessageVO> queryBySessionId(String sessionId) {
    String conversationId = ChatService.getConversationId(sessionId);
    List<Message> messages = redisChatMemory.get(conversationId);
    // 过滤:只要用户和助手的消息(系统提示词、工具消息不给前端)
    return messages.stream()
            .filter(m -> m.getMessageType() == MessageType.ASSISTANT
                      || m.getMessageType() == MessageType.USER)
            .map(m -> MessageVO.builder()
                    .type(MessageTypeEnum.valueOf(m.getMessageType().name()))
                    .content(m.getText())
                    .build())
            .toList();
}
```

```java
@GetMapping("/{sessionId}")
public List<MessageVO> queryBySessionId(@PathVariable String sessionId) {
    return chatSessionService.queryBySessionId(sessionId);
}
```

> 关键点:**前端只该看到 USER / ASSISTANT**,系统消息和工具消息是内部实现,要过滤掉。

---

## 五、本节速记

1. 会话记忆要持久化 → 自己实现 `ChatMemoryRepository`(Redis 用 List 存);
2. `saveAll` 是**全量覆盖**,先删后写;`maxMessages` 防止上下文无限膨胀;
3. conversationId 用 `用户id_会话id`,避免不同用户撞车;
4. 坑:SpringAI `Message` 无 getter、不可直接序列化 → 自定义镜像类 `MyMessage` + `MessageUtil` 转换;
5. 查询历史要**过滤 SYSTEM/TOOL 消息**,只返回 USER/ASSISTANT。
