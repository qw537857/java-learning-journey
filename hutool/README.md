# HuTool 学习笔记 🛠️

> 面向初级工程师的 HuTool 工具库学习笔记:从依赖引入到高频核心类(字符串/集合/日期/Bean/转换/JSON),把课程导图整理成能"照着敲、照着复习"的笔记,重点标注坑点。
> 原则:只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 HuTool 入门与断言工具(依赖引入 + Assert)](./01-HuTool入门与断言工具.md)
- [x] [02 高频核心:基础工具类(IdUtil/StrUtil/EnumUtil/RandomUtil/NumberUtil)](./02-HuTool高频核心-基础工具类.md)
- [x] [03 高频核心:集合工具(CollStreamUtil/CollUtil/ListUtil)](./03-HuTool高频核心-集合工具.md)
- [x] [04 高频核心:日期与 Bean 转换(DateUtil/LocalDateTimeUtil/BeanUtil/Convert)](./04-HuTool高频核心-日期与Bean转换.md)
- [x] [05 数据处理与 Spring 集成(MapUtil/JSONUtil/SpringUtil)](./05-HuTool数据处理与Spring集成.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-05 | HuTool 入门 + 高频核心工具类 + 集合/日期/转换 + JSON 与 Spring 集成 | [01](./01-HuTool入门与断言工具.md) [02](./02-HuTool高频核心-基础工具类.md) [03](./03-HuTool高频核心-集合工具.md) [04](./04-HuTool高频核心-日期与Bean转换.md) [05](./05-HuTool数据处理与Spring集成.md) |

## 一句话速记

1. HuTool = Java 工具类库,静态方法开箱即用;**hutool-core 必引**,spring/json/crypto 等按需引;
2. 通用心法:自动处理 null 但**返回值可能为 null 要判空**;转换失败给默认值**不抛异常**;
3. 高频五类:IdUtil(唯一ID)、StrUtil(字符串)、CollUtil(集合)、DateUtil(日期)、JSONUtil(JSON);
4. 三个大坑:`NumberUtil.round` 返回 **BigDecimal**;`ListUtil.toList(单个null)` 得到 `[null]`;`getFirst` 对 HashSet 拿不到插入顺序首元素;
5. SpringUtil 在 hutool-spring 扩展包,别只引 core。
