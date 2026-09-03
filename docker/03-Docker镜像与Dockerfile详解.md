# Docker 镜像与 Dockerfile 详解(2026-09-03)

> 来源:Docker 课程课件整理
> 目标:掌握镜像的"增删查、拉取推送",并重点吃透 **Dockerfile 两种构建方式 + 核心指令**,尤其 CMD 与 ENTRYPOINT 的区别。
> 学习建议:每个指令动手写一个 Dockerfile 构建一遍,踩过的坑才是真学到。

---

## 一、镜像管理(Image)

### 1.1 列出 / 查看镜像

```bash
docker images                  # 列出本地镜像
docker images -a               # -a 含中间层镜像(无仓库名/标签的是中间层,别删)
docker images -q               # -q 只显示镜像 id
docker inspect 镜像id或名       # 查看镜像详细信息
```

镜像名规则:**仓库名 + 镜像名 + 标签**唯一确定一个镜像,例如 `redis:6.2.6`(不写 tag 默认 `latest`)。

### 1.2 删除镜像

```bash
docker rmi 镜像id               # 删除指定镜像
docker rmi -f 镜像id            # 强制删除
docker rmi $(docker images -q centos)   # 删除所有 centos 镜像
```

> ⚠️ 有容器正在使用的镜像删不掉,先删容器再删镜像。

### 1.3 搜索 / 拉取 / 推送镜像

```bash
# 搜索(等价于去 hub.docker.com 搜)
docker search centos

# 拉取
docker pull centos             # 默认 latest
docker pull ubuntu:14.04       # 拉指定 tag
docker pull -a 镜像名           # 拉该仓库全部 tag

# 推送(类比 git push)
# 1. 先去 hub.docker.com 注册账号
docker login                   # 登录
docker push <你的用户名>/<仓库名>:<tag>   # 推送
```

> 推送私有仓库(如阿里云容器镜像服务)流程:
> ① 登录容器镜像服务控制台 → 建**命名空间** → 建**镜像仓库**;
> ② 按页面给的脚本执行(把用户名换成你自己的):
> ```bash
> sudo docker login --username=<你的用户名> registry.cn-hangzhou.aliyuncs.com
> sudo docker tag <镜像Id> registry.cn-hangzhou.aliyuncs.com/<命名空间>/<仓库名>:<版本号>
> sudo docker push registry.cn-hangzhou.aliyuncs.com/<命名空间>/<仓库名>:<版本号>
> ```
> ③ 其他机器拉取:`docker pull registry.cn-hangzhou.aliyuncs.com/<命名空间>/<仓库名>:<版本号>`

---

## 二、构建镜像的两种方式

```
方式一:docker commit —— 把"改过的容器"保存成镜像(不推荐生产用)
方式二:docker build  —— 用 Dockerfile 文件构建(标准做法)
```

### 2.1 docker commit(容器 → 镜像)

适合快速保存现场,但**不可复现、不透明**,生产不推荐。

```bash
docker commit [选项] 容器名 [仓库名[:tag]]
# -a 作者  -m 提交说明

# 示例:在容器里装好 nginx 后,把整个容器提交成镜像
docker run -it --name nginx-dev -p 80 centos /bin/bash   # 进去装 nginx 后退出
docker commit -a 'your-name' nginx-dev my-nginx:v1.0
docker images                                             # 就能看到 my-nginx:v1.0

# 用自建镜像启动容器
docker run -d -p 80 my-nginx:v1.0 /usr/sbin/nginx -g "daemon off;"
```

### 2.2 docker build(Dockerfile → 镜像)

Dockerfile 就是一个**写满构建命令的文本文件**,用它构建可复现、可版本管理,是标准做法。

```bash
mkdir -p ~/docker-test && cd ~/docker-test
vi Dockerfile                # 写 Dockerfile(见下)
docker build -t='<仓库名>/<镜像名>:<tag>' 目录
# 示例:
docker build -t='my-nginx:2.0' ~/docker-test/
```

构建后用它创建容器即可复用。

---

## 三、Dockerfile 指令详解

Dockerfile 由注释(`#`)和指令(`INSTRUCTION argument`)组成,下面按构建顺序讲。

### FROM(必选,第一行)

```dockerfile
FROM <image>
FROM <image>:<tag>
```

指定**基础镜像**,必须是第一条非注释指令。

### MAINTAINER / LABEL(作者信息)

```dockerfile
MAINTAINER your-name "your-email@example.com"
# 新版推荐:
LABEL maintainer="your-email@example.com"
```

### RUN(构建时执行命令)

```dockerfile
# shell 模式
RUN echo hello
# exec 模式
RUN ["/bin/bash", "-c", "echo hello"]
```

> 每个 RUN 都会在**当前镜像上层新建一层镜像**来执行——指令越多镜像层越多,能合并就合并(用 `&&` 连接)。

### EXPOSE(声明端口)

```dockerfile
EXPOSE 80
```

> **只是"声明"容器会用到 80 端口**,出于安全 Docker 并不会自动打开它——运行容器时依然要手动 `-p` 做端口映射。

### CMD(容器启动时的默认命令)⭐

```dockerfile
CMD ["executable", "param1", "param2"]   # exec 模式(推荐)
CMD command param1 param2                 # shell 模式
CMD ["param1", "param2"]                  # 作为 ENTRYPOINT 的默认参数
```

要点:

- 在**容器启动时**执行(不是构建时,RUN 才是构建时);
- 如果 `docker run` 后面带了命令,**CMD 会被覆盖**。

### ENTRYPOINT(容器启动命令,不可被覆盖)⭐

```dockerfile
ENTRYPOINT ["executable", "param1", "param2"]
ENTRYPOINT command param1 param2
```

- 和 CMD 很像,唯一区别:**不会被 `docker run` 后面的命令覆盖**;
- 要覆盖只能显式 `docker run --entrypoint 新命令`;
- 经典搭配:**ENTRYPOINT 写主程序,CMD 写默认参数**(CMD 参数可被 run 覆盖)。

### ADD / COPY(拷文件进镜像)

```dockerfile
COPY <src>... <dest>
ADD <src>... <dest>
```

- 把构建目录下的文件复制进镜像;**src 必须是构建目录内的相对路径**;dest 用镜像内绝对路径;
- 路径有空格用数组写法:`ADD ["src", "dest"]`;
- ADD 支持自动解压 tar、支持远程 URL;**远程 URL 不推荐用 ADD**,建议 RUN curl/wget;
- 一般场景优先用 **COPY**(语义单纯),ADD 用于需要解压的场景。

### VOLUME(声明数据卷,后面单独讲)

```dockerfile
VOLUME ["/data"]
```

给容器挂数据卷,提供**持久化和共享数据**能力。

### WORKDIR(设置工作目录)

```dockerfile
WORKDIR /app
```

设置容器内工作目录,**ENTRYPOINT 和 CMD 都会在此目录下执行**;相对路径会层层拼接:

```dockerfile
WORKDIR /a
WORKDIR b
WORKDIR c
RUN pwd    # 结果:/a/b/c
```

### ENV(环境变量)

```dockerfile
ENV <key>=<value>
ENV JAVA_HOME=/usr/local/jdk
```

构建过程和运行过程都生效。

### USER(以什么用户运行)

```dockerfile
USER daemon
USER nginx
```

不写默认 **root** 运行;生产安全习惯:用低权限用户跑应用。

### ONBUILD(触发器,进阶)

```dockerfile
ONBUILD [INSTRUCTION]
```

**当本镜像被其他镜像作为基础镜像(被 FROM)时**,才触发执行里面的指令。适合做"基础镜像 + 自动追加逻辑"。

---

## 四、经典完整示例:一个带 Nginx 的镜像

```dockerfile
# 基础镜像(国内网络可先配加速器,或使用可访问的公共镜像)
FROM centos:7.9.2009

# 作者
LABEL maintainer="your-name <your-email@example.com>"

# 修复 CentOS8+ yum 源失效问题(老镜像必需)
RUN sed -i -e "s|mirrorlist=|#mirrorlist=|g" /etc/yum.repos.d/CentOS-*
RUN sed -i -e "s|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g" /etc/yum.repos.d/CentOS-*

# 安装 nginx
RUN yum install -y epel-release
RUN yum install -y nginx

# 声明端口
EXPOSE 80

# 默认启动命令(运行时可用 run 参数覆盖)
CMD ["/usr/sbin/nginx", "-g", "daemon off;"]
```

```bash
# 构建并运行
docker build -t='my-nginx:1.0' .
docker run -d -p 80:80 --name web1 my-nginx:1.0
curl http://127.0.0.1:80
```

---

## 五、CMD vs ENTRYPOINT(面试高频,一次记牢)

| 对比 | CMD | ENTRYPOINT |
|------|-----|------------|
| 什么时候执行 | 容器启动时 | 容器启动时 |
| `docker run` 带命令会怎样 | **被覆盖** | 不被覆盖(仍执行) |
| 如何覆盖 | run 后面直接写命令 | 只能 `--entrypoint` |
| 最佳用法 | 放**默认参数** | 放**主程序** |

```dockerfile
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
```

- 不传参数运行:`nginx -g "daemon off;"`;
- 传参数运行:`docker run ... nginx -t` → 实际执行 `nginx -t`,主程序永远是 nginx。

> 速记:**CMD 是"默认值,可被顶掉",ENTRYPOINT 是"定死的入口,顶不掉"**。
