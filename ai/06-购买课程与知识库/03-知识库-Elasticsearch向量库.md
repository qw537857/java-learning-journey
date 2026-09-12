# 知识库:用 Elasticsearch 做向量库(2026-09-12)

> 来源:AI 课程导图(11 购买课程与知识库)整理,面向 Java 初级工程师。
> 目标:把 RAG 的"内存向量库"升级为 **Elasticsearch**,完成部署 → 集成 → 写数据 → 接入对话。

---

## 一、为什么要知识库

课程推荐的前提是"**先从知识库匹配到课程,再按课程 id 查详情推荐**"。

- 之前学 RAG 用的是**内存向量库**(SimpleVectorStore),重启即丢,**项目里不可能用**;
- 生产方案:用 **Elasticsearch** 作为向量库存储(项目里常用、运维成熟)。

---

## 二、RAG 原理(复习 + 对照官方流程)

SpringAI 官方把 RAG 拆成两条流程:

**① 文档摄取(ETL,离线)**

```
读取文档 → 分割成块(chunks) → 转换(向量化/加元数据) → 写入向量库
```

**② 检索增强生成(在线)**

```
用户提问 → 向量检索最相关块 → 与问题拼成增强提示词 → 交给大模型生成回答
```

> 一句话:**离线把知识"灌"进向量库,在线只检索相关片段再问模型**——既绕开上下文限制,又让回答有据可依。

---

## 三、部署 Elasticsearch(向量库)

> 项目里原有的 ES 是给"课程搜索"用的、版本较老,所以**另起一个 ES 专门做向量库**。

```bash
docker run -d \
  --name es2 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -v es2-data:/usr/share/elasticsearch/data \
  -v es2-plugins:/usr/share/elasticsearch/plugins \
  --privileged \
  --network es2-net \
  --restart=always \
  -p 19200:9200 \
  -p 19300:9300 \
  elasticsearch:8.13.4

# 容器已存在就先删再建
docker rm -f es2
# 清理挂载目录里的无用数据
docker volume prune
```

访问 `http://your-server-ip:19200/` 有返回 → 部署成功。

> 💡 学习环境用单节点 + 关闭安全认证;生产必须开认证、做集群与持久化规划。

---

## 四、项目集成 ES 向量库

### 4.1 依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-elasticsearch</artifactId>
    <exclusions>
        <!-- 排除自带版本,统一用下面显式版本 -->
        <exclusion>
            <groupId>co.elastic.clients</groupId>
            <artifactId>elasticsearch-java</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.15.5</version>
</dependency>
```

### 4.2 配置(放 Nacos)

```yaml
spring:
  elasticsearch:
    uris: http://your-server-ip:19200
  ai:
    dashscope:
      api-key: ${ALIYUN_API_KEY}
      chat:
        enabled: true
        options:
          model: qwen-max-latest
      embedding:
        enabled: true
        options:
          model: text-embedding-v4     # 向量模型
          dimensions: 1024
    vectorstore:
      elasticsearch:
        initialize-schema: true        # 自动初始化索引结构
        dimensions: 1024               # 必须与向量模型维度一致!
```

> ⚠️ `dimensions` 与 embedding 模型维度**必须一致**(这里是 1024),否则写入/检索报错。

### 4.3 部署 Kibana(方便看数据)

```bash
docker run -d --name kibana2 \
  -e ELASTICSEARCH_HOSTS=http://your-server-ip:19200 \
  -p 15601:5601 \
  kibana:8.13.4
```

访问 `http://your-server-ip:15601/app/dev_tools#/console`,常用命令:

```json
GET _cat/indices?h=index                    // 查看所有索引
GET /spring-ai-document-index               // 查看索引结构
GET /spring-ai-document-index/_search       // 搜索数据
DELETE /spring-ai-document-index            // 删除索引
```

> `spring-ai-document-index` 是 SpringAI 默认的索引名。

---

## 五、写入知识库数据

### 5.1 写入接口

```java
@Slf4j
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final VectorStore vectorStore;

    /** 批量写入:文本 → Document → 向量库 */
    @PostMapping
    public void saveVectorStore(@RequestParam("messages") List<String> messages) {
        log.info("保存到向量数据库,数量:{}", messages.size());
        List<Document> documents = CollStreamUtil.toList(messages,
                message -> Document.builder().text(message).build());
        vectorStore.add(documents);
        log.info("保存成功");
    }
}
```

> ⚠️ 实操提醒:**每次写入不要超过 3 条**(分批写),避免一次请求过大/超时。

### 5.2 配套查询/删除接口(练习)

```java
@GetMapping                                                          // 单个文本向量化
public EmbeddingResponse embed(@RequestParam("message") String message) {
    return embeddingModel.embedForResponse(List.of(message));
}

@DeleteMapping                                                       // 按 id 删除
public void deleteVectorStore(@RequestParam("ids") List<String> ids) {
    vectorStore.delete(ids);
}

@GetMapping("/search")                                               // 相似度检索
public List<Document> search(@RequestParam("message") String message) {
    return vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(5).build());
}

@GetMapping("/search/all")                                           // 查看全部
public List<Document> searchAll() {
    return vectorStore.similaritySearch(SearchRequest.builder().query("").topK(999).build());
}
```

> 有了这几个接口,增删查都能在系统里完成,不用再进 Kibana 手工操作。

---

## 六、接入对话(挂 RAG)

```java
// 创建 RAG 增强 Advisor:相似度阈值 0.6,取最相关 6 条
QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(SearchRequest.builder()
                .similarityThreshold(0.6d)
                .topK(6)
                .build())
        .build();

return chatClient.prompt()
        .system(s -> s.text(systemPromptConfig.getChatSystemMessage().get())
                      .param("now", DateUtil.now()))
        .advisors(a -> a.advisors(qaAdvisor)                          // ★ RAG
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
        .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId))
        .user(question)
        .stream()
        // ... 其余 doOnCancel/takeWhile/事件封装同上几篇
```

测试:"推荐课程,年龄 25、本科、没有编程基础、对 Java 感兴趣" → AI 基于知识库推荐课程,再追问可继续下单。

---

## 七、本节速记

1. 内存向量库只能教学,生产用 **ES / Milvus / Redis** 等;本篇用 ES;
2. RAG 两步走:**离线灌数据(ETL)** + **在线检索增强**;
3. 集成三步:**引依赖(注意排除冲突版本)→ 配 uris/dimensions → `initialize-schema` 自动建索引**;
4. `dimensions` 必须和向量模型一致;写入**分批**(每次 ≤3 条);
5. 对话挂 RAG 就一行:`QuestionAnswerAdvisor`(阈值 + topK);
6. Kibana 的 Dev Tools 是排查"数据到底进没进"的利器。
