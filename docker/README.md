# Docker 学习笔记 🐳

> 面向初级工程师的 Docker 学习笔记:虚拟化概念、核心概念、容器操作、镜像与 Dockerfile、数据卷与 Compose、Java 项目容器化部署。
> 原则:把课程内容整理成能"照着敲、照着复习"的笔记,命令覆盖实操全流程。本仓库只收录通用知识,不含任何业务信息。

## 学习路线

- [x] [01 Docker 入门:虚拟化与核心概念(装 Docker + 镜像加速)](./01-Docker入门-虚拟化与核心概念.md)
- [x] [02 容器操作实战(生命周期命令 + Nginx 静态网站部署)](./02-Docker容器操作实战.md)
- [x] [03 镜像与 Dockerfile 详解(commit/build + 核心指令 + CMD vs ENTRYPOINT)](./03-Docker镜像与Dockerfile详解.md)
- [x] [04 数据卷与 Docker Compose(持久化共享 + 多容器编排)](./04-数据卷与Docker-Compose.md)
- [x] [05 Java 项目容器化部署实战(SpringBoot Dockerfile + IDEA 远程部署)](./05-Java项目容器化部署实战.md)

## 笔记列表

| 日期 | 主题 | 链接 |
|------|------|------|
| 2026-09-03 | Docker 入门 + 容器操作 + 镜像/Dockerfile + 数据卷/Compose + Java 部署 | [01](./01-Docker入门-虚拟化与核心概念.md) [02](./02-Docker容器操作实战.md) [03](./03-Docker镜像与Dockerfile详解.md) [04](./04-数据卷与Docker-Compose.md) [05](./05-Java项目容器化部署实战.md) |

## 一句话速记

1. Docker = 集装箱式的"应用打包运行"工具,容器共享宿主内核,秒级启动;
2. Registry → Repository → Image(tag)→ Container,一条链记牢;
3. 容器删了数据不能丢 → 数据卷;容器多了管不过来 → docker-compose;
4. Dockerfile:FROM 起手,RUN 构建时干活,CMD/ENTRYPOINT 启动时干活,EXPOSE 只是声明端口;
5. Java 部署 = 写好 Dockerfile → build → run(-p 映射 + -v 挂载)→ push/pull 交付。
