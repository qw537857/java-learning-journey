# Docker 入门:虚拟化与核心概念(2026-09-03)

> 来源:Docker 课程课件整理
> 目标:讲透"Docker 是什么、解决了什么问题、三个核心概念",并完成 CentOS7 安装 + 镜像加速配置。
> 学习建议:概念部分先理解"为什么",命令部分照着敲 3 遍。

---

## 一、虚拟化:从虚拟机到容器

### 1.1 为什么需要虚拟化

**虚拟化**:把一台物理计算机"虚拟"成多台逻辑计算机,每台逻辑计算机可以跑不同操作系统,应用之间互相隔离、互不影响。

### 1.2 传统虚拟化 → 硬件辅助虚拟化 → 容器虚拟化

| 阶段 | 做法 | 特点 |
|------|------|------|
| **传统虚拟化(软件模拟)** | 虚拟机软件模拟出 CPU、内存、硬盘等整套虚拟硬件层,客户机指令都要穿过虚拟硬件层 | 慢,要模拟所有硬件 |
| **硬件辅助虚拟化** | CPU 厂商(Intel/AMD)在硬件层直接支持虚拟化,部分指令无需经过虚拟硬件层模拟 | 性能和效率大幅提升 |
| **容器级虚拟化** | **不需要模拟硬件层,所有容器共享宿主机的同一个内核** | 启动快、资源省,见下节 |

### 1.3 传统虚拟机 vs 容器

| 对比项 | 传统虚拟化(VM) | 容器虚拟化(Docker) |
|--------|---------------|-------------------|
| 创建速度 | 很慢 | 非常快(秒级) |
| 性能消耗 | 模拟硬件层增加系统调用链环节,有性能损耗 | 共享内核,几乎没有性能损耗 |
| 资源消耗 | 很大(每个 VM 一套完整 OS) | 很小,一台机器轻松跑多个容器 |
| 操作系统覆盖 | 支持 Linux / Windows / Mac | 仅限内核所支持的操作系统(如 Linux 容器只能在 Linux 内核上跑) |

> 一句话:**虚拟机 = 虚拟出一整台电脑;容器 = 只把"应用 + 它的运行环境"打包隔离,大家共用同一个操作系统内核。**

---

## 二、Docker 是什么

### 2.1 一句话理解

Docker 是一个"装应用的容器",就像:
- 杯子用来装水、笔筒用来装笔、书包用来装书;
- 你可以把网站、把 Java 程序、把数据库……**任何程序**装进 Docker 里。

### 2.2 Docker 的思想:集装箱

Docker 的思想来源于**集装箱**:

- 集装箱把各种货品**标准化**:装货、卸货、搬运都变成流水线,不需要每种货品单独设计运输方式;
- 货品封装在集装箱里,**对外隔离、更安全**;
- 集装箱之间互不影响,一艘大船可以同时运所有货品,随时方便地装到飞机、火车、货车上。

对应到软件:

- "货品" = 应用 + 它依赖的第三方服务 + 运行环境;
- Docker = 把"应用 + 环境"打包成标准化的**镜像**,从而轻松完成**产品交付、环境迁移、部署运维**;
- Docker 对宿主机资源消耗极小,能把服务器资源利用率拉满。

### 2.3 Docker 解决了哪些问题

```
1. 组织有序性:多个应用依赖混乱、同一依赖要不同版本 → 容器把每个应用的环境独立隔离,互不影响。
2. 便携性:应用迁移(同 OS / 跨 OS)、大规模集群 → 容器化后非常方便。
3. 安全性:应用被攻击 / 有 bug / 依赖混乱 → 危害被隔离在容器内,不殃及宿主机。
```

### 2.4 Docker vs 传统虚拟机(VM)

Docker 核心基于 **LXC(Linux Container)** 技术实现,本质是**容器不是虚拟机**,用 Linux 的 **namespace(隔离)+ cgroups(资源限制)** 实现隔离与限额。

| 对比项 | Docker | VM |
|--------|--------|-----|
| 操作系统 | 与宿主机**共享内核** | 在宿主机 OS 上再跑一套虚拟机 OS |
| 部署难度 | 非常简单 | 组件多、部署复杂 |
| 启动速度 | 秒级 | 分钟级 |
| 执行性能 | 和物理机几乎一致 | 会占用较多资源 |
| 镜像体积 | MB 级别 | GB 级别 |
| 管理效率 | 管理简单 | 组件互相依赖、管理复杂 |
| 隔离性 | 相对较弱(共享内核) | 彻底 |

### 2.5 应用场景

- **面向产品**:交付模式改变——交付"镜像"而不是"环境 + 安装文档";
- **面向开发**:统一开发环境,告别"在我机器上是好的";
- **面向运维**:比 VM 性能损失更小、**秒级自动化扩容**(VM 扩容是分钟级)、适合微服务架构;
- **面向测试**:同一环境下测试多个版本;
- 典型场景:微服务、自动化部署、大规模集群。

---

## 三、Docker 三大核心概念

> Docker 三大概念:**仓库(Repository)、镜像(Image)、容器(Container)**。
> 类比 Maven/Git:**镜像 = jar 包/代码版本,仓库 = 中央仓库/远程仓库,容器 = 真正跑起来的进程实例**。

### 3.1 镜像 Image(只读模板)

- 一个**只读的模板**,用来创建容器;
- **一个镜像可以创建多个容器**;
- 来源:从公开仓库(Docker Hub)或私服仓库 `pull` 拉取,或通过 **Dockerfile 构建**自己的镜像;
- 镜像有 **tag(标签)** 区分版本,如 `redis:6.2.6`,`latest` 是最新标签。

### 3.2 容器 Container(镜像的运行实例)

- 由镜像创建的**实例**,可以被**启动、停止、运行、删除**;
- 每个容器之间**互相隔离**,可以看作一个精简的 Linux 环境;
- 类比:镜像 = 类(class),容器 = new 出来的对象(instance)。

### 3.3 仓库 Repository(存放镜像的地方)

- 类似 Maven 仓库 / Git 仓库,用来存放镜像;
- 仓库之上还有 **Registry(仓库注册服务器)**:Registry 里有很多 Repository,一个 Repository 里有很多镜像,一个镜像可以有很多 tag;
- 最大的公开仓库:**Docker Hub**(https://hub.docker.com);
- 使用方式类似 Git:用 `pull` / `push` 拉取和推送镜像。

> 层级关系记牢:**Registry(注册服务器) > Repository(仓库) > Image(镜像,带 tag) > Container(容器)**

---

## 四、在 CentOS7 上安装 Docker

### 4.1 前置要求

- Docker 官方要求 **CentOS 内核 3.10 以上**;
- 查看内核版本:`uname -r`

### 4.2 安装步骤

```bash
# 1. 更新 yum 仓库
yum update

# 2. 查看是否已装 docker,有则先卸载
yum list installed | grep docker
yum remove docker docker-common docker-selinux docker-engine   # 若有则删

# 3. 安装依赖包(yum-utils 提供 yum-config-manager)
yum install -y yum-utils device-mapper-persistent-data lvm2

# 4. 配置阿里云 yum 源
yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 5. 查看可安装版本(选需要的版本)
yum list docker-ce --showduplicates | sort -r

# 6. 安装指定版本(示例:20.10.9)
yum install docker-ce-20.10.9-3.el7 docker-ce-cli-20.10.9-3.el7 containerd.io -y

# 7. 启动 + 开机自启
systemctl start docker
systemctl enable docker

# 8. 验证
docker version
```

> 💡 老版本(18.06)安装方式相同,把版本号换掉即可。

### 4.3 配置镜像加速器(国内拉镜像必备)

原因:直连 Docker Hub 经常拉不动/超时,配好国内加速器即可。阿里云控制台「容器镜像服务 → 镜像加速器」会给每人一个专属地址,形如 `https://<你的ID>.mirror.aliyuncs.com`。

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": ["https://<你的加速器地址>"]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

也可以直接多配几个公共加速器(按需增减,公共加速器可能失效):

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://docker.nju.edu.cn"
  ]
}
```

> ⚠️ **镜像拉取不了时的通用排查**:① 检查 `/etc/docker/daemon.json` 里加速器是否可用;② 改完必须 `systemctl daemon-reload && systemctl restart docker`;③ 拉取特定版本要先确认 tag 名正确(`docker pull 镜像名:tag` 默认 `latest`)。

---

## 五、本节速记

1. 虚拟化演进:软件模拟硬件 → 硬件辅助虚拟化 → **容器共享内核**;
2. Docker 是"集装箱",解决 **环境混乱、迁移难、不安全** 三大痛点;
3. 三个概念一条链:**Registry → Repository → Image(tag) → Container**;
4. 装 Docker = 加 yum 源 → 装 docker-ce → 启动自启 → `docker version` 验证;
5. 国内使用先配好**镜像加速器**。
