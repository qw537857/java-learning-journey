# Docker 容器操作实战(2026-09-03)

> 来源:Docker 课程课件整理
> 目标:把容器"创建、查看、进入、停止、删除"这条生命周期链路敲熟,并完成一次静态网站(Nginx)容器化部署。
> 学习建议:每条命令在 CentOS7 上跟着敲,注意区分"交互式容器"和"守护式容器"。

---

## 一、容器的生命周期命令

### 1.1 运行一个新容器

```bash
# 基本格式
docker run IMAGE [COMMAND] [ARGS...]

# 示例:用 centos 镜像执行一条命令,执行完容器就退出
docker run centos echo 'hello docker'

# --name:给容器起名字(强烈建议,不然只能记一长串 id)
docker run --name my-centos centos echo 'hello docker'
```

`run` 命令内部逻辑:

1. 先检查本地有没有这个镜像,**没有会自动 pull**;
2. 运行后启动一个容器,并开启一套独立文件系统(可在容器内自由建文件);
3. **当容器内的主程序退出时,容器也就退出了**;
4. 运行中的容器不能直接删,要先停止,或 `rm -f` 强删。

### 1.2 交互式运行(进入容器操作)

```bash
# -i:--interactive 允许和容器内标准输入交互
# -t:--tty 分配一个伪终端
docker run -i -t centos /bin/bash

# 起名字 + 交互式
docker run --name my-centos -it centos /bin/bash
```

进入后就是一个 centos 的 shell,敲 `exit` 退出 → **容器随之停止**。

### 1.3 查看容器

```bash
docker ps        # 只看正在运行的
docker ps -a     # 看所有(含已停止)
docker ps -l     # 看最近创建的
docker ps -q     # 只输出容器 id(方便拼命令,如 docker rm $(docker ps -aq))
```

### 1.4 查看详情 / 自定义名 / IP

```bash
docker inspect 容器名            # 查看容器详细信息(很长)
docker run --name=my-web -it centos /bin/bash   # 自定义名
docker inspect my-web | grep IPAddress   # 查容器 IP
```

### 1.5 启动 / 删除

```bash
docker start [-i] 容器名    # 重新启动已停止的容器
docker rm 容器名            # 删除容器(只能删已停止的)
docker rm $(docker ps -aq)  # 删除所有已停止容器
```

---

## 二、守护式容器(重点,生产天天用)

### 2.1 为什么需要守护式

前面的容器都是"创建后马上关了"。真实场景里,应用需要**长期运行提供服务**:

| 特点 | 说明 |
|------|------|
| 能长期运行 | 不会因会话退出而停 |
| 没有交互式会话 | 后台默默干活 |
| 适合跑应用和服务 | Web 服务、数据库等 |

### 2.2 守护式启动

```bash
# -d:后台运行(daemon)
docker run -d 镜像名 [COMMAND] [ARGS...]

# 示例:跑一个每 1 秒打印一次 hello 的后台容器(证明它还活着)
docker run --name dc1 -d centos /bin/sh -c "while true; do echo helloworld; sleep 1; done"
```

> 敲完 `docker ps` 能看到 dc1 处于 UP 状态——它没退出,因为前台进程一直在循环。

### 2.3 退出容器但不关闭它

交互式进入容器后:

- `exit` → 容器停止;
- `Ctrl + P + Q` → **退出终端但容器继续运行**(保命技巧)。

### 2.4 重新进入运行中的容器

```bash
# attach:附加到容器原来的主进程,执行的是原来的命令
docker attach dc1

# exec:在运行中的容器里另起一个新进程(更常用,相当于"开个新终端进去")
docker exec -it dc1 /bin/bash
```

> 区别:`attach` 进入的是容器**主进程**(会跟着主进程输出走,一退出主进程就…);`exec` 是**新开进程**,互不干扰,日常进容器干活优先用 `exec -it`。

### 2.5 查看日志和进程

```bash
docker logs dc1            # 看日志
docker logs -f dc1         # -f 持续跟踪(follow),像 tail -f
docker logs -t dc1         # -t 每条日志加时间戳
docker logs --tail 20 dc1  # --tail 只看最后 N 行
docker top dc1             # 看容器内正在跑的进程(类似 ps)
```

### 2.6 停止守护式容器

```bash
docker stop dc1   # 优雅停止:发停止信号,等容器自己关闭
docker kill dc1   # 直接杀掉(强停)
```

---

## 三、端口映射:让容器对外提供服务

容器内部是隔离网络,外面访问不到,需要**端口映射**把"宿主机端口 : 容器端口"打通。

```bash
# -P:随机把容器所有暴露的端口映射到宿主机
docker run -P -it centos /bin/bash

# -p:手动指定映射
docker run -p 80 -it centos /bin/bash        # 只给容器端口 80,宿主机端口随机(32768 起)
docker run -p 8080:80 -it centos /bin/bash   # 宿主机8080 → 容器80(最常用)
```

> 映射后访问:`curl http://127.0.0.1:8080/`,本质是访问宿主机的 8080,再由 docker 转发到容器 80。

---

## 四、实战:用容器部署 Nginx 静态网站

### 4.1 部署流程

```
创建映射端口的容器 → 容器里装 Nginx → 改首页 → 启动 Nginx → 验证访问
```

```bash
# 1. 创建交互式容器并映射端口(宿主机随机端口)
docker run -p 80 --name=my-nginx -it centos /bin/bash

# 2. 容器内安装 nginx + vim
#    ⚠️ CentOS8 已停止维护,官方镜像里的 yum 源会失效,先执行下面两句修复:
sed -i -e "s|mirrorlist=|#mirrorlist=|g" /etc/yum.repos.d/CentOS-*
sed -i -e "s|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g" /etc/yum.repos.d/CentOS-*

yum install -y epel-release
yum install -y nginx vim

# 3. 改静态首页
cd /usr/share/nginx/html
vi index.html          # 写点自己的内容,如 <h1>Hello Docker</h1>

# 4. 启动 nginx(配置文件在 /etc/nginx/nginx.conf)
/usr/sbin/nginx

# 5. Ctrl + P + Q 退出(不关容器)

# 6. 宿主机验证访问
curl http://127.0.0.1:32769/index.html    # 端口用 docker ps 里映射出来的实际端口
```

### 4.2 踩坑经验(面试/实操都爱问)

1. `docker inspect 容器名` 可以查到容器 IP,**在宿主机用容器 IP 也能访问**到静态页;
2. `docker stop` 关容器 → `docker start` 再开 → **Nginx 不会自动启动**(容器内没有服务托管),要用 `docker top` 验证;
3. 手动拉起容器内服务:`docker exec -it my-nginx /usr/sbin/nginx`;
4. **容器删除重建后,端口映射会重新分配**,原来的随机端口就失效了——所以生产部署要么固定 `-p 8080:80`,要么用 compose/编排统一管理。

---

## 五、本节速记

| 想干什么 | 用什么命令 |
|----------|-----------|
| 前台跑容器 | `docker run -it 镜像 /bin/bash` |
| 后台跑容器 | `docker run -d 镜像 命令` |
| 退出不关容器 | `Ctrl + P + Q` |
| 进容器干活 | `docker exec -it 容器名 /bin/bash` |
| 看日志 | `docker logs -f 容器名` |
| 端口映射 | `docker run -p 宿主机端口:容器端口` |
| 优雅停止 / 强杀 | `docker stop` / `docker kill` |
| 删停止的容器 | `docker rm 容器名` |

> 核心理解:**容器 = 一个跑着主进程的精简 Linux**,主进程退 = 容器退。守护式容器的任务就是让主进程永远别退出。
