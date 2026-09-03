# Java 项目容器化部署实战(2026-09-03)

> 来源:Docker 课程课件整理
> 目标:把今天学的 Docker 用回 Java 项目上——写好 SpringBoot 的 Dockerfile、用 IDEA 远程部署到服务器、以及 compose 一键编排。
> 学习建议:先在本地 IDEA 里把流程走通,再到服务器复现。涉及远程开放端口的操作**仅限学习环境**,生产必须加认证/防火墙。

---

## 一、SpringBoot 项目标准 Dockerfile(直接抄)

### 1.1 基于 JDK17 的镜像

```dockerfile
# 基础镜像:JDK17(拉不动就配置镜像加速器,或换成你能访问的公共镜像)
FROM openjdk:17-jdk

# 可注入的启动参数(运行时可用 -e PARAMS=... 覆盖)
ENV PARAMS="--server.port=8000"

# 把容器时区设置为中国时区(不设的话日志时间差 8 小时)
RUN /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo 'Asia/Shanghai' >/etc/timezone

# 把构建好的 jar 拷进镜像,固定叫 app.jar
COPY *.jar /app.jar
EXPOSE 8000

# 启动命令(UTF-8 + 防止随机数阻塞 + 接收外部参数)
ENTRYPOINT ["/bin/sh","-c","java -Dfile.encoding=utf8 -Djava.security.egd=file:/dev/./urandom -jar app.jar ${PARAMS}"]
```

### 1.2 基于 JDK8 的镜像

把第一行换成:

```dockerfile
FROM openjdk:8u342-jdk
```

其余完全一样。

> 小贴士:
> - `-Djava.security.egd=file:/dev/./urandom` 是经典经验:避免高并发下因 `/dev/random` 熵不足导致启动卡顿;
> - `${PARAMS}` 让"端口、环境等参数"在运行时用 `-e PARAMS="--server.port=9000 --spring.profiles.active=prod"` 灵活注入,不用为每个环境重做镜像。

### 1.3 构建 + 运行

```bash
# 先 mvn package 打出 jar,然后:
docker build -t boot-demo:1.0 .
docker run -d -p 8000:8000 --name boot-app -e PARAMS="--server.port=8000" boot-demo:1.0
curl http://127.0.0.1:8000/接口路径
```

---

## 二、IDEA 远程部署到 Docker(开发提效)

把"本地改代码 → 打包 → 传服务器 → 跑容器"自动化:IDEA 直接连服务器的 Docker 帮你构建运行。

### 2.1 服务器:开放 Docker 远程连接(仅限学习环境!)

```bash
# 1. 改 docker 服务启动参数,追加 TCP 监听
vi /usr/lib/systemd/system/docker.service
# 找到 ExecStart= 那行,在尾部追加:
# -H unix:///var/run/docker.sock -H tcp://0.0.0.0:2375
# 改完长这样:
# ExecStart=/usr/bin/dockerd -H fd:// --containerd=/run/containerd/containerd.sock -H unix:///var/run/docker.sock -H tcp://0.0.0.0:2375

# 2. 重载并重启
systemctl daemon-reload
systemctl restart docker

# 3. 确认端口在监听
yum install -y net-tools
netstat -ntlp | grep 2375
```

> ⚠️ **安全警告**:`tcp://0.0.0.0:2375` 是**无认证的裸端口**,谁连上谁就能控制你的 Docker(等于 root)。**只允许在可信内网/学习环境开**,生产环境必须:绑定内网 IP + TLS 证书认证 + 防火墙白名单,三选一以上。

### 2.2 IDEA 侧配置

1. IDEA 装 **Docker 插件**(一般自带);
2. Settings → Build, Execution, Deployment → Docker → 新增连接:
   - Engine API URL 填:`tcp://<服务器IP>:2375`;
3. 确认连接图标变绿(能列出服务器上的镜像/容器);
4. 在 Run/Debug Configurations 新建 **Dockerfile 运行配置**:
   - Dockerfile 选项目里的 `Dockerfile`;
   - 镜像 tag、容器名、端口映射(`9008:8000`)、环境变量照填;
5. 点运行 → IDEA 自动把 jar 打进镜像、在服务器上起容器。

### 2.3 IDEA 部署用的 Dockerfile 示例(含 VOLUME 注释)

```dockerfile
# 基础镜像
FROM openjdk:8
# 维护者信息
LABEL maintainer="your-name <your-email@example.com>"
# 设置容器时区为东八区
RUN /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone

# SpringBoot 内嵌 Tomcat 默认用 /tmp 做工作目录;
# 声明 /tmp 为数据卷,写入的信息不落容器存储层,由宿主机 /var/lib/docker 下临时目录接管
VOLUME /tmp

# 复制 jar 到镜像(放在 Dockerfile 同级或指定相对路径)
ADD target/<你的项目>-1.0.0.jar app.jar

# 启动命令
ENTRYPOINT ["java","-jar","app.jar"]

# 声明服务端口
EXPOSE 9008
```

> 流程本质:IDEA 通过 2375 端口把 `Dockerfile + jar` 交给服务器 Docker 执行,和你手动 `docker build` 一模一样。

---

## 三、容器内装 JDK + SSH(构建多节点学习环境)

教学场景常需要"一个能 ssh 登录的 JDK 容器"(比如搭集群模拟多台机器),可参考下面这份去敏感化的 Dockerfile:

```dockerfile
FROM centos:7.5.1804

# 换阿里云 yum 源
RUN curl -o /etc/yum.repos.d/CentOS-Base.repo https://mirrors.aliyun.com/repo/Centos-7.repo

# 安装 wget 并下载 JDK(把版本/地址换成实际使用的)
RUN yum install -y wget
RUN wget https://download.oracle.com/java/17/archive/jdk-17.0.4.1_linux-x64_bin.tar.gz
RUN tar xzvf jdk-17.0.4.1_linux-x64_bin.tar.gz -C /usr/local/
RUN rm -rf jdk-17.0.4.1_linux-x64_bin.tar.gz

ENV JAVA_HOME=/usr/local/jdk-17.0.4.1/
ENV CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
ENV PATH=$PATH:$JAVA_HOME/bin

# 写进 /etc/profile 让登录终端也生效
RUN echo -e 'export JAVA_HOME=/usr/local/jdk-17.0.4.1/' >> /etc/profile \
    && echo -e 'export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar' >> /etc/profile \
    && echo -e 'export PATH=$PATH:$JAVA_HOME/bin' >> /etc/profile \
    && source /etc/profile

# 安装 sshd 并生成主机密钥,设置 root 密码(改成你自己的强密码)
RUN yum -y install openssh-server \
    && mkdir -p /var/run/sshd/ \
    && ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key \
    && ssh-keygen -t dsa -f /etc/ssh/ssh_host_dsa_key \
    && echo 'root:<你的强密码>' | chpasswd

CMD /usr/sbin/sshd -D
EXPOSE 22
```

```bash
docker build -t jdk-ssh:1.0 .
docker run -d -p 2222:22 --name node1 jdk-ssh:1.0
ssh root@127.0.0.1 -p 2222    # 输入你设置的密码进入容器
java -version                 # JDK 已在容器内
```

> ⚠️ 只用于本地练习;密码不要用弱密码,更不要放进公开仓库(本笔记已用占位符)。

---

## 四、完整链路回顾(今天的内容串起来)

```
写代码(mvn package 出 jar)
   ↓
写 Dockerfile(FROM/RUN/COPY/EXPOSE/ENTRYPOINT)
   ↓
docker build 构建镜像(03 篇)
   ↓
docker run -d -p 端口映射 + -v 数据卷(02、04 篇)
   ↓
多个服务 → docker-compose.yml 一键编排(04 篇)
   ↓
交付:镜像 push 到仓库,别人 pull 下来直接跑(03 篇)
```

**生产注意事项清单:**
1. 基础镜像尽量带明确 tag,不用裸 `latest`;
2. 容器时区要设成 Asia/Shanghai,否则日志时间错 8 小时;
3. 启动参数用 ENV + `${PARAMS}` 注入,环境差异不重建镜像;
4. 2375 裸端口只在学习环境开,生产用 TLS/内网;
5. 密码、密钥一律用占位符 + 环境变量/secret 注入,不进镜像和仓库。
