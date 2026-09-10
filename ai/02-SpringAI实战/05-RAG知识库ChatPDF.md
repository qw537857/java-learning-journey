# RAG 实战:知识库 ChatPDF(2026-09-10)

> 来源:AI 课程导图(05 SpringAI)整理,面向 Java 初级工程师。
> 目标:搞懂向量与向量库,并用 SpringAI 实现一个"上传 PDF → 针对 PDF 提问"的知识库应用(ChatPDF)。

---

## 一、RAG 原理回顾

大模型有**知识限制**:训练数据滞后(几个月前)、缺少专业/企业私有数据。

解决思路:给大模型**外挂知识库**。但知识库**不能直接拼进提示词**——上下文有大小限制(早期 GPT 2000 token,现在也不到 200K token)。

**办法:从庞大知识库里只挑"与问题相关的一小部分",拼成提示词。**
怎么找相关内容?全文检索不行(只做文字匹配),我们要求**内容相似度** → 这就用到**向量**。

### 1.1 向量模型

- **向量**:空间中带方向和长度的量,可以是二维也可以多维;
- 两个向量能算距离:**欧氏距离越小越相似;余弦距离越大越相似**;
- **向量模型**把文本转成向量,好的模型能保证:含义相似的文本,向量在空间中更近。

### 1.2 配置向量模型(以百炼为例)

百炼的 `text-embedding-v4`(兼容 OpenAI):

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: qwen-max
      embedding:
        options:
          model: text-embedding-v4
          dimensions: 1024
```

### 1.3 测试:向量到底准不准

写个工具类算距离(欧氏 + 余弦),再用测试验证:

```java
public class VectorDistanceUtils {
    private static final double EPSILON = 1e-12;   // 浮点相等判断阈值

    /** 欧氏距离:越小越相似 */
    public static double euclideanDistance(float[] a, float[] b) {
        validateVectors(a, b);
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /** 余弦距离:越大越相似(取值范围 [-1,1]) */
    public static double cosineDistance(float[] a, float[] b) {
        validateVectors(a, b);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        na = Math.sqrt(na); nb = Math.sqrt(nb);
        if (na < EPSILON || nb < EPSILON) throw new IllegalArgumentException("zero vector");
        double sim = dot / (na * nb);
        return Math.max(Math.min(sim, 1.0), -1.0);
    }

    private static void validateVectors(float[] a, float[] b) {
        if (a == null || b == null) throw new IllegalArgumentException("null");
        if (a.length != b.length) throw new IllegalArgumentException("dimension");
        if (a.length == 0) throw new IllegalArgumentException("empty");
    }
}
```

```java
@SpringBootTest
class EmbeddingTests {
    @Autowired private OpenAiEmbeddingModel embeddingModel;

    @Test
    public void testEmbedding() {
        String query = "global conflicts";
        String[] texts = { "某地区停火谈判仍在进行", "某国外交谈判继续", "水井检测出污染物超标", "体育场馆恢复运营", "空间站开展实验" };

        float[] qv = embeddingModel.embed(query);
        List<float[]> tvs = embeddingModel.embed(Arrays.asList(texts));

        for (float[] tv : tvs) {
            System.out.println(VectorDistanceUtils.euclideanDistance(qv, tv));
        }
        System.out.println("------------------");
        for (float[] tv : tvs) {
            System.out.println(VectorDistanceUtils.cosineDistance(qv, tv));
        }
    }
}
```

结果符合预期:"global conflicts" 与国际新闻的距离明显更近。

> 💡 EPSILON:浮点数(二进制)有精度缺陷,**不能直接用 `==` 比较**小数,要用极小阈值判断。

### 1.4 向量数据库

向量模型负责"生成向量",但**存储和检索**要靠**向量数据库**,作用是:① 存向量数据 ② 基于相似度检索。

SpringAI 支持很多向量库(Milvus、Redis、PGVector、Elasticsearch、Qdrant…),都实现统一接口 **`VectorStore`**,会一个就会全部;另外还有个内存版 **`SimpleVectorStore`**,专门用于测试/教学。

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-advisors-vector-store</artifactId>
</dependency>
```

```java
@Bean
public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
}
```

`VectorStore` 接口:

```java
void add(List<Document> documents);                    // 保存文档(向量化)
void delete(List<String> idList);                      // 删除
List<Document> similaritySearch(String query);         // 相似度检索
List<Document> similaritySearch(SearchRequest request);
```

> 注意:**VectorStore 的基本单位是 `Document`**,知识库文件要先拆分成 Document 再写入。

### 1.5 文件读取与转换

SpringAI 提供 ETL 工具,PDF 读取有两种拆分方式:

| Reader | 拆分方式 | 建议 |
|--------|----------|------|
| `PagePdfDocumentReader` | 按页拆分 | ✅ 推荐 |
| `ParagraphPdfDocumentReader` | 按目录拆分 | ❌ 不推荐(很多 PDF 没章节标签) |

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>
```

```java
// 读取 PDF → 拆成 Document → 写入向量库 → 检索测试
PagePdfDocumentReader reader = new PagePdfDocumentReader(
        new FileSystemResource("your-file.pdf"),
        PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(1)      // 每 1 页作为一个 Document
                .build());

List<Document> documents = reader.read();
vectorStore.add(documents);

List<Document> docs = vectorStore.similaritySearch(
        SearchRequest.builder()
                .query("get 和 post 请求的区别")
                .topK(1)                     // 取最相关的 1 条
                .similarityThreshold(0.6)    // 相似度阈值
                .filterExpression("file_name == 'your-file.pdf'")
                .build());
```

### 1.6 RAG 三阶段总结(背下来)

```
第一阶段(存储):知识库切片 → 每片向量化 → 写入向量数据库
第二阶段(检索):用户问题向量化 → 去向量库检索最相关片段
第三阶段(对话):检索片段 + 用户问题 → 拼成提示词 → 发给大模型得到回答
```

---

## 二、实战:ChatPDF

目标:用户上传 PDF,AI 基于 PDF 内容回答问题(个人知识库应用)。

### 2.1 上传/下载 PDF

**上传要做**:校验是否 PDF → 保存文件(本地/OSS)→ 记录 chatId 与文件路径映射 → 拆分并向量化。
**下载要做**:读文件 → 返回给前端(带文件名)。

```java
public interface FileRepository {
    boolean save(String chatId, Resource resource);   // 保存文件 + 记录映射
    Resource getFile(String chatId);
}
```

实现类 `LocalPdfFileRepository`:

- `save()`:把文件复制到本地,并 `chatFiles.put(chatId, filename)`;
- 用 `@PostConstruct` 启动时加载(`chat-pdf.properties` 会话映射 + `chat-pdf.json` 向量库)、`@PreDestroy` 停机时持久化;
- 💡 用 Redis 等真正的向量库就不需要自己持久化 VectorStore,但 **chatId ↔ 文件 的映射仍需自己维护**。

```java
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {

    private final FileRepository fileRepository;
    private final VectorStore vectorStore;

    /** 上传 */
    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            if (!Objects.equals(file.getContentType(), "application/pdf")) {
                return Result.fail("只能上传 PDF 文件!");
            }
            if (!fileRepository.save(chatId, file.getResource())) {
                return Result.fail("保存文件失败!");
            }
            writeToVectorStore(file.getResource());   // 拆分 + 向量化
            return Result.ok();
        } catch (Exception e) {
            log.error("upload failed", e);
            return Result.fail("上传文件失败!");
        }
    }

    /** 下载 */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable String chatId) throws IOException {
        Resource resource = fileRepository.getFile(chatId);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String filename = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    private void writeToVectorStore(Resource resource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1)
                        .build());
        vectorStore.add(reader.read());
    }
}
```

**两个配套配置**:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB        # 默认只有 1MB,知识库文件常超
      max-request-size: 30MB
```

- CORS 要 `exposedHeaders("Content-Disposition")`,否则前端拿不到下载文件名。

### 2.2 配置 RAG 的 ChatClient

RAG 的完整流程(问题向量化 → 检索 → 拼提示词 → 调用)SpringAI 用一个 **`QuestionAnswerAdvisor`** 全包了:

```java
@Bean
public ChatClient pdfChatClient(OpenAiChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
    return ChatClient.builder(model)
            .defaultSystem("请根据提供的上下文回答问题,不要自己猜测。")
            .defaultAdvisors(
                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
                    new SimpleLoggerAdvisor(),
                    QuestionAnswerAdvisor.builder(vectorStore)
                            .searchRequest(SearchRequest.builder()
                                    .similarityThreshold(0.5d)
                                    .topK(2)
                                    .build())
                            .build())
            .build();
}
```

### 2.3 对话接口(带文件过滤)

```java
@RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
public Flux<String> chat(String prompt, String chatId) {
    Resource file = fileRepository.getFile(chatId);
    Flux<String> content = pdfChatClient.prompt(prompt)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
            .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION,
                    "file_name == '" + file.getFilename() + "'"))   // 只检索这个文件
            .stream()
            .content();
    chatHistoryRepository.save("pdf", chatId);
    return content;
}
```

---

## 三、本节速记

1. RAG 三步:**切片向量化入库 → 问题向量化检索 → 拼提示词生成**;
2. 向量相似度:欧氏距离越小越像,余弦距离越大越像;浮点比较用 EPSILON 阈值;
3. 向量库统一接口 `VectorStore`;教学用 `SimpleVectorStore`,生产用 Redis/Milvus/PGVector 等;
4. PDF 读取推荐 `PagePdfDocumentReader`(按页拆);上传注意 1MB 默认限制;
5. `QuestionAnswerAdvisor` 一句话接入 RAG,`FILTER_EXPRESSION` 可按文件过滤。
