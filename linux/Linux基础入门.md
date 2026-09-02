# Linux 入门笔记（2026-09-01）

> 来源：Linux 课程思维导图课件整理
> 目标：把课件内容整理成一份能"照着敲、照着复习"的笔记，贴合初级工程师视角。
> 学习建议：命令光看没用，**每条至少敲 3 遍**，才能形成肌肉记忆。

---

## 一、Linux基础

### 1.1 内核 vs 发行版

- **内核（kernel）**：只提供操作系统最基本的功能——内存管理、进程调度、文件管理等。
  - Linux 内核官网：https://www.kernel.org/
  - 内核是林纳斯（Linus）维护的，是"发动机"。
- **发行版（distribution）**：厂商基于 Linux 内核，集成上漂亮易用的桌面和常用软件后发布的"整装商品"。

> 一句话理解：内核是"发动机"，发行版是"整车"。

### 1.2 常见发行版

| 发行版 | 一句话特点 | 备注 |
|--------|-----------|------|
| **RedHat 系列** | 国内使用最广、资料最多 | 含 RHEL（收费）、Fedora（免费/激进）、CentOS（RHEL 社区克隆版，免费、稳定，适合做服务器） |
| **Ubuntu** | 入门首选、安装简单、社区活跃、显卡驱动友好 | 图形界面华丽，对新手友好 |
| **Debian** | 最符合开源精神、稳定、社区驱动 | Ubuntu 基于 Debian；APT 包管理 |
| **Fedora** | 新技术吸纳快、半年一版、RedHat 赞助 | YUM 包管理，默认 Gnome 桌面 |
| **openSUSE** | 德国制造、极其稳定、KDE 桌面"最华丽" | YaST 图形化管理工具 |
| **CentOS** | 几乎等于"剔除了专有代码的 RedHat" | 非常稳定，**最适合做服务器操作系统** |

### 1.3 Linux 应用领域

1. 基于 Linux 的企业服务器（网站服务器信息可查：www.netcraft.com）
2. 嵌入式应用

### 1.4 Linux 与 Windows 的不同（重点）

1. **严格区分大小写**——`File` 和 `file` 是两个东西。
2. **一切皆文件**——包括硬件设备，都以文件形式保存。
3. **不靠扩展名区分文件类型**（扩展名只是给管理员看约定）：
   - 压缩包：`.gz` `.bz2` `.tar.bz2` `.tgz` 等
   - 二进制软件包：`.rpm`
   - 网页文件：`.html` `.php`
   - 脚本文件：`.sh`
   - 配置文件：`.conf`
4. **Windows 程序不能直接装到 Linux 上运行**（平台不同、可执行文件格式不同）。
5. **多用户操作系统**——多人可同时登录使用。

### 1.5 字符界面的优势

- 占用系统资源更少（服务器跑得更省）。
- 减少出错和被攻击的可能性（没有图形"花架子"）。

---

## 二、系统安装

- **装虚拟机 VMware**：先在 Windows 里装个虚拟机软件。
- **装 CentOS 系统**：在 VMware 里安装 CentOS 操作系统。

> 环境准备阶段，先能跑起来再说。之后所有命令练习都在这个 CentOS 虚拟机里敲。

---

## 三、Linux 管理（命令主线）

> 心法：**熟能生巧，每个命令敲 3 遍以上。**

### 3.0 系统目录结构（FHS 约定）

| 目录 | 作用 | 关键点 |
|------|------|--------|
| `/` | 根目录，一切从这里开始 | 只有 root 有根目录写权限；注意 `/` 和 `/root` 不同 |
| `/bin` | 用户二进制命令 | 所有用户的命令：`ps ls ping grep cp` |
| `/sbin` | 系统二进制命令 | 管理员维护用：`iptables reboot fdisk ifconfig` |
| `/etc` | 配置文件 | 程序启动/停止脚本；`hosts` 是本地 DNS 解析（设备名→IP） |
| `/dev` | 设备文件 | 终端、USB 等：`/dev/tty1` |
| `/proc` | 进程信息（虚拟文件系统） | 系统资源以文本形式存在：`/proc/uptime` |
| `/var` | 变量文件（内容会增长） | 日志 `/var/log`、包 `/var/lib`、邮件 `/var/mail`、队列 `/var/spool` |
| `/tmp` | 临时文件 | **重启后被清空** |
| `/usr` | 用户程序 | `/usr/bin` `/usr/sbin` `/usr/lib` `/usr/local`（源码安装的软件） |
| `/home` | 普通用户的家目录 | `/home/john` `/home/nikita` |
| `/boot` | 引导加载程序文件 | 内核 `vmlinuz`、`grub`、`initrd` |
| `/lib` | 系统库 | 文件名为 `ld*` 或 `lib*.so.*` |
| `/opt` | 可选的附加应用程序 | 厂商附赠软件装这里 |
| `/mnt` | 挂载目录 | 管理员临时挂载文件系统 |
| `/media` | 可移动媒体设备 | 挂载光盘 `/media/cdrom`、软驱 `/media/floppy` |
| `/srv` | 服务数据 | 如 `/srv/cvs` |

### 3.1 ssh 协议

**SSH = Secure Shell（安全外壳协议）**：
- 建立在应用层，是专为**远程登录会话**和其他网络服务提供安全性的协议。
- 能有效防止远程管理过程中的**信息泄露**（数据加密传输）。
- 跨平台（HP-UX、Linux、AIX、Solaris 等几乎都能跑）。

### 3.2 命令基本格式

```
命令 [选项] [参数]
```

注意：
- 个别命令不遵循此格式。
- 多选项可以写一起：`-a -l -h`。
- 简化选项 = 完整选项：`-a` 等于 `--all`。

**命令提示符 `[root@localhost ~]#` 拆解：**
- `root`：当前登录用户
- `localhost`：主机名
- `~`：当前所在目录（家目录）。root 的 `~` 是 `/root`；普通用户 user1 的 `~` 是 `/home/user1`
- `#`：超级用户提示符；普通用户是 `$`

**路径：**
- 绝对路径：以 `/` 开头，如 `/etc/sysconfig`
- 相对路径：不以 `/` 开头
- `.` 当前路径，`..` 上一级（父）路径
- 公式：`绝对路径 = 相对点 + 相对路径`

**查询目录内容 `ls`：**
```bash
ls -a   # 显示所有文件（含隐藏文件）
ls -l   # 显示详细信息
ls -h   # 人性化显示文件大小（配合 -l 用）
```

### 3.3 文件处理命令（核心）

| 命令 | 英文原意 | 作用 | 常用参数/示例 |
|------|---------|------|--------------|
| `pwd` | print working directory | 查看当前所在目录位置 | `pwd` |
| `touch` | - | 创建空文件 | `touch 文件名` |
| `mkdir` | make directories | 创建目录 | `mkdir -p 目录名`（-p 递归创建） |
| `cd` | change directory | 切换目录 | `cd ~` 回家目录；`cd -` 回上次目录；`cd ..` 上一级 |
| `rmdir` | remove empty directory | 删**空**目录 | `rmdir 目录名` |
| `rm` | remove | 删文件/目录 | `rm -rf`（-r 递归 -f 强制）；**危险别乱用** |
| `cp` | copy | 复制（保留源） | `cp -r 源 目标`（-r 复制目录） |
| `mv` | move | 剪切/移动/改名（不留源） | `mv 源 目标` |

**cp / mv 区别一句话：**
- `cp`：**保留**源资源，生成一份同样内容的新资源。
- `mv`：**不保留**源资源，把内容"挪走"（可移动，也可改名）。

### 3.4 文件搜索命令

**命令搜索：**
- `whereis 命令名`：找命令所在路径 + 帮助文档位置（-b 只找可执行文件，-m 只找帮助文件）
- `which 文件名`：找命令所在路径及别名
- **PATH 环境变量**：系统搜索命令的路径（类似 Windows 的 Path）

**文件搜索 `find`：**
```bash
find / -name install.log        # 按文件名搜（注意别大范围搜，耗资源）
find /root -iname install.log   # -iname 不区分大小写
find /root -user root           # 按所有者
find /root -nouser              # 找没有所有者的文件
```

**Linux 通配符（find 里是完全匹配）：**
- `*` 匹配任意内容
- `?` 匹配任意一个字符
- `[]` 匹配括号内任意一个字符

**按时间查（mtime 修改时间）：**
```bash
find /var/log -mtime +10   # 10天前修改
-10   # 10天内
10    # 10天当天
+10   # 10天前
# atime=访问时间 ctime=改变属性时间 mtime=修改文件时间
```

**按大小查：**
```bash
find . -size 25k    # 正好25KB（k小写）
-25k  # 小于  / +25k  # 大于
find . -size 25M    # M大写（涉及 M 时）
```

**复合条件：**
```bash
find /etc -size +20k -a -size -50k
# -a and 逻辑与，-o or 逻辑或

find /etc -size +20k -a -size -50k -exec ls -lh {} \;
# -exec ls -lh {} \;  是固定格式，对查到的每个文件执行 ls -lh
```

**字符串搜索 `grep`（在文件内容里搜）：**
```bash
grep [-i] [-v] 字符串 文件名
# -i 忽略大小写  -v 排除指定字符串
```
- 过滤文件内容：`cat /root/anaconda-ks.cfg | grep System`
- 过滤命令结果：`ls /etc/sysconfig/network-scripts | grep ifcfg-ens32`
- `|` 管道符：前一个命令的结果作为后一个命令的输入（类似 SQL 的 from→where→select 层层过滤）

**find 和 grep 的区别（面试常问）：**
- `find`：在**系统中**搜索符合条件**的文件名**。
- `grep`：在**文件里**搜索符合条件的**字符串**。

### 3.5 帮助命令

```bash
man ls        # 查看 ls 的完整帮助（manual）
ls --help     # 命令选项帮助（不适用内部命令，如 cd）
help cd       # 内部命令用 help
```

### 3.6 压缩与解压缩命令

> 两个概念：**压缩** = 大变小（文件数不变）；**打包** = 多文件合成一个（大小不变）。
> .zip 等格式 = 打包 + 压缩一起做。

**① .zip 格式：**
```bash
zip 压缩文件名.zip 原文件     # 压缩文件
zip -r 压缩文件名.zip 原文件  # -r 压缩目录
unzip 压缩文件.zip -d 指定目录  # 解压到指定目录
```

**② .gz 格式（只管压缩，不打含目录）：**
```bash
gzip 原文件                     # 压缩，原文件消失
gzip -c 原文件 > 压缩文件.gz     # 压缩且保留原文件（-c 输出到控制台，> 重定向）
gzip -d 压缩文件.gz             # 解压（或 gunzip 压缩文件.gz）
```

**③ .bz2 格式：**
```bash
bzip2 源文件        # 压缩，不能保留源文件
bzip2 -k 源文件      # 压缩并保留源文件（不能压目录）
bzip2 -d 压缩文件    # 解压（或 bunzip2 压缩文件）
```

**④ 打包/解打包 `tar`：**
```bash
tar -cvf 打包文件.tar 源文件   # -c 打包 -v 显示过程 -f 指定打包文件名
tar -xvf 打包文件.tar          # -x 解打包
```

**⑤ .tar.gz（先打包再压缩，对标 zip）：**
```bash
tar -zcvf 压缩包.tar.gz 源文件   # -z 压缩成 .tar.gz
tar -zxvf 压缩包.tar.gz          # -x 解压
tar -zxvf 压缩包.tar.gz -C 指定目录  # -C 解压到指定目录
```

**⑥ .tar.bz2：**
```bash
tar -jcvf 压缩包.tar.bz2 源文件  # 压缩
tar -jxvf 压缩包.tar.bz2         # 解压
```

> 记法：z= gz，j= bz2，c=创造(压缩)，x=解压，v=显示，f=文件，C=指定目录。

### 3.7 关机与重启

```bash
shutdown -h now    # 立即关机（-h 关机）
shutdown -r now    # 立即重启（-r 重启）
shutdown -c        # 取消之前的关机命令
```
> 用 shutdown 关机时**会帮我们保存数据**，比下面这些安全。

**其他关机命令（不安全，不保存数据）：** `halt`、`poweroff`、`init 0`
**其他重启命令：** `reboot`、`init 6`

**系统运行级别：**
| 级别 | 含义 |
|------|------|
| 0 | 关机 |
| 1 | 单用户 |
| 2 | 不完全多用户（不含 NFS） |
| 3 | 完全多用户（字符界面） |
| 4 | 未分配 |
| 5 | 图形界面 |
| 6 | 重启 |

```bash
runlevel                # 查看当前运行级别
cat /etc/inittab        # 修改默认运行级别
logout                  # 退出登录
```

### 3.8 查看信息类命令

```bash
w 用户名        # 查看用户登录信息（USER TTY FROM LOGIN@ IDLE JCPU PCPU WHAT）
who             # 查看谁登录了（用户名/终端/登录时间来源IP）
last            # 查看当前和过去登录记录（读 /var/log/wtmp）
lastlog         # 查看每个用户最后一次登录时间（读 /var/log/lastlog）
cat /etc/redhat-release   # 查系统版本
df -h           # 磁盘使用情况（-h 格式化显示）
top             # 查看任务进程（类似任务管理器）
free            # 查看内存占用（total used free buffers cached）
history         # 查看历史命令
echo "内容"     # 在显示器输出内容
```

**文件查看：**
```bash
cat 文件    # 显示整个文件内容
tail 文件   # 默认看末尾 10 行
tail -f 文件  # 实时看最新追加的内容（看日志神器）
```

---

## 四、Vi 编辑器（必须熟练）

### 4.1 三种模式

| 模式 | 说明 |
|------|------|
| **编辑模式（命令模式）** | 默认模式，按键被当作编辑命令 |
| **输入模式** | 按键被当作输入的字符 |
| **末行模式** | 输入文件管理命令（保存、退出、替换等） |

**模式切换：**
- 编辑 → 输入：`i`（光标前插入）`a`（光标后插入）`o`（下一行插入）`s`（删当前字符并插入）
  - 大写 `I` 行首插入、`A` 行尾插入、`O` 上一行插入、`S` 删当前行并插入
- 输入 → 编辑：按 `ESC`
- 编辑 → 末行：按 `:` 
- 末行 → 编辑：按 `ESC`

### 4.2 常用操作

```bash
vi /path/to/file   # 打开文件
# 末行模式：
wq      # 保存退出
q!      # 退出不保存
```

**移动光标（编辑模式）：**
- 逐字符：`h` 左、`l` 右、`j` 下、`k` 上
- 行内跳转（末行模式）：`0` 行首、`$` 行尾
- 整页跳转（编辑模式）：`G` 最后一行、`gg` 第一行

**翻屏（编辑模式）：** `ctrl+f` 下一页、`ctrl+b` 上一页

**删除：**
- `dd` 删光标所在行；`3dd` 删 3 行（编辑模式）
- `: 1,4d` 删第 1~4 行（末行模式）

**复制粘贴：**
- `yy` 复制光标行；`2yy` 复制 2 行（编辑模式）
- `p` 粘贴

**查找替换：**
- 查找：`/pattern`（从前向后）、`?pattern`（从后向前）、`n` 下一个、`N` 上一个
- 替换（末行模式）：`startNum,endNums/partter/string/gi`
  - `g` 全局替换、`i` 忽略大小写
  - 例：`%s/f/F/gi` 全文把 f 替换成 F（忽略大小写）

---

## 五、权限管理

### 5.1 用户管理（一切皆文件）

**权限三要素（针对一个资源）：**
1. **属主（u）**：文件所有者对这个资源的权限
2. **属组（g）**：文件所属组对这个资源的权限
3. **其他用户（o）**：既不是属主也不是属组的其他人

**文件权限位：**
- `r` 可读（能 cat）
- `w` 可写（能编辑/删除）
- `x` 可执行

**用户相关命令：**
```bash
useradd 用户名                  # 创建用户
useradd -G 组名 用户名          # 创建用户并分配到组
cat /etc/passwd                # 查看系统用户（每行 7 字段）
groupadd 组名                  # 创建组
cat /etc/group                 # 查看用户组
usermod -G 组名 用户名          # 修改用户所属组
userdel -f 用户名              # 强制删除（-f）
userdel -r 用户名              # 删除用户及其所有文件（-r）
groupdel 组名                  # 删除组
passwd                        # 修改密码（存在 /etc/shadow）
```

**/etc/passwd 每行 7 字段：**
```
用户名 : 密码(x) : uid : gid : 账号说明 : 家目录 : shell
```
- uid：root 为 0；1-499（有的到 1000）系统账号；500-65535（有的从 1000 起）可登录账号
- 登录流程：输用户名密码 → 查 /etc/passwd 有无该账号 → 读出 UID/GID、家目录、shell → 验密码 → 正确则登录
- shell 若为 `/sbin/nologin`，则该账号**没有登录环境**（不能登录）

### 5.2 文件基本权限

**看懂 `-rw-r--r--`：**
- 第 1 位：文件类型（`-` 文件、`d` 目录、`l` 软链接）
- 后 9 位分 3 组：`rw-`(u) `r--`(g) `r--`(o)

**修改权限 `chmod`：**
```bash
chmod [选项] 模式 文件名
# 选项：-R 递归
# 模式：u/g/o/a +-= rwx，或数字
```

**方式一（字母式）：**
```bash
chmod u+x 文件          # 给属主加 x
chmod g+w,o+w 文件      # 给属组和其他人加 w
chmod a=rwx 文件        # 所有人 =rwx
```

**方式二（数字式）：**
```
r=4  w=2  x=1
rwx=7  rw-=6  r--=4
rwxr-xr-x = 755

chmod 755 文件
```

**其他权限命令：**
```bash
chown 用户名 文件名   # 修改文件所有者
chgrp 组名 文件名     # 修改文件所属组
```

### 5.3 sudo 权限

- sudo：root 把**超级用户才能执行的命令**授权给普通用户执行；操作对象是**系统命令**。
- `visudo`：实际修改的是 `/etc/sudoers` 文件。
- 授权行格式：`用户名 被管理主机地址=(可用身份) 授权命令(绝对路径)`
  - `root ALL=(ALL) ALL`
  - `%wheel ALL=(ALL) ALL`（`%组名` 表示授权给整个组）

---

## 六、系统服务管理（CentOS 7）

**systemctl 是 CentOS 7 的服务管理主工具：**
```bash
systemctl start <服务名>      # 启动
systemctl stop <服务名>       # 关闭
systemctl restart <服务名>    # 重启
systemctl status <服务名>     # 查看状态
systemctl enable <服务名>     # 开机自启
systemctl disable <服务名>    # 禁止开机自启
systemctl list-unit-files     # 查看开机启动项
```

**查看进程 `ps`：**
```bash
ps -ef
# UID PID PPID C STIME TTY TIME CMD
# 相当于是"任务管理器"
```

**杀死进程：**
```bash
kill -9 pid    # 强制结束进程
```

---

## 七、网络管理

### 7.1 基本概念

| 概念 | 比喻 | 说明 |
|------|------|------|
| IP 地址 | 手机号码 | 网络通信中主机的标识符 |
| MAC 地址 | 身份证号码 | 物理网卡的唯一标识符 |
| 子网掩码 | - | 区分网络地址和主机地址，确定网段 |
| 网关 | 关口 | 一个网络主机连接另一个网络主机的关口 |
| DNS | 通讯录 | 把域名解析成 IP 地址 |

### 7.2 网卡配置文件

位置：`/etc/sysconfig/network-scripts/ifcfg-eth0`（`ifcfg-eth1/2...` 依次）

```
TYPE=Ethernet          # 网卡类型
DEVICE=ens32           # 网卡接口名称
ONBOOT=yes             # 开机是否自动加载
BOOTPROTO=static       # 地址协议：static静态 / dhcp动态
IPADDR=192.168.x.x     # 网卡 IP（示例，按需改成自己网段）
NETMASK=255.255.255.0  # 子网掩码
GATEWAY=192.168.x.1    # 网关（示例，通常是同网段的 .1/.2）
DNS1=114.114.114.114   # DNS
```

```bash
ip addr    # 查看 IP
```

### 7.3 防火墙（CentOS 7：firewalld）

```bash
firewall-cmd --help                                        # 帮助
firewall-cmd --state                                       # 查看状态
firewall-cmd --zone=public --list-ports                    # 查看所有开放的端口
firewall-cmd --zone=public --add-port=8080/tcp --permanent # 开启端口(永久生效)
firewall-cmd --reload                                      # 更新规则
firewall-cmd --zone=public --remove-port=8080/tcp --permanent  # 删除端口
```

---

## 八、Linux 应用（软件安装与部署）

### 8.1 软件包管理器

- **后端工具**：`rpm`（RedHat 的工业标准）、`dpt`（Debian）
- **依赖管理**：安装 A 可能要 B，B 又要 C，形成依赖链
- **前端工具**：`yum`（解决依赖问题，底层调 rpm）、`apt-get`

### 8.2 rpm 常用命令

```bash
rpm -ivh 包名.rpm        # 安装（-i 安装 -v 显示 -h 进度）
rpm -ivh --nodeps --force 包名   # 忽略依赖、强制安装（慎用）

rpm -q 软件名            # 查询是否安装
rpm -qi 软件名           # 查看包信息
rpm -ql 软件名           # 列出包内文件
rpm -qf 文件或目录       # 该文件属于哪个包
rpm -qa                 # 列出所有已装 rpm 包
rpm -e 软件名            # 卸载
```

### 8.3 yum 常用命令

```bash
yum install 包名 -y      # 安装（-y 自动确认）
yum list | grep mysql    # 搜索软件包
yum list installed       # 查看已装
yum remove 包名          # 卸载
```

**修改 yum 源为阿里源：**
```bash
yum install wget -y
mv /etc/yum.repos.d/CentOS-Base.repo /etc/yum.repos.d/CentOS-Base.repo_bak
wget -O /etc/yum.repos.d/CentOS-Base.repo http://mirrors.aliyun.com/repo/Centos-7.repo
yum makecache
yum -y update
```

### 8.4 源码安装（以 Redis 为例，完整流程）

```bash
# 1. 上传安装包到 /root/software
# 2. 解压到指定目录
cd /root/software
tar -zxvf redis-6.2.1.tar.gz -C /usr/local
# 3. 进入目录
cd /usr/local/redis-6.2.1/
# 4. 安装 gcc 编译器
yum install -y gcc
# 5. 编译 + 安装
make
cd /usr/local/redis-6.2.1/src
make install
# 6. 修改配置（后台运行、允许远程）
vi /usr/local/redis-6.2.1/redis.conf
#   daemonize yes     后台运行
#   protected-mode no 关闭保护模式
#   bind 0.0.0.0      允许任意 IP 连接（默认注释掉 bind 127.0.0.1）
# 7. 启动
/usr/local/redis-6.2.1/src/redis-server /usr/local/redis-6.2.1/redis.conf
# 8. 验证
ps -ef | grep redis
netstat -tunpl | grep 6379
```

### 8.5 部署要点（JDK / Tomcat / MySQL）

- **JDK / Tomcat 安装**：解压 tar.gz（解开即可用）；源码安装需先编译。
- **MySQL 安装**（rpm 方式）：安装前**先关防火墙**和相关安全策略。
```bash
rpm -ivh --nodeps --force mysql-community-common-5.7.22-1.el7.x86_64.rpm \
  mysql-community-libs-... mysql-community-client-... mysql-community-server-...
```
- **MySQL 远程授权：**
```sql
grant all privileges on *.* to 'root'@'%' identified by 'admin' with grant option;
flush privileges;
```
- **大小写敏感**：默认 Linux 下 MySQL 表名区分大小写。
  `show variables like "%case%";` 或配置 `/etc/my.cnf` 加 `lower_case_table_names=1`
- **中文乱码**：连接串加 `useUnicode=true&characterEncoding=utf-8`

---

## 九、今日要记住的点（自测清单）

- [ ] 内核 vs 发行版怎么区分？
- [ ] Linux 和 Windows 5 点区别（大小写/一切皆文件/不靠扩展名/程序不通用/多用户）？
- [ ] `/bin` 和 `/sbin`、`/etc`、`/var`、`/tmp` 各存什么？
- [ ] `cp` 和 `mv` 的本质区别？
- [ ] `find`（找文件名）和 `grep`（找字符串）区别？
- [ ] `tar -zcvf` / `-zxvf`、`-C` 是什么意思？
- [ ] 运行级别 0/3/5/6 各代表什么？
- [ ] Vi 三种模式怎么切换？`wq`/`q!`/`dd`/`yy`/`p` 记得吗？
- [ ] `-rw-r--r--` 怎么读？rwx=421 怎么换算成 755？
- [ ] `chmod`、`chown`、`chgrp` 分别改什么？
- [ ] `/etc/passwd` 7 字段分别是什么？
- [ ] 防火墙怎么开端口？`systemctl` 怎么管理服务？
- [ ] Redis 源码安装完整流程能默写吗？
