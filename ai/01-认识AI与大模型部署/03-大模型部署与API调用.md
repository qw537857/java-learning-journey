# 大模型应用开发:模型部署与 API 调用(2026-09-08)

> 来源:AI 课程导图(03 大模型应用开发)整理,面向 Java 初级工程师。
> 目标:搞清楚"模型从哪来(云 API / 本地 Ollama)"和"怎么调大模型 API(OpenAI 兼容规范、角色、会话记忆)"。

---

## 〇、先立一个观念

**大模型应用开发 ≠ 在浏览器里跟 AI 聊天。**

开发 = 你的程序通过 HTTP 请求**访问模型对外暴露的 API 接口**,拿到返回结果再拼进自己的业务里。

所以第一步是:**企业先要有一个"可访问的大模型"**。

---

## 一、三种获取大模型的方式(对比记牢)

| 方式 | 优点 | 缺点 |
|------|------|------|
| **开放大模型 API**(DeepSeek/通义等官方或云平台) | 零部署维护成本,按调用收费 | 依赖平台稳定性;长期成本较高;数据在第三方,有隐私问题 |
| **云平台部署私有大模型**(一键部署) | 前期成本低、部署维护方便、网络延迟低 | 数据仍在第三方;长期成本高 |
| **本地(公司服务器)部署私有模型** | 数据完全自主、安全;不依赖外部环境;长期成本更低 | 初期部署成本高、维护困难 |

> ⚠️ **重点提醒**:这里"本地部署"指的是**公司自己的服务器**。
> 大模型要的算力很高,**个人电脑跑的都是阉割/蒸馏/量化版**,性能差;而现在各种平台都有**免费满血模型 API** 可用。所以:**不建议在自己电脑上部署**,除非你想做模型微调或测试。

---

## 二、方式一:开放大模型服务(以阿里云百炼为例)

### 2.1 基本概念:Token 计费

- 开放平台按调用消耗的 **token** 付费,**每百万 token 通常几毛 ~ 几元**;
- 平台一般会送新用户百万 token 免费额度;
- token 粗略理解:**1 个汉字 ≈ 2 个 token**(英文一个单词≈1 个 token)。

### 2.2 三步开通

```
1. 注册账号:注册阿里云账号,访问「百炼」平台并开通服务(首次赠送百万 token,含 DeepSeek-R1、qwen 等)
2. 申请 API-KEY:右上角用户头像 → API-KEY → 创建我的 API-KEY
   ⚠️ API-KEY 是访问凭证,一定保密,不能泄露(后面所有请求都要带它)
3. 体验模型:模型广场 → 选模型 → 「API 调用示例」进文档 → 「立即体验」进试验台模拟调用
```

> 学习期强烈建议先在**控制台试验台**把请求参数调通,再写代码。

---

## 三、方式二:本地部署(Ollama)

Ollama = 帮你**部署和运行大模型的工具**,类似 Docker(命令都很像),官网按系统下载安装包。

### 3.1 安装与配置(不想装 C 盘必看)

```bash
# 默认双击安装会装到 C 盘用户目录;想装别的盘用命令行指定目录:
mkdir your-ollama-dir
OllamaSetup.exe /DIR=your-ollama-dir

# 配置模型存放目录的环境变量(避免模型全堆 C 盘):
# 新建系统环境变量:OLLAMA_MODELS=your-model-dir
```

如果模型已经下载到 C 盘想迁移(经典软链接方案):

```bash
# 1. 彻底退出 Ollama,管理员 CMD 停止服务
net stop ollama
# 2. 把原模型文件夹剪切到其他盘(如 D 盘)
move C:\Users\%USERNAME%\.ollama  your-target-dir\.ollama
# 3. 建系统软链接,让程序以为还在原位置
mklink /D C:\Users\%USERNAME%\.ollama  your-target-dir\.ollama
# 4. 重启服务
net start ollama
```

### 3.2 搜索与选择模型

- Ollama 官网(或 `ollama list`)可搜索国内外常见模型,**热度第一通常是 deepseek-r1**;
- 模型有很多版本,**参数大小越大,推理能力越强、要求的算力越高**:如 7b/8b 可在家用机流畅跑,671b 是满血版;
- Ollama 提供的多是**量化压缩版**(比蒸馏版更小、对显卡要求更低)。

> 参考配置:32G 内存 + 6G 显存的机器,选 7b 或 8b 都能流畅运行。

### 3.3 运行模型

```bash
ollama run deepseek-r1:7b
```

- **首次运行会自动下载模型**,根据大小约 5 分钟 ~ 1 小时,耐心等待;
- 下载完就进入对话界面,和 ChatGPT 类似(具备会话记忆)。

### 3.4 Ollama 常用命令(记这些就够)

```bash
ollama serve    # 启动服务
ollama run      # 运行模型(进入对话)
ollama pull     # 拉取模型
ollama list     # 列出已下载模型
ollama ps       # 列出正在运行的模型
ollama stop     # 停止模型
ollama rm       # 删除模型
ollama show     # 查看模型信息
ollama help     # 帮助
```

### 3.5 常见问题

| 问题 | 处理 |
|------|------|
| 报错 0xc0000409 | 换用同学已下载好的模型文件(通过设置改模型位置),或**更新显卡驱动**(英伟达官网下载开发版驱动) |
| 屏幕一闪一闪 | 等 1 小时左右,一般自动恢复(下载/加载模型时占用高) |

---

## 四、调用大模型:OpenAI 兼容接口规范

**目前大多数大模型都遵循 OpenAI 接口规范**(HTTP 协议 + JSON 参数),路径、参数、返回值大同小异,以各家官方文档为准。

### 4.1 几个关键要素

| 要素 | 说明 |
|------|------|
| 请求方式 | 通常是 **POST**,传 JSON |
| 请求路径(示例) | DeepSeek 官方:`https://api.deepseek.com`;阿里云百炼:`https://dashscope.aliyuncs.com/compatible-mode/v1`;本地 Ollama:`http://localhost:11434` |
| 安全校验 | 开放平台都要带 **API-KEY**;本地 Ollama 不需要 |
| 请求参数 | `model` 模型名、`messages` 消息数组、`stream` 是否流式、`temperature` 随机性 |

### 4.2 temperature:控制"随机性"

```
temperature 取值范围 [0, 2)
越小 → 输出越确定、越保守(适合事实问答)
越大 → 输出越天马行空(适合创意生成)
```

> ⚠️ 注意:**DeepSeek-R1 不支持 temperature 参数**。

### 4.3 messages 与三种角色

`messages` 是数组,每条消息含两个属性:

```json
{
  "role": "角色",
  "content": "消息内容(即提示词 Prompt,发给大模型的指令)"
}
```

| role | 含义 |
|------|------|
| **system** | 设定 AI 的行为模式(人设/规则),**影响最大、最重要** |
| **user** | 用户说的话 |
| **assistant** | AI 之前的回复(用于多轮) |

**System 消息示例**(这也是"不同 AI 产品回答你是谁各不相同"的原因——产品都在 user 提问前拼了一段自己的 system 设定):

```
system: 你是在线商城的智能客服,名字叫小助手。请以友好、热情的方式回答用户问题。
user:   你好
assistant: 你好,我是小助手 😊 请问有什么可以帮您?
```

### 4.4 会话记忆:大模型没有记忆

- 大模型本身**没有记忆**,每次 API 调用相互独立;
- AI 产品能记住上下文,是因为它把**每一轮 User、Assistant 消息都塞进 messages 数组一起发过去**——大模型基于"历史对话"续写,看起来就像有记忆。

```
第1次请求 messages = [system, user1]
第2次请求 messages = [system, user1, assistant1, user2]
第3次请求 messages = [system, user1, assistant1, user2, assistant2, user3]
... 依次累加,注意控制长度(上下文窗口有限)
```

### 4.5 动手调一次(Ollama 本地示例)

本地 Ollama 跑起来后,自带 HTTP 接口 `http://localhost:11434/api/chat`:

```json
POST http://localhost:11434/api/chat
{
  "model": "qwen3:1.7b",
  "messages": [
    { "role": "system", "content": "你是一位耐心、友好的编程学习助手。" },
    { "role": "user", "content": "你是谁" }
  ],
  "stream": false
}
```

> 云平台(如百炼)提供图形化试验台,网页里就能试;原理和上面的 JSON 一样。

---

## 五、本节速记

1. 大模型开发 = 调 API;模型来源三选一:**开放 API / 云私有 / 本地私有**;
2. 个人电脑别部署大模型,用免费满血 API 更香;Ollama 只适合本地测试/微调场景;
3. 接口规范 OpenAI 兼容:POST + JSON,`model/messages/stream/temperature` 是核心参数;
4. **system 消息定人设**(AI 产品差异的来源);**历史消息全量回传 = 会话记忆**;
5. API-KEY 是钥匙,永远保密;DeepSeek-R1 不支持 temperature。
