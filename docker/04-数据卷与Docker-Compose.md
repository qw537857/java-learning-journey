# 数据卷与 Docker Compose(2026-09-03)

> 来源:Docker 课程课件整理
> 目标:搞懂**数据卷**为什么能持久化/共享,并学会用 **docker-compose** 一条命令管理多个容器。
> 学习建议:重点理解"容器删了数据不能丢"这个痛点,再动手写一份 compose 文件。

---

## 一、为什么要数据卷(Data Volume)

Docker 的理念是"应用和环境打包",但**容器生命周期很短**(删容器就什么都没了),而数据要**持久化**;同时多个容器之间还要**共享数据**。这些需求催生了数据卷:

> **数据卷 = 经过特殊设计的目录,绕过联合文件系统(UFS),独立于容器的生命周期存在。**

理解数据卷,记 4 句话:

1. 数据卷**独立于容器**,真实存在于**宿主机**,容器删除它不删;
2. 数据卷可以是**文件或目录**;
3. 容器通过数据卷和**宿主机共享数据**(双向);
4. 同一个数据卷可被**多个容器同时挂载**,实现容器间共享。

### 数据卷的特点

- 容器启动时初始化——如果镜像在挂载点本身有数据,会**拷贝到新数据卷**里;
- 容器之间可共享、可重用;
- 可以直接修改数据卷里的内容(改宿主机文件,容器内立即可见)。

---

## 二、数据卷的三种用法

### 2.1 run 时挂载(最常用)

```bash
# 语法:docker run -v 宿主机路径:容器内路径
docker run -v ~/data:/data -it centos /bin/bash

# 效果:宿主机 ~/data 目录 ↔ 容器 /data 目录 实时互通
```

### 2.2 只读挂载

```bash
# 加 :ro 后,容器内只能读不能写(适合放配置/密钥/只读资源)
docker run -v ~/datavolume:/data:ro -it centos /bin/bash
```

### 2.3 Dockerfile 里声明 VOLUME

```dockerfile
FROM centos
VOLUME ["/datavolume1"]
CMD /bin/bash
```

```bash
docker build -t my-volume-test .
docker run -it my-volume-test
```

> ⚠️ 注意:通过 VOLUME 声明的卷,启动时会在宿主机自动分配一个匿名目录(在 `/var/lib/docker/volumes/` 下),**你无法指定宿主机路径**,所以多个容器没法直接用同一个。要"精确共享/指定路径",就用 `-v 宿主机路径:容器路径`。

---

## 三、docker-compose:多容器一键管理

### 3.1 为什么需要 Compose

- Dockerfile 负责 **Build(构建镜像)**;
- docker-compose 负责 **Run(创建/编排容器)**;
- 一个 yml 文件统一管理多个容器的**网络、数据卷、环境变量、端口、重启策略**等;
- 效果:**一次配置,一键 start/stop/restart/rm 全部服务**,告别一堆 `docker run` 命令。

### 3.2 安装 Compose

```bash
# 方式一:在线下载(版本号可换)
sudo curl -L "https://mirrors.aliyun.com/docker-toolbox/linux/compose/1.29.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

# 方式二:本地有安装包就直接上传到 /usr/local/bin/

# 加执行权限 + 建软链
sudo chmod +x /usr/local/bin/docker-compose
sudo ln -s /usr/local/bin/docker-compose /usr/bin/docker-compose

# 验证
docker-compose --version
```

### 3.3 compose 文件结构(逐行注释版)

```yaml
version: '2.1'        # compose 文件版本(注意:新版 compose 已不强制写 version)
services:             # 要管理的容器列表
  web:                # 服务名(容器名)
    image: nginx:1.17.6        # 用哪个镜像
    ports:                     # 端口映射
      - "9001:80"              # 宿主机9001 → 容器80
    volumes:                   # 数据卷
      - /root/web/dist:/usr/share/nginx/html
    environment:               # 环境变量(key: value 写法)
      username: your-name
    restart: always            # 重启策略(见下)
    dns:                       # 容器 DNS
      - "114.114.114.114"
```

### 3.4 restart 重启策略(重要)

| 策略 | 行为 |
|------|------|
| `no` | 默认。容器退出时不重启 |
| `on-failure` | 非正常退出(退出码非 0)才重启 |
| `on-failure:3` | 非正常退出时重启,**最多 3 次** |
| `always` | 退出总是重启 |
| `unless-stopped` | 总是重启,**但手动 stop 过的不重启**(d 进程启动时已停的也不拉) |

> 生产常用 `always` / `unless-stopped`,容器挂了能自己爬起来。

### 3.5 常用命令

```bash
docker-compose up -d              # 构建并后台启动全部服务(最常用)
docker-compose ps                 # 查看服务状态
docker-compose logs -f            # 跟踪所有服务日志
docker-compose restart            # 重启所有
docker-compose restart web        # 只重启 web 服务
docker-compose start / stop       # 启动 / 停止(保留容器)
docker-compose stop web
docker-compose rm                 # 删除所有服务容器
docker-compose down               # 停止并删除容器+网络(常用收尾)
```

---

## 四、实战:nginx + SpringBoot 示例(compose 版)

场景:两个 Nginx 静态站 + 两个 SpringBoot 应用,共享同一份静态资源目录。

```bash
# 1. 准备静态资源(构建前端后放到该目录)
mkdir -p /root/compose/dist
echo '<h1>Hello Docker Compose</h1>' > /root/compose/dist/index.html
```

```yaml
# docker-compose.yml
version: '2.1'
services:
  nginx-1:
    image: nginx:1.17.6
    ports:
      - "9001:80"
    volumes:
      - /root/compose/dist:/usr/share/nginx/html
    restart: always

  nginx-2:
    image: nginx:1.17.6
    ports:
      - "9002:80"
    volumes:
      - /root/compose/dist:/usr/share/nginx/html
    restart: always

  app-1:                        # 自己构建的 SpringBoot 镜像(boot-demo:1.0)
    image: boot-demo:1.0
    ports:
      - "7001:8000"
    restart: always

  app-2:
    image: boot-demo:1.0
    ports:
      - "7002:8000"
    restart: always
```

```bash
docker-compose up -d     # 一键起 4 个容器
docker-compose ps
curl http://127.0.0.1:9001/index.html    # 访问两个 nginx
curl http://127.0.0.1:9002/index.html
```

> 体会:同样的效果用 `docker run` 要敲 4 条又臭又长的命令,compose 一份 yml 搞定,而且两台 Nginx 挂的是**同一个宿主机目录**,改一处两边都生效——这就是数据卷 + 编排的典型组合。

---

## 五、本节速记

1. **容器是"一次性"的,数据卷是"永久"的**——删容器不删数据,卷存在宿主机;
2. 挂载三式:`-v 宿主机:容器`、`:ro` 只读、Dockerfile `VOLUME`;
3. Compose = **用 yml 把多个容器的配置写下来,一条 `up -d` 全起**;
4. 重启策略从 `no` → `on-failure:N` → `always` → `unless-stopped` 按需选;
5. 天天用的三条:`docker-compose up -d`、`ps`、`logs -f`。
