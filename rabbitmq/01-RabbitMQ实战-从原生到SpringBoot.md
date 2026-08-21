# 01 RabbitMQ 实战:从原生客户端到 SpringBoot 集成

- **日期**:2026-08-21
- **一句话总结**:消息队列解决"解耦、异步、削峰"三大问题;本篇把 RabbitMQ 的核心概念、四种交换机、原生 Java 客户端、SpringBoot 集成一次讲透,全部带可运行代码。
- **配套代码**:原生客户端工程 [rabbitmq-send-demo](./rabbitmq-send-demo/) / [rabbitmq-receive-demo](./rabbitmq-receive-demo/)(纯 Java main 方法跑);SpringBoot 工程 [springboot-send-demo](./springboot-send-demo/)(发送端) / [springboot-receive-demo](./springboot-receive-demo/)(接收端)。

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
        factory.setHost("your-rabbitmq-host");   // 你的 RabbitMQ 地址
        factory.setPort(5672);                // 注意是 5672,不是管理台 15672!
        factory.setUsername("root");
        factory.setPassword("root");
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
        factory.setUsername("root");
        factory.setPassword("root");

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

**依赖**(springboot-send-demo / springboot-receive-demo 的 pom):

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
    username: root
    password: root
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

## 七、踩坑笔记(血的教训)

1. **端口搞错**:连接用 **5672**(AMQP 协议端口),15672 是网页管理台端口,连不上就是这问题。
2. **监听器不生效**:`@RabbitListener` 所在类必须有 `@Component`/`@Service`,否则不在容器里,永远收不到消息。
3. **虚拟主机(vhost)不匹配**:账号和 vhost 要配对,默认 `/`,配错了报 `ACCESS_REFUSED`。
4. **交换机/队列参数不一致**:同名队列重复声明时参数(持久化等)必须一致,否则报 `PRECONDITION_FAILED`。

---

## 八、下一步(路线图)

- [ ] 02 深入:死信队列(DLX)+ 延迟队列(插件/TTL 实现)
- [ ] 03 深入:消费端手动 ACK + 消息幂等性
- [ ] 04 深入:集群与镜像队列
