# 01 RabbitMQ 实战:从原生客户端到 SpringBoot 集成

- **日期**:2026-08-21
- **一句话总结**:消息队列解决"解耦、异步、削峰"三大问题;本篇把 RabbitMQ 的核心概念、四种交换机、原生 Java 客户端、SpringBoot 集成一次讲透,全部带可运行代码。
- **配套代码**:原生客户端工程(纯 Java main 方法跑)与 SpringBoot 工程(发送端/接收端),见文末说明。

---

## 一、先搞清楚:RabbitMQ 是什么,为什么用它

**是什么**:一个消息队列(MQ)软件,用来在系统之间传递消息。生产者把消息扔进队列,消费者从队列里取。

**为什么用**(面试必问三件套):

| 问题 | 大白话 | 场景 |
|------|--------|------|
| **解耦** | 订单系统不用关心"谁"要处理订单数据。新增一个下游系统,上游代码不用改 | 下单后要通知库存、短信、积分……系统多了就乱 |
| **异步** | 用户下单,秒回"成功",不用等短信/邮件都发完 | 注册后发短信,同步发要等 1 秒,异步只要 1 毫秒 |
| **削峰** | 高并发瞬间,请求先堆到队列里,消费者按自己的速度慢慢处理,系统不被冲垮 | 秒杀、双十一,瞬间 10 万请求 |

> 面试口径:先背"解耦/异步/削峰",再各举一个自己项目里的例子。

---

## 二、核心概念(必须画得出来)

```
生产者 Producer ──> 交换机 Exchange ──(按路由键)──> 队列 Queue ──> 消费者 Consumer
                        ↑                      ↑
                     RoutingKey 路由键      Binding 绑定
```

| 概念 | 大白话 |
|------|--------|
| **生产者 / 消费者** | 发消息的 / 收消息的 |
| **队列 Queue** | 存消息的"信箱",消息真正待的地方 |
| **交换机 Exchange** | 消息的"中转站",决定消息发给哪些队列。**生产者只发给交换机,不直接发队列** |
| **路由键 RoutingKey** | 消息上贴的"标签",交换机根据它路由 |
| **绑定 Binding** | 队列和交换机之间的"约定":什么标签的消息进这个队列 |

**关键点**:消息不是直接进队列,而是先进交换机,交换机按"类型 + 路由键"决定投给哪个队列。

---

## 三、四种交换机类型(重点:direct / fanout / topic)

| 类型 | 路由规则 | 典型场景 |
|------|----------|---------|
| **Direct**(直连) | 路由键**完全相等**才投递 | 一对一精确路由,最常用 |
| **Fanout**(广播) | 不看路由键,**发给所有绑定队列** | 广播通知、群聊 |
| **Topic**(主题) | 路由键**模糊匹配**(`*` 匹配一个词,`#` 匹配零或多个) | 按业务分类订阅 |
| Headers(头部) | 按消息头匹配 | 很少用,了解即可 |

**Topic 匹配示例**:路由键 `order.create`、`order.pay`、`user.login`
- `order.*` → 匹配 order.create、order.pay,不匹配 order.create.a(一个 `*` 只算一个词)
- `order.#` → 匹配 order.create、order.create.a 等(order 下所有)

---

## 四、原生 Java 客户端(纯 main 方法,不依赖 Spring)

**核心五步**:创建连接工厂 → 建物理连接 Connection → 建虚拟信道 Channel → 声明队列/交换机/绑定 → 发消息。

发送端 `DirectSend.java`:

```java
package com.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class DirectSend {
    public static void main(String[] args) throws Exception {
        // 1. 连接工厂:IP、端口、账号
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("your-rabbitmq-host"); // 你的 RabbitMQ 地址
        factory.setPort(5672);                // 注意是 5672,不是管理台 15672!
        factory.setUsername("your-username");
        factory.setPassword("your-password");
        factory.setVirtualHost("/");

        // 2. 物理连接 + 3. 虚拟信道(所有操作都走 Channel)
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 4. 声明队列(queueDeclare):名称、持久化、排他、自动删除、参数
        String queueName = "directQueue";
        channel.queueDeclare(queueName, true, false, false, null);

        // 4. 声明交换机(exchangeDeclare):名称、类型(direct/fanout/topic)、持久化
        String exchangeName = "directExchange";
        channel.exchangeDeclare(exchangeName, "direct", true);

        // 4. 绑定:队列 + 交换机 + 路由键
        channel.queueBind(queueName, exchangeName, "directKey");

        // 5. 发消息:交换机、路由键、属性、内容(byte[])
        String message = "hello world";
        channel.basicPublish(exchangeName, "directKey", null, message.getBytes());
        System.out.println("已发送:" + message);

        // 先关 Channel,再关 Connection(先创建的后关闭)
        channel.close();
        connection.close();
    }
}
```

接收端 `DirectReceive.java`(核心差异在 `basicConsume`):

```java
package com.example;

import com.rabbitmq.client.*;

public class DirectReceive {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("your-rabbitmq-host");
        factory.setPort(5672);
        factory.setUsername("your-username");
        factory.setPassword("your-password");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String queueName = "directQueue";
        channel.queueDeclare(queueName, true, false, false, null);

        // 回调式消费:有消息来了自动执行 handleDelivery
        channel.basicConsume(queueName, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) {
                System.out.println("收到消息:" + new String(body));
            }
        });
        System.out.println("等待消息中...");
        // 保持程序不退出,手动 Ctrl+C 结束
    }
}
```

> 注意:接收端 main 方法跑完会退出,所以要用 `basicConsume` 注册回调 + 阻塞等待。

---

## 五、消息可靠性:Confirm 确认 / 事务(生产端)

默认生产者"扔出去就不管了",消息到底进没进队列不知道。两个方案:

| 方案 | 做法 | 特点 |
|------|------|------|
| **Confirm 模式**(推荐) | `channel.confirmSelect()` 开启,发送后 `waitForConfirms()` 等待 Broker 确认 | 性能好,主流 |
| **事务模式** | `channel.txSelect()` 开启,发送后 `txCommit()` 提交 | 性能差(每次都要刷盘),了解即可 |

```java
// Confirm 模式关键代码
channel.confirmSelect();
channel.basicPublish(exchangeName, routingKey, null, message.getBytes());
if (channel.waitForConfirms()) {
    System.out.println("消息确认已到达 Broker");
}
```

> 面试口径:生产端用 Confirm,消费端用手动 ACK(后面 SpringBoot 部分讲),两头都可靠。

---

## 六、SpringBoot 集成(重点,工作中 90% 用这个)

**依赖**(SpringBoot 发送/接收工程共用):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**1. 配置文件 application.yml**:

```yaml
spring:
  rabbitmq:
    addresses: your-rabbitmq-host1:5672,your-rabbitmq-host2:5672  # 逗号分隔支持多个
    username: your-username
    password: your-password
    virtual-host: /
```

**2. 声明队列 / 交换机 / 绑定(配置类)**:

```java
package com.example.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyRabbitConfig {

    public static final String QUEUE_NAME = "myQueue";
    public static final String EXCHANGE_NAME = "myExchange";

    // 队列:名称、持久化、独占、自动删除
    @Bean
    public Queue myQueue() {
        return new Queue(QUEUE_NAME, true, false, false);
    }

    // 交换机:名称、持久化、自动删除
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    // 绑定:队列、目标类型、交换机、路由键、参数
    @Bean
    public Binding myBinding() {
        return new Binding(QUEUE_NAME, Binding.DestinationType.QUEUE, EXCHANGE_NAME, "bootKey", null);
    }
}
```

**3. 发送端:直接注入 RabbitTemplate**:

```java
@Service
public class SendService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void send(String message) {
        // 参数:交换机、路由键、消息内容
        rabbitTemplate.convertAndSend(MyRabbitConfig.EXCHANGE_NAME, "bootKey", message);
        System.out.println("发送成功:" + message);
    }
}
```

**4. 接收端:一个注解搞定 @RabbitListener**:

```java
package com.example.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component   // 别忘了!监听器必须是 Spring 容器管理的 Bean
public class MyRabbitListener {

    @RabbitListener(queues = {MyRabbitConfig.QUEUE_NAME})
    public void onMessage(String message) {
        System.out.println("listener收到消息:" + message);
    }
}
```

> 启动接收端工程,再用发送端工程调用一次 send,控制台就能看到"listener收到消息:xxx"。

---

## 七、死信队列(DLX):消息的"垃圾回收站"

### 7.1 什么是死信,为什么需要它

**死信(Dead Letter)**:一条消息进了队列,但**永远无法被正常消费掉**(比如处理失败、过期了、队列满了),这种"判死刑"的消息就叫死信。

**死信队列(DLX,Dead Letter Exchange)**:专门收留死信的队列。正常队列可以配置"我这边处理不掉的消息,转给哪个交换机",那个交换机再把消息路由到死信队列里。

**大白话**:正常队列像个"待办箱",处理不了的单子不直接扔掉,而是扔进一个"废单箱",后面有人专门处理废单(排查、补偿、重试)。

**为什么要搞这么麻烦,直接丢掉不行吗?** 不行。很多消息丢不得:下单消息处理失败,直接丢=订单丢了。进死信队列后,可以:
- **排查问题**:看死信里的消息内容,定位为什么处理失败
- **补偿重放**:修好 bug 后,把死信重新投递回去
- **做延迟队列**(见第八节,最经典的用法)

### 7.2 消息什么时候会变成死信(三种来源,面试必答)

| 来源 | 触发条件 | 大白话 |
|------|----------|--------|
| **消息被拒绝** | 消费者 `basicReject` / `basicNack` 且 `requeue=false` | 消费者明确说"这条我不要了,也别放回队列" |
| **消息过期** | 队列设置了 TTL,消息在队列里待太久 | 消息在队列里"饿死"了 |
| **队列满了** | 队列设置了最大长度,新消息把队头的挤掉 | 待办箱满了,最早的单子被挤出去 |

> 面试口径:死信三来源 = **拒收(requeue=false)** + **TTL 过期** + **队列长度超限**。

### 7.3 配置示例:普通队列 + 死信交换机 + 死信队列(完整代码)

在 SpringBoot 工程里,用 `@Bean` 声明一套"普通队列 + 死信交换机 + 死信队列":

```java
package com.example.rabbitmq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 死信队列(DLX)配置:
 * 普通队列 normal.queue 处理不了的消息 -> dlx.exchange -> dlx.queue
 */
@Configuration
public class DlxConfig {

    // ---------- 普通队列:声明时挂上"死信参数" ----------
    @Bean
    public Queue normalQueue() {
        Map<String, Object> args = new HashMap<>();
        // 死信交换机:normal.queue 的死信都发到这里
        args.put("x-dead-letter-exchange", "dlx.exchange");
        // 死信路由键:死信发到 dlx.exchange 时用的路由键(用于匹配死信队列)
        args.put("x-dead-letter-routing-key", "dlx.key");
        return new Queue("normal.queue", true, false, false, args);
    }

    @Bean
    public DirectExchange normalExchange() {
        return new DirectExchange("normal.exchange");
    }

    @Bean
    public Binding normalBinding() {
        return BindingBuilder.bind(normalQueue()).to(normalExchange()).with("normal.key");
    }

    // ---------- 死信交换机 + 死信队列 ----------
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("dlx.exchange");
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue("dlx.queue", true);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with("dlx.key");
    }
}
```

**坑提前标**:死信参数是在**声明队列时**写死的。队列已经存在后再改参数(比如换死信交换机),启动会报 `PRECONDITION_FAILED`,需要先删掉旧队列再重启(管理台或 `rabbitmqctl delete_queue normal.queue`)。

### 7.4 演示:消费失败 -> 进死信

消费者监听普通队列,模拟处理失败并"拒收且不重回队列":

```java
package com.example.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NormalQueueConsumer {

    @RabbitListener(queues = "normal.queue")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String msg = new String(message.getBody());
        System.out.println("普通队列收到:" + msg + ",模拟处理失败...");

        // 第二个参数 multiple=false:只拒绝这一条
        // 第三个参数 requeue=false:不重回队列 -> 这条消息变成死信,被扔到 dlx.exchange
        channel.basicNack(tag, false, false);
    }
}
```

再监听死信队列,看消息是否"死后重生":

```java
package com.example.rabbitmq.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DlxQueueConsumer {

    @RabbitListener(queues = "dlx.queue")
    public void onMessage(String message) {
        System.out.println("死信队列收到(处理失败的那条在这里):" + message);
    }
}
```

> 发送一条消息到 `normal.exchange`(路由键 `normal.key`),控制台会依次打印:普通队列收到 → 死信队列收到。管理台里也能看到 `dlx.queue` 里堆积的消息。

---

## 八、延迟队列:TTL + 死信实现"定时任务"

### 8.1 什么是延迟队列,用在哪

**延迟队列**:消息发出去后**不立刻被消费**,等 N 秒/分钟后再给消费者。

**典型场景**(面试最爱问):

| 场景 | 大白话 |
|------|--------|
| 下单 30 分钟未支付自动关单 | 订单消息延迟 30 分钟,到点检查"付没付钱,没付就关单" |
| 超时未确认自动取消 | 预约/订座 15 分钟不确认,自动释放 |
| 定时通知 | 开播前 10 分钟提醒用户 |

> 不用延迟队列的土办法:定时任务轮询订单表("扫一遍,超时的关掉")。数据量大了就是全表扫描,延迟也不精确。延迟队列是"每条消息自带闹钟",到点精准触发。

### 8.2 RabbitMQ 没有现成的延迟队列,两种实现

| 方案 | 原理 | 适用 |
|------|------|------|
| **TTL + 死信(推荐,零依赖)** | 消息进普通队列时设 TTL,过期后变死信进死信队列,消费者只监听死信队列 | 所有消息延迟**相同时间**(比如统一 30 分钟) |
| **延迟插件**(rabbitmq_delayed_message_exchange) | 装官方插件,消息自带延迟时间 | 每条消息延迟**不同时间**,更灵活 |

### 8.3 TTL + 死信方案:完整可运行代码

复用 7.3 的配置,只要给普通队列加上 TTL 参数(改一处):

```java
// DlxConfig 里 normalQueue() 的参数追加一行:
args.put("x-message-ttl", 30000);   // 消息在队列里最多待 30 秒,到期变死信
```

完整版 normalQueue():

```java
@Bean
public Queue normalQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", "dlx.exchange");   // 到期后进哪个交换机
    args.put("x-dead-letter-routing-key", "dlx.key");      // 到期后用什么路由键
    args.put("x-message-ttl", 30000);                      // 30 秒没人消费就"过期"
    return new Queue("normal.queue", true, false, false, args);
}
```

流程:

```
生产者 --30秒TTL--> normal.queue(睡30秒) --过期--> dlx.exchange --> dlx.queue --> 消费者
```

**消费者只监听 dlx.queue 就行**:normal.queue 不需要任何消费者,它的作用只是"让消息睡 30 秒"。

```java
package com.example.rabbitmq.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DelayConsumer {

    @RabbitListener(queues = "dlx.queue")
    public void onMessage(String message) {
        System.out.println("延迟 30 秒后我才收到:" + message);
    }
}
```

生产者照常发到 normal.exchange 即可,唯一区别是消息要**等 30 秒**才会出现在 dlx.queue。

### 8.4 经典大坑:TTL 是按"队头"算的,不是按每条算的

RabbitMQ 的 TTL 过期检查是:**只看队头消息**(先进队列的那条)。

```
队列:[A(30秒) B(10秒)]
```

A 是队头、TTL 30 秒;B 在 A 后面、TTL 10 秒。B 的 10 秒到了**不会立刻过期**,必须等 A 先到期出队,B 才开始计时。结果 B 实际等了 30+ 秒才被处理。

**影响**:想用 TTL+DLX 做"每条消息各自延迟不同时间"会不准。要么所有消息统一延迟时间,要么用官方延迟插件(每条消息独立延迟,精确)。

---

## 九、消费端手动 ACK + 消息幂等(面试高频)

### 9.1 为什么默认的自动 ACK 不安全

SpringBoot 默认 `acknowledge-mode: auto`(自动确认):**消息一到消费者手里就标记"已消费"**,不管业务代码有没有处理成功。

```
消费者拿到消息 -> 立刻 ACK -> 业务代码崩了 -> 消息已确认,丢了
```

**手动 ACK 流程**:

```
消费者拿到消息 -> 业务处理 -> 成功才 ACK / 失败 NACK(可重回队列)
```

业务没处理完,消息一直处于 unacked 状态,消费者挂了 RabbitMQ 会重新投递给别的消费者——**消息不丢**。

### 9.2 配置 + 完整代码

application.yml 开启手动 ACK:

```yaml
spring:
  rabbitmq:
    host: your-mq-host
    port: 5672
    username: your-username
    password: your-password
    listener:
      simple:
        acknowledge-mode: manual   # 手动确认,处理成功才 ACK
        prefetch: 1                # 每次只取 1 条,处理完再取(配合手动 ACK 防堆积)
```

消费者代码:

```java
package com.example.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ManualAckConsumer {

    @RabbitListener(queues = "order.queue")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String msg = new String(message.getBody());
        try {
            // TODO 真正的业务处理(写库、调接口...)
            System.out.println("处理成功:" + msg);
            channel.basicAck(tag, false);        // 成功:确认,消息出队
        } catch (Exception e) {
            System.out.println("处理失败:" + msg);
            // 第三个参数 requeue:true = 放回队列重试;false = 进死信/丢弃
            channel.basicNack(tag, false, true); // 失败:重回队列,让别的消费者重试
        }
    }
}
```

> `@Header(AmqpHeaders.DELIVERY_TAG)` 拿到这条消息的投递编号,ACK 时要用它告诉 RabbitMQ"确认的是哪条"。

**坑提前标**:`basicNack` 第三个参数 `requeue=true` 时,如果业务**一直失败**,消息会无限循环重试,把消费者拖死。正确姿势:重试几次后 `requeue=false` 丢进死信队列(配合第七节),人工介入。

### 9.3 幂等:MQ 会重复投递,消费必须"做一次和做 N 次结果一样"

**为什么会有重复消息**:RabbitMQ 保证的是 **at-least-once(至少一次)**,不保证 exactly-once。典型场景:消费者处理完消息、还没来得及 ACK 就挂了 → RabbitMQ 认为没消费 → 重新投递 → **同一条消息被处理两次**。

**大白话**:"至少一次"= 消息不会丢,但可能**多发一次**。所以消费者要防"同一件事干两遍"(比如重复下单、重复扣款)。

### 9.4 两种主流幂等方案(带代码)

**方案一:Redis setnx 记录消息 ID(推荐,快)**

```java
package com.example.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class IdempotentConsumer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "order.queue")
    public void onMessage(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        String body = new String(message.getBody());
        // 消息 ID 由生产者生成并放进 header(生产端:MessageProperties.setMessageId())
        String messageId = message.getMessageProperties().getMessageId();

        // setnx:key 不存在才设置成功。第一次处理返回 true,重复投递返回 false
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent("mq:msg:" + messageId, "1", 1, TimeUnit.DAYS);
        if (first == null || !first) {
            System.out.println("重复消息,直接确认丢弃:" + messageId);
            channel.basicAck(tag, false);   // 已处理过,确认掉,不重复处理
            return;
        }

        try {
            // TODO 真正的业务处理(只有第一次会走到这里)
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, true);
        }
    }
}
```

**方案二:数据库唯一键(最稳,业务上强约束)**

给业务表加一个 `message_id` 唯一索引,消费时直接 insert:

```sql
-- 建表时加唯一索引
CREATE TABLE t_order_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_message_id (message_id)
);
```

```java
// 消费逻辑:先插记录,插得进去 = 第一次;插不进去(唯一键冲突) = 重复,直接跳过
// try { orderRecordMapper.insert(record); }   // 第一次:成功
// catch (DuplicateKeyException e) { 重复消息,跳过处理 }
```

| 方案 | 优点 | 缺点 |
|------|------|------|
| Redis setnx | 快、不侵入业务表 | Redis 要可用;key 要设过期时间防内存涨 |
| 数据库唯一键 | 最可靠,和业务数据同库同事务 | 要改表结构;高并发下唯一索引有开销 |

> 面试口径:MQ 是 at-least-once → 可能重复 → 消费端幂等。方案:Redis setnx / DB 唯一键 / 状态机判断。

---

## 十、踩坑笔记(血的教训)

1. **端口搞错**:连接用 **5672**(AMQP 协议端口),15672 是网页管理台端口,连不上就是这问题。
2. **监听器不生效**:`@RabbitListener` 所在类必须有 `@Component`/`@Service`,否则不在容器里,永远收不到消息。
3. **虚拟主机(vhost)不匹配**:账号和 vhost 要配对,默认 `/`,配错了报 `ACCESS_REFUSED`。
4. **交换机/队列参数不一致**:同名队列重复声明时参数(持久化等)必须一致,否则报 `PRECONDITION_FAILED`。
5. **TTL 按队头算**:队列里 A 在 B 前面,A 没到期,B 先到期也不会触发,要等 A 出队。想每条消息独立延迟,用官方延迟插件。
6. **nack 的 requeue 参数别乱用**:`requeue=true` 且业务一直失败 → 无限重试循环拖死消费者;`requeue=false` 又没配死信 → 消息直接丢。正确组合:重试几次后进死信。
7. **手动 ACK 忘了 ack/nack**:消息一直 unacked,`prefetch=1` 时消费者会被自己卡死(拿不到新消息)。
8. **自动 ACK 丢消息**:默认 auto 模式消息一到就确认,消费者处理中挂了消息就没了。需要"不丢消息"的必须改 manual。
9. **队列参数改了不生效**:死信/TTL 参数在声明队列时定死,改参数必须删旧队列重建,否则 `PRECONDITION_FAILED`。

---

## 十一、下一步(路线图)

- [x] ~~02 深入:死信队列(DLX)+ 延迟队列~~ → **已并入本篇第七、八节**
- [x] ~~03 深入:消费端手动 ACK + 消息幂等性~~ → **已并入本篇第九节**
- [ ] 02 深入:集群与镜像队列
