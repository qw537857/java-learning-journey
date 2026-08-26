# 全文检索:Elasticsearch 实战(倒排索引 + IK 分词 + 高亮)

> 适用场景:站内搜索、游记/文章/商品关键词检索。本文讲清楚"为什么不用 MySQL LIKE、ES 靠什么快、怎么写一个带高亮的搜索",最后给可运行示例。

## 1. 是什么:为什么 MySQL 的 LIKE 撑不住搜索

初级同学第一反应:搜索不就是 `WHERE title LIKE '%关键词%'` 吗?

三个致命问题:

1. **不走索引**。`%关键词%` 这种前后都带通配符的写法,MySQL 索引直接失效,数据量一上来就是全表扫描。
2. **不能分词**。搜"大连旅游攻略",匹配的是完整字符串;搜"大连攻略"就什么都匹配不到,因为它不会把句子拆成词。
3. **没有相关性排序**。匹配到的结果谁排前面?LIKE 做不到按"匹配度"排序,只能按时间或 ID。

Elasticsearch(简称 ES)就是专门干这个的:**分词 + 倒排索引 + 相关性打分**。

## 2. 为什么快:倒排索引(大白话版)

MySQL 的索引是"正排":**文档 → 词**。查"大连"要扫所有文档,看哪个文档里有"大连"。

ES 的倒排索引反过来:**词 → 文档**。

```
文档1: 大连旅游攻略
文档2: 大连美食推荐
文档3: 上海旅游攻略

倒排索引:
  大连   -> [文档1, 文档2]
  旅游   -> [文档1, 文档3]
  攻略   -> [文档1, 文档3]
  美食   -> [文档2]
  推荐   -> [文档2]
```

搜索"大连"时,直接查倒排表拿到 `[文档1, 文档2]`,再按打分排序,毫秒级返回。这就是"倒排"的含义——**先建好"词→文档"的字典,查询就是查字典**。

## 3. 分词:IK 分词器(ik_max_word)

倒排表里的"词"哪来的?靠**分词器**把句子拆成词。

- 英文天然按空格分词。
- 中文没有空格,得靠词典分词。ES 默认的 standard 分词器会把中文按单个字切,效果很差,所以要装 **IK 分词器**。

IK 有两种粒度:

| 模式 | 效果 | 用途 |
|------|------|------|
| `ik_max_word` | 最细粒度拆分:"大连旅游攻略" → 大连/旅游/攻略 | 建索引(存更多词,召回更全) |
| `ik_smart` | 粗粒度,只拆最合理的词 | 搜索时可选用 |

> 记忆点:**建索引用 max(多存词),查询一般也能用 max**,保证能匹配上。

## 4. 核心查询:multiMatch 多字段匹配 + 高亮

实际项目里的经典写法(ES 8.x Java Client,`co.elastic.clients`):

```java
// 搜索"关键词",在 title、summary 两个字段里匹配,并用 ik_max_word 分词
SearchResponse<NoteEs> resp = client.search(sh -> sh
        .index("note")                              // 索引名
        .from((currentPage - 1) * pageSize)          // 分页:从第几条开始
        .size(pageSize)                              // 每页几条
        .query(q -> q.multiMatch(                    // 多字段匹配
                m -> m.query(keyword)                // 搜索词
                        .fields(Arrays.asList("title", "summary"))
                        .analyzer("ik_max_word")     // IK 分词
        ))
        .highlight(Highlight.of(h -> h
                .preTags("<span style='color:red'>") // 命中词前缀
                .postTags("</span>")                 // 命中词后缀
                .fields(Map.of(
                        "title", HighlightField.of(hf -> hf),
                        "summary", HighlightField.of(hf -> hf)
                ))
        )),
        NoteEs.class                                 // 结果映射类型
);
```

**multiMatch 和 match 的区别**:match 只匹配单字段,multiMatch 可以同时匹配多个字段并合并打分,适合"搜一个词、多个字段一起找"。

**高亮(highlight)**:ES 会把命中的词用你指定的标签包起来,前端拿到带 `<span style='color:red'>` 的内容,`v-html` 一渲染,关键词就红字了。

## 5. 经典套路:先查 ES 拿 ID,再回 MySQL 查原数据

很多新手把整个表数据都塞进 ES,结果要改索引结构、同步数据,维护成本极高。

**更稳的套路是:ES 只存检索需要的字段(冗余一份),搜出 ID 列表后,再按 ID 去 MySQL(或 Feign 调服务)查完整数据。**

```java
List<Long> ids = new ArrayList<>();
List<Hit<NoteEs>> hits = resp.hits().hits();
for (Hit<NoteEs> hit : hits) {
    ids.add(Long.valueOf(hit.id()));   // 1. 先拿 ID
}

// 2. 回 MySQL 查原数据(这里用 Feign 远程调用示例)
List<Note> mysqlList = ids.stream()
        .map(id -> remoteNoteService.getOne(id).getData())
        .collect(Collectors.toList());

// 3. 把高亮结果回填到原数据对象(高亮字段覆盖原字段)
for (Hit<NoteEs> hit : hits) {
    Map<String, List<String>> highlightMap = hit.highlight();
    for (String key : highlightMap.keySet()) {
        String value = String.join("", highlightMap.get(key));
        BeanUtils.setProperty(noteMap.get(Long.valueOf(hit.id())), key, value);
    }
}
```

这样:
- ES 里数据丢了/没同步,MySQL 兜底,最多是搜不到,不会崩;
- 展示用的完整字段(封面图、作者、点赞数)永远从 MySQL 拿最新值;
- 高亮字段通过反射 `BeanUtils.setProperty` 覆盖到原对象上,前端无感。

## 6. 完整可运行示例(Spring Boot 3 + ES 8)

### 依赖(pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
<!-- 或直接用官方 Java Client -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.13.4</version>
</dependency>
```

### 配置(application.yml)

```yaml
spring:
  elasticsearch:
    uris: http://your-es-host:9200   # 占位符,换成你的 ES 地址
```

### 实体(ES 文档对象,冗余字段)

```java
package com.example.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoteEs {
    private Long id;
    private String title;    // 参与检索的字段
    private String summary;  // 参与检索的字段
    private String coverUrl; // 展示字段
}
```

### 搜索服务(含高亮,完整版)

```java
package com.example.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.search.NoteEs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Autowired
    private ElasticsearchClient client;

    /**
     * 全文检索 + 高亮
     *
     * @param keyword  搜索关键词
     * @param page     页码(从 1 开始)
     * @param pageSize 每页条数
     */
    public List<NoteEs> search(String keyword, int page, int pageSize) throws IOException {
        // 1. 高亮配置:命中词用红字包裹
        Map<String, HighlightField> fieldMap = new HashMap<>();
        fieldMap.put("title", HighlightField.of(h -> h));
        fieldMap.put("summary", HighlightField.of(h -> h));

        Highlight highlight = Highlight.of(h -> h
                .preTags("<span style='color:red'>")
                .postTags("</span>")
                .fields(fieldMap));

        // 2. 查询:multiMatch + ik_max_word 分词 + 高亮 + 分页
        SearchResponse<NoteEs> resp = client.search(sh -> sh
                .index("note")
                .from((page - 1) * pageSize)
                .size(pageSize)
                .query(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields(Arrays.asList("title", "summary"))
                        .analyzer("ik_max_word")
                ))
                .highlight(highlight), NoteEs.class);

        // 3. 处理结果:高亮片段拼接回填(省略回 MySQL 查原数据的步骤)
        List<Hit<NoteEs>> hits = resp.hits().hits();
        return hits.stream().map(hit -> {
            NoteEs doc = hit.source();
            Map<String, List<String>> hMap = hit.highlight();
            if (doc != null && hMap != null) {
                for (String key : hMap.keySet()) {
                    String value = String.join("", hMap.get(key));
                    if ("title".equals(key)) doc.setTitle(value);
                    if ("summary".equals(key)) doc.setSummary(value);
                }
            }
            return doc;
        }).toList();
    }
}
```

### 同步数据进 ES(监听 MySQL binlog 或定时全量刷)

生产环境一般用 Canal 监听 binlog 增量同步(见仓库 canal 笔记),简单项目也可以用定时任务全量重建索引:

```java
@Component
public class NoteIndexBuilder implements ApplicationRunner {
    @Autowired
    private ElasticsearchClient client;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 示例:定时任务里全量重建索引
        // client.indices().delete(d -> d.index("note"));  // 删旧索引(谨慎)
        // 然后遍历 MySQL 数据,逐个 index 进 ES
        NoteEs doc = new NoteEs();
        doc.setId(1L);
        doc.setTitle("大连旅游攻略");
        doc.setSummary("三天两夜,带你玩转大连");
        client.index(i -> i.index("note").id("1").document(doc));
    }
}
```

## 7. 面试口径

**Q: 为什么搜索不用 MySQL 而用 ES?**
A: ① `LIKE '%词%'` 不走索引,大数据量全表扫描慢;② MySQL 不能分词,搜"大连旅游"匹配不到"大连攻略";③ 没有相关性打分排序。ES 用倒排索引 + 分词 + BM25 打分解决这三件事。

**Q: 倒排索引是什么?**
A: 正排是"文档→词",倒排是"词→文档列表"。建索引时把文档分词,记录每个词出现在哪些文档;查询时查词表直接拿文档列表,不用全表扫。

**Q: 中文搜索为什么要 IK 分词器?**
A: ES 默认分词器按单字切中文,效果差。IK 基于词典把中文切词,`ik_max_word` 最细粒度(召回全),`ik_smart` 粗粒度(更精准)。

**Q: 高亮是怎么实现的?**
A: 查询时指定 highlight 字段和 preTags/postTags,ES 把命中的词用标签包起来返回;后端把高亮片段回填到对象,前端 `v-html` 渲染成红字。

**Q: 搜索结果为什么还要回 MySQL 查一次?**
A: ES 只存检索冗余字段,完整数据以 MySQL 为准,保证最新、避免双写一致性问题;ES 挂了最多搜不到,不影响主流程。

## 8. 踩坑提醒

1. **前端渲染高亮必须用 `v-html`/`dangerouslySetInnerHTML`,但要注意 XSS**:高亮标签是后端拼的,如果内容里混入用户脚本,直接渲染有风险,生产环境建议白名单过滤或转义。
2. **`ik_max_word` 建索引、查询分词器不一致会导致搜不到**,建议查询也指定 `analyzer("ik_max_word")`。
3. **ES 8.x 默认开启 HTTPS + 安全认证**,本地玩要关闭 security 或配账号,`uris` 别配错。
4. **分页太深(from 很大)性能差**,超 1 万条用 search_after 游标。
5. **索引名全小写**,ES 不支持大写索引名。
