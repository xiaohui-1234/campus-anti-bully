# MinIO 管理工具 `mc` 使用手册大全

> 适用场景：本手册用于项目开发、部署、运维、测试环境中使用 MinIO Client（`mc`）管理 MinIO / S3 兼容对象存储。
>
> `mc` 是 MinIO 官方命令行客户端，类似 Linux 下的 `ls`、`cp`、`mv`、`rm`、`find`、`mirror` 等工具，但操作对象可以是本地文件系统、MinIO、Amazon S3、以及其他 S3 兼容对象存储。

---

## 目录

- [1. mc 是什么](#1-mc-是什么)
- [2. 核心概念](#2-核心概念)
- [3. 安装 mc](#3-安装-mc)
- [4. 配置 MinIO 连接 alias](#4-配置-minio-连接-alias)
- [5. 基础命令结构](#5-基础命令结构)
- [6. 桶 Bucket 管理](#6-桶-bucket-管理)
- [7. 文件 / 对象 Object 管理](#7-文件--对象-object-管理)
- [8. 目录模拟与路径规则](#8-目录模拟与路径规则)
- [9. 批量上传、下载、复制、移动](#9-批量上传下载复制移动)
- [10. 同步与备份 mirror](#10-同步与备份-mirror)
- [11. 查找对象 find](#11-查找对象-find)
- [12. 删除对象与危险操作](#12-删除对象与危险操作)
- [13. 公开访问、私有访问与匿名策略](#13-公开访问私有访问与匿名策略)
- [14. 预签名链接 share](#14-预签名链接-share)
- [15. 用户、组、策略管理 admin](#15-用户组策略管理-admin)
- [16. 常用权限策略 JSON](#16-常用权限策略-json)
- [17. 桶版本控制 versioning](#17-桶版本控制-versioning)
- [18. 生命周期 lifecycle](#18-生命周期-lifecycle)
- [19. 对象锁、保留与合规能力](#19-对象锁保留与合规能力)
- [20. 服务状态与运维排查](#20-服务状态与运维排查)
- [21. mc 与 Spring Boot 项目配合方式](#21-mc-与-spring-boot-项目配合方式)
- [22. Windows 常见用法](#22-windows-常见用法)
- [23. Linux 常见用法](#23-linux-常见用法)
- [24. Docker 环境中使用 mc](#24-docker-环境中使用-mc)
- [25. 常见错误与解决办法](#25-常见错误与解决办法)
- [26. 常用命令速查表](#26-常用命令速查表)
- [27. 推荐项目实践规范](#27-推荐项目实践规范)

---

# 1. mc 是什么

`mc` 全称通常叫 **MinIO Client**，是 MinIO 官方提供的命令行管理工具。

它可以做这些事：

1. 连接 MinIO 服务端。
2. 创建、删除、查看 bucket。
3. 上传、下载、复制、移动、删除文件。
4. 批量同步本地目录和 MinIO bucket。
5. 生成临时访问链接。
6. 设置公开访问规则。
7. 管理用户、用户组、权限策略。
8. 查看服务状态、磁盘状态、集群状态。
9. 配置版本控制、生命周期、对象锁等高级功能。

简单理解：

```text
MinIO Console：网页管理后台，适合人工点点点。
mc：命令行管理工具，适合运维、脚本、自动化、批量操作。
Spring Boot SDK：业务代码里访问 MinIO 的方式。
```

---

# 2. 核心概念

## 2.1 MinIO 服务端

MinIO 服务端是真正存文件的对象存储服务。

常见访问端口：

```text
9000：S3 API 端口，程序、mc、SDK 通常访问这个端口。
9001：Console 控制台端口，浏览器管理后台通常访问这个端口。
```

示例：

```text
http://127.0.0.1:9000      # API 地址
http://127.0.0.1:9001      # 控制台地址
```

---

## 2.2 Bucket 桶

Bucket 类似顶级文件夹，也可以理解为一个存储空间。

例如：

```text
avatar
upload
processed
audio
video
logs
```

对象路径示例：

```text
avatar/1.png
upload/user-1/2026/05/07/test.jpg
processed/user-1/2026/05/07/result.jpg
```

在 MinIO 里，真正的 bucket 是 `avatar`、`upload`、`processed`。

后面的 `user-1/2026/05/07/test.jpg` 是 object key，不是真正的文件夹。

---

## 2.3 Object 对象

Object 就是存进去的文件。

对象由两部分组成：

```text
bucket + object key
```

例如：

```text
upload/user-1/a.jpg
```

含义是：

```text
bucket = upload
object key = user-1/a.jpg
```

---

## 2.4 Alias 别名

`mc` 不会让你每次都输入完整地址、账号、密码。

它用 alias 保存连接配置。

例如：

```bash
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
```

之后就可以用：

```bash
mc ls local
```

而不是每次写完整连接信息。

---

# 3. 安装 mc

## 3.1 Windows 安装

### 方法一：直接下载 `mc.exe`

打开 PowerShell：

```powershell
Invoke-WebRequest -Uri "https://dl.min.io/client/mc/release/windows-amd64/mc.exe" -OutFile "mc.exe"
```

测试：

```powershell
.\mc.exe --version
```

为了方便全局使用，可以把 `mc.exe` 放到某个固定目录，例如：

```text
C:\tools\minio\mc.exe
```

然后把这个目录加入系统环境变量 `Path`。

加入后测试：

```powershell
mc --version
```

---

## 3.2 Linux 安装

### x86_64 Linux

```bash
curl -O https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/mc
mc --version
```

### ARM64 Linux

树莓派、ARM 服务器可能使用 ARM64：

```bash
curl -O https://dl.min.io/client/mc/release/linux-arm64/mc
chmod +x mc
sudo mv mc /usr/local/bin/mc
mc --version
```

---

## 3.3 macOS 安装

```bash
brew install minio/stable/mc
mc --version
```

或者：

```bash
curl -O https://dl.min.io/client/mc/release/darwin-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/mc
mc --version
```

Apple Silicon 可以使用 ARM64 版本。

---

# 4. 配置 MinIO 连接 alias

## 4.1 添加 alias

命令格式：

```bash
mc alias set <别名> <MinIO API 地址> <AccessKey> <SecretKey>
```

示例：

```bash
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
```

如果你的 MinIO 部署在服务器上：

```bash
mc alias set prod http://47.116.xxx.xxx:9000 minioadmin minioadmin
```

如果使用 HTTPS：

```bash
mc alias set prod https://oss.example.com minioadmin minioadmin
```

---

## 4.2 查看 alias

```bash
mc alias list
```

或：

```bash
mc alias ls
```

---

## 4.3 删除 alias

```bash
mc alias remove local
```

或：

```bash
mc alias rm local
```

---

## 4.4 查看某个 alias 是否可用

```bash
mc ls local
```

如果能列出 bucket，说明连接成功。

---

## 4.5 配置文件位置

`mc` 的配置通常保存在用户目录下：

### Linux

```text
~/.mc/config.json
```

### Windows

通常在：

```text
C:\Users\你的用户名\.mc\config.json
```

不建议手动修改，优先使用：

```bash
mc alias set
mc alias remove
```

---

# 5. 基础命令结构

`mc` 的基本格式：

```bash
mc <命令> <参数>
```

例如：

```bash
mc ls local
mc mb local/images
mc cp a.jpg local/images/
mc rm local/images/a.jpg
```

MinIO 路径格式：

```text
alias/bucket/object-key
```

示例：

```text
local/avatar/1.png
local/upload/user-1/test.jpg
```

含义：

```text
alias = local
bucket = avatar
object key = 1.png
```

---

# 6. 桶 Bucket 管理

## 6.1 查看所有 bucket

```bash
mc ls local
```

输出类似：

```text
[2026-05-07 10:00:00 CST]     0B avatar/
[2026-05-07 10:00:00 CST]     0B upload/
[2026-05-07 10:00:00 CST]     0B processed/
```

---

## 6.2 创建 bucket

命令格式：

```bash
mc mb <alias>/<bucket>
```

示例：

```bash
mc mb local/avatar
mc mb local/upload
mc mb local/processed
mc mb local/audio
mc mb local/video
```

---

## 6.3 删除空 bucket

```bash
mc rb local/test-bucket
```

注意：bucket 必须为空，否则删除失败。

---

## 6.4 强制删除 bucket 及里面所有对象

危险命令：

```bash
mc rb --force local/test-bucket
```

更危险：递归删除大量数据前一定确认 alias 和 bucket。

建议先执行：

```bash
mc ls local/test-bucket
```

再执行删除。

---

## 6.5 查看 bucket 磁盘占用

```bash
mc du local/upload
```

递归查看：

```bash
mc du --recursive local/upload
```

---

## 6.6 查看 bucket 信息

```bash
mc stat local/upload
```

---

# 7. 文件 / 对象 Object 管理

## 7.1 查看 bucket 下对象

```bash
mc ls local/upload
```

递归查看：

```bash
mc ls --recursive local/upload
```

显示更详细信息：

```bash
mc ls --recursive --summarize local/upload
```

---

## 7.2 上传单个文件

```bash
mc cp ./test.jpg local/upload/
```

上传后对象路径为：

```text
local/upload/test.jpg
```

---

## 7.3 上传并指定对象名

```bash
mc cp ./test.jpg local/upload/user-1-2026-05-07.jpg
```

---

## 7.4 上传到模拟目录

```bash
mc cp ./test.jpg local/upload/user-1/2026/05/07/test.jpg
```

注意：MinIO 没有真正的多级目录，这里的 `/` 是 object key 的一部分。

---

## 7.5 下载单个文件

```bash
mc cp local/upload/test.jpg ./test.jpg
```

下载到当前目录：

```bash
mc cp local/upload/test.jpg .
```

---

## 7.6 查看对象信息

```bash
mc stat local/upload/test.jpg
```

可以看到对象大小、类型、ETag、修改时间等信息。

---

## 7.7 查看对象内容

适合文本文件：

```bash
mc cat local/upload/readme.txt
```

查看前几行：

```bash
mc head local/upload/readme.txt
```

查看后几行：

```bash
mc tail local/upload/log.txt
```

---

## 7.8 重命名对象

MinIO 对象存储没有传统意义上的“原地重命名”。

`mc mv` 本质通常是：

```text
复制到新 key + 删除旧 key
```

示例：

```bash
mc mv local/upload/old.jpg local/upload/new.jpg
```

---

## 7.9 复制对象

同一个 bucket 内复制：

```bash
mc cp local/upload/a.jpg local/upload/b.jpg
```

跨 bucket 复制：

```bash
mc cp local/upload/a.jpg local/processed/a.jpg
```

跨 MinIO 服务复制：

```bash
mc cp local/upload/a.jpg prod/upload/a.jpg
```

---

# 8. 目录模拟与路径规则

MinIO 是对象存储，不是传统文件系统。

传统文件系统：

```text
upload/
  user-1/
    2026/
      test.jpg
```

MinIO 实际存储的是一个 object key：

```text
user-1/2026/test.jpg
```

所以：

```bash
mc cp test.jpg local/upload/user-1/2026/test.jpg
```

并不是先创建目录再放文件，而是直接创建了一个 key 为：

```text
user-1/2026/test.jpg
```

的对象。

---

# 9. 批量上传、下载、复制、移动

## 9.1 上传整个目录

```bash
mc cp --recursive ./images local/upload/
```

如果本地结构是：

```text
images/a.jpg
images/b.jpg
```

上传后通常是：

```text
local/upload/images/a.jpg
local/upload/images/b.jpg
```

如果只想上传目录里的内容：

```bash
mc cp --recursive ./images/ local/upload/
```

---

## 9.2 下载整个目录

```bash
mc cp --recursive local/upload/images ./downloaded-images
```

---

## 9.3 批量移动

```bash
mc mv --recursive local/upload/tmp/ local/upload/archive/
```

---

## 9.4 批量复制到另一个 bucket

```bash
mc cp --recursive local/upload/ local/backup-upload/
```

---

# 10. 同步与备份 mirror

`mc mirror` 是非常重要的命令，适合做目录同步、迁移、备份。

## 10.1 本地目录同步到 MinIO

```bash
mc mirror ./data local/backup/data
```

含义：

```text
把 ./data 的内容同步到 local/backup/data
```

---

## 10.2 MinIO 同步到本地

```bash
mc mirror local/backup/data ./data-backup
```

---

## 10.3 MinIO bucket 之间同步

```bash
mc mirror local/upload prod/upload
```

---

## 10.4 开启覆盖更新

```bash
mc mirror --overwrite ./data local/backup/data
```

---

## 10.5 删除目标端多余文件

```bash
mc mirror --remove ./data local/backup/data
```

注意：`--remove` 会删除目标端有、源端没有的对象。

非常危险，建议先测试。

---

## 10.6 持续监听同步

```bash
mc mirror --watch ./data local/backup/data
```

适合开发环境或轻量同步。

生产环境更建议配合定时任务、系统服务、日志监控使用。

---

## 10.7 定时备份示例

Linux crontab：

```bash
crontab -e
```

每天凌晨 2 点把 MinIO 的 `upload` bucket 备份到本地：

```cron
0 2 * * * /usr/local/bin/mc mirror local/upload /data/minio-backup/upload >> /var/log/minio-backup.log 2>&1
```

---

# 11. 查找对象 find

## 11.1 按名称查找

```bash
mc find local/upload --name "*.jpg"
```

---

## 11.2 查找某个用户的文件

```bash
mc find local/upload --name "user-1*"
```

---

## 11.3 查找大于指定大小的文件

```bash
mc find local/upload --larger 10MiB
```

---

## 11.4 查找小于指定大小的文件

```bash
mc find local/upload --smaller 1MiB
```

---

## 11.5 查找后执行删除

危险命令：

```bash
mc find local/upload --name "*.tmp" --exec "mc rm {}"
```

建议先只查找：

```bash
mc find local/upload --name "*.tmp"
```

确认无误后再删除。

---

# 12. 删除对象与危险操作

## 12.1 删除单个对象

```bash
mc rm local/upload/test.jpg
```

---

## 12.2 删除多个对象

```bash
mc rm local/upload/a.jpg local/upload/b.jpg
```

---

## 12.3 递归删除目录前缀

```bash
mc rm --recursive local/upload/tmp/
```

---

## 12.4 强制删除

```bash
mc rm --recursive --force local/upload/tmp/
```

---

## 12.5 删除前建议

删除前先看：

```bash
mc ls --recursive local/upload/tmp/
```

然后再删：

```bash
mc rm --recursive --force local/upload/tmp/
```

生产环境建议：

1. 先启用版本控制。
2. 再设置生命周期。
3. 删除前导出清单。
4. 不要直接对根路径执行递归删除。

极度危险示例：

```bash
mc rm --recursive --force local/upload
```

如果路径写错，可能会删除整个 bucket 内的数据。

---

# 13. 公开访问、私有访问与匿名策略

MinIO 默认通常是私有访问。

如果对象私有，浏览器直接访问对象 URL 会失败。

常见访问策略：

```text
private：私有，必须鉴权。
public download：公开读，任何人可以下载。
custom policy：自定义权限。
presigned url：临时授权链接。

权限策略
none/download/upload/public
```

---

## 13.1 查看匿名访问规则

```bash
mc anonymous get local/avatar
```

---

## 13.2 设置 bucket 公开下载

```bash
mc anonymous set download local/avatar
```

适合头像、公开图片、前端静态资源。

设置后，用户可以直接通过 URL 访问对象。

例如：

```text
http://服务器IP:9000/avatar/1.png
```

---

## 13.3 设置某个前缀公开

只公开 `public/` 前缀：

```bash
mc anonymous set download local/upload/public/
```

这样：

```text
local/upload/public/a.jpg
```

可以公开访问，但其他对象仍然私有。

---

## 13.4 取消公开访问

```bash
mc anonymous set none local/avatar
```

---

## 13.5 查看匿名策略 JSON

```bash
mc anonymous get-json local/avatar
```

---

## 13.6 导入匿名策略 JSON

```bash
mc anonymous set-json policy.json local/avatar
```

---

# 14. 预签名链接 share

预签名链接适合：

1. 私有文件临时下载。
2. 私有文件临时上传。
3. 后端生成链接，小程序 / 前端 / 设备端短时间使用。

---

## 14.1 生成临时下载链接

```bash
mc share download local/upload/test.jpg
```

设置有效期：

```bash
mc share download --expire 1h local/upload/test.jpg
```

常见时间单位：

```text
30m：30 分钟
1h：1 小时
24h：24 小时
7d：7 天
```

---

## 14.2 生成临时上传链接

```bash
mc share upload local/upload/
```

设置有效期：

```bash
mc share upload --expire 30m local/upload/
```

设备端可以拿到这个链接后直接上传文件。

但在企业级项目中，通常更推荐：

```text
设备端 / 前端 -> Spring Boot 鉴权 -> 后端生成预签名 URL -> 客户端直传 MinIO
```

而不是让客户端长期持有 AccessKey 和 SecretKey。

---

# 15. 用户、组、策略管理 admin

`mc admin` 用于管理 MinIO 服务端。

注意：需要管理员权限。

---

## 15.1 查看服务信息

```bash
mc admin info local
```

---

## 15.2 查看用户列表

```bash
mc admin user list local
```

或：

```bash
mc admin user ls local
```

---

## 15.3 添加用户

```bash
mc admin user add local appuser appuser123456
```

格式：

```bash
mc admin user add <alias> <accessKey> <secretKey>
```

建议：

1. 不要用弱密码。
2. 不要在生产环境继续使用 `minioadmin/minioadmin`。
3. 不同业务系统使用不同用户。
4. 不同环境使用不同用户。

---

## 15.4 禁用用户

```bash
mc admin user disable local appuser
```

---

## 15.5 启用用户

```bash
mc admin user enable local appuser
```

---

## 15.6 删除用户

```bash
mc admin user remove local appuser
```

或：

```bash
mc admin user rm local appuser
```

---

## 15.7 创建用户组

```bash
mc admin group add local appgroup appuser
```

---

## 15.8 查看用户组

```bash
mc admin group list local
```

---

## 15.9 查看组信息

```bash
mc admin group info local appgroup
```

---

## 15.10 向组添加用户

```bash
mc admin group add local appgroup user2
```

---

## 15.11 从组中移除用户

```bash
mc admin group remove local appgroup user2
```

---

## 15.12 查看已有策略

```bash
mc admin policy list local
```

常见内置策略可能包括：

```text
readonly
readwrite
writeonly
consoleAdmin
diagnostics
```

具体以当前 MinIO 版本为准。

---

## 15.13 查看策略内容

```bash
mc admin policy info local readwrite
```

---

## 15.14 创建自定义策略

准备 `policy.json`：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::upload/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::upload"
      ]
    }
  ]
}
```

添加策略：

```bash
mc admin policy create local upload-readwrite policy.json
```

部分旧版本可能使用：

```bash
mc admin policy add local upload-readwrite policy.json
```

以 `mc admin policy --help` 显示为准。

---

## 15.15 给用户绑定策略

```bash
mc admin policy attach local upload-readwrite --user appuser
```

旧版本可能使用：

```bash
mc admin policy set local upload-readwrite user=appuser
```

建议优先使用新版：

```bash
mc admin policy attach
```

---

## 15.16 给组绑定策略

```bash
mc admin policy attach local upload-readwrite --group appgroup
```

---

## 15.17 解除用户策略

```bash
mc admin policy detach local upload-readwrite --user appuser
```

---

## 15.18 删除策略

```bash
mc admin policy remove local upload-readwrite
```

或：

```bash
mc admin policy rm local upload-readwrite
```

---

# 16. 常用权限策略 JSON

## 16.1 只读某个 bucket

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject"
      ],
      "Resource": [
        "arn:aws:s3:::avatar/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::avatar"
      ]
    }
  ]
}
```

适合只下载头像、公开图片读取服务。

---

## 16.2 只允许上传，不允许删除

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::upload/*"
      ]
    }
  ]
}
```

适合设备端上传，但不允许设备端删除数据。

---

## 16.3 上传、读取、删除某个 bucket

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::upload/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::upload"
      ]
    }
  ]
}
```

适合后端服务账号。

---

## 16.4 多 bucket 后端服务账号策略

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::avatar/*",
        "arn:aws:s3:::upload/*",
        "arn:aws:s3:::processed/*",
        "arn:aws:s3:::audio/*",
        "arn:aws:s3:::video/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::avatar",
        "arn:aws:s3:::upload",
        "arn:aws:s3:::processed",
        "arn:aws:s3:::audio",
        "arn:aws:s3:::video"
      ]
    }
  ]
}
```

---

# 17. 桶版本控制 versioning

版本控制可以防止误删、误覆盖。

开启后，同一个 object key 可以保留多个版本。

---

## 17.1 查看版本控制状态

```bash
mc version info local/upload
```

---

## 17.2 开启版本控制

```bash
mc version enable local/upload
```

---

## 17.3 暂停版本控制

```bash
mc version suspend local/upload
```

注意：暂停不等于删除已有历史版本。

---

## 17.4 查看对象历史版本

```bash
mc ls --versions local/upload/test.jpg
```

---

## 17.5 恢复旧版本思路

一般流程：

1. 查看对象版本。
2. 找到需要恢复的版本 ID。
3. 使用 `mc cp` 或相关版本参数复制旧版本为当前版本。

不同版本的 `mc` 参数可能略有差异，执行前建议查看：

```bash
mc ls --help
mc cp --help
```

---

# 18. 生命周期 lifecycle

生命周期规则用于自动清理旧文件、旧版本、临时文件。

适合场景：

```text
临时上传文件 7 天后删除
日志文件 30 天后删除
旧版本 90 天后删除
```

---

## 18.1 查看生命周期规则

```bash
mc ilm ls local/upload
```

---

## 18.2 添加生命周期规则：30 天后删除

```bash
mc ilm rule add local/upload --expire-days 30
```

---

## 18.3 只对某个前缀生效

```bash
mc ilm rule add local/upload --prefix tmp/ --expire-days 7
```

含义：

```text
local/upload/tmp/ 下的对象 7 天后自动过期删除
```

---

## 18.4 删除生命周期规则

先查看规则：

```bash
mc ilm ls local/upload
```

然后根据规则 ID 删除：

```bash
mc ilm rule rm local/upload <rule-id>
```

---

# 19. 对象锁、保留与合规能力

对象锁用于防止对象在指定时间内被删除或覆盖。

常见模式：

```text
governance：治理模式，有权限的管理员可以绕过。
compliance：合规模式，通常更严格，到期前不能删除。
```

创建 bucket 时启用对象锁：

```bash
mc mb --with-lock local/audit-logs
```

查看锁相关帮助：

```bash
mc retention --help
mc legalhold --help
```

生产环境慎用对象锁。开启前应确认业务确实需要强合规保留，否则可能导致数据无法删除，占用大量存储。

---

# 20. 服务状态与运维排查

## 20.1 查看 MinIO 服务信息

```bash
mc admin info local
```

可以查看：

1. 服务器节点。
2. 磁盘状态。
3. 存储用量。
4. 版本信息。
5. 在线状态。

---

## 20.2 查看服务配置

```bash
mc admin config get local
```

查看某项配置：

```bash
mc admin config get local region
```

---

## 20.3 查看日志

```bash
mc admin trace local
```

只看 S3 请求：

```bash
mc admin trace --type s3 local
```

---

## 20.4 查看 Prometheus 指标

```bash
mc admin prometheus generate local
```

---

## 20.5 健康检查

```bash
mc ready local
```

或者：

```bash
mc ping local
```

具体命令支持情况与 `mc` 版本有关。

---

## 20.6 查看当前 mc 版本

```bash
mc --version
```

升级前后建议记录版本，便于排查命令差异。

---

# 21. mc 与 Spring Boot 项目配合方式

在项目中要区分三类工具：

```text
mc：运维 / 初始化 / 排查 / 批处理工具。
MinIO Java SDK：Spring Boot 业务代码上传下载文件使用。
MinIO Console：人工管理后台。
```

---

## 21.1 mc 适合做什么

1. 初始化 bucket。
2. 创建服务账号。
3. 创建权限策略。
4. 设置公开访问规则。
5. 批量导入测试文件。
6. 同步备份数据。
7. 排查文件是否真正上传成功。
8. 生成临时测试下载链接。

---

## 21.2 Spring Boot 适合做什么

1. 接收前端 / 小程序 / 设备端上传请求。
2. 鉴权用户身份。
3. 生成对象命名规则。
4. 调用 MinIO Java SDK 上传文件。
5. 将对象路径写入 MySQL。
6. 返回可访问 URL 或预签名 URL。
7. 控制业务权限，而不是让用户直接拿 MinIO 密钥。

---

## 21.3 推荐初始化流程

```bash
# 1. 配置连接
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin

# 2. 创建 bucket
mc mb local/avatar
mc mb local/upload
mc mb local/processed
mc mb local/audio
mc mb local/video

# 3. 设置头像 bucket 公开读
mc anonymous set download local/avatar

# 4. 创建后端服务账号
mc admin user add local app-backend StrongPassword123456

# 5. 创建策略
mc admin policy create local app-backend-policy app-backend-policy.json

# 6. 绑定策略
mc admin policy attach local app-backend-policy --user app-backend
```

---

## 21.4 application.yml 示例

```yaml
minio:
  endpoint: http://127.0.0.1:9000
  access-key: app-backend
  secret-key: StrongPassword123456
  bucket:
    avatar: avatar
    upload: upload
    processed: processed
    audio: audio
    video: video
  public-base-url: http://127.0.0.1:9000
```

---

# 22. Windows 常见用法

## 22.1 PowerShell 中配置 alias

```powershell
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
```

---

## 22.2 上传桌面文件

```powershell
mc cp "C:\Users\zhou1\Desktop\test.jpg" local/upload/
```

---

## 22.3 下载到桌面

```powershell
mc cp local/upload/test.jpg "C:\Users\zhou1\Desktop\test.jpg"
```

---

## 22.4 路径包含空格时

必须加引号：

```powershell
mc cp "C:\Users\zhou1\Desktop\my image.jpg" local/upload/
```

---

## 22.5 PowerShell 执行当前目录下的 mc.exe

```powershell
.\mc.exe --version
```

---

# 23. Linux 常见用法

## 23.1 查看 mc 位置

```bash
which mc
```

---

## 23.2 上传文件

```bash
mc cp /home/user/test.jpg local/upload/
```

---

## 23.3 下载文件

```bash
mc cp local/upload/test.jpg /home/user/test.jpg
```

---

## 23.4 后台同步

```bash
nohup mc mirror --watch /data/upload local/upload > /var/log/mc-mirror.log 2>&1 &
```

查看日志：

```bash
tail -f /var/log/mc-mirror.log
```

---

## 23.5 systemd 服务示例

创建文件：

```bash
sudo vim /etc/systemd/system/minio-mirror.service
```

内容：

```ini
[Unit]
Description=Mirror local upload directory to MinIO
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/mc mirror --watch /data/upload local/upload
Restart=always
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
```

启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable minio-mirror
sudo systemctl start minio-mirror
```

查看状态：

```bash
systemctl status minio-mirror
journalctl -u minio-mirror -f
```

---

# 24. Docker 环境中使用 mc

## 24.1 使用官方 mc 镜像

```bash
docker run --rm -it --entrypoint=/bin/sh minio/mc
```

进入容器后：

```bash
mc --version
```

---

## 24.2 一次性执行命令

```bash
docker run --rm minio/mc ls play
```

`play` 是 MinIO 官方公开测试服务的常见示例 alias，实际项目中通常使用自己的 alias。

---

## 24.3 Docker Compose 示例

```yaml
services:
  minio:
    image: minio/minio:latest
    container_name: minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123
    volumes:
      - ./minio-data:/data

  mc:
    image: minio/mc:latest
    container_name: minio-mc
    depends_on:
      - minio
    entrypoint: >
      /bin/sh -c "
      sleep 5;
      mc alias set local http://minio:9000 minioadmin minioadmin123;
      mc mb --ignore-existing local/upload;
      mc mb --ignore-existing local/avatar;
      mc anonymous set download local/avatar;
      exit 0;
      "
```

启动：

```bash
docker compose up -d
```

---

# 25. 常见错误与解决办法

## 25.1 `mc: command not found`

原因：`mc` 没安装，或者没加入环境变量。

Linux 解决：

```bash
curl -O https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/mc
mc --version
```

Windows 解决：

1. 下载 `mc.exe`。
2. 放到固定目录。
3. 把目录加入 `Path`。
4. 重新打开 PowerShell。
5. 执行 `mc --version`。

---

## 25.2 `Access Denied`

常见原因：

1. AccessKey / SecretKey 错误。
2. 当前用户没有对应 bucket 权限。
3. bucket 是私有的，匿名访问失败。
4. 策略没有绑定到用户。
5. 策略 Resource 写错。

排查：

```bash
mc alias ls
mc admin user info local appuser
mc admin policy info local policy-name
mc admin policy entities local
```

---

## 25.3 浏览器打不开图片

可能原因：

1. bucket 私有。
2. 没设置匿名下载。
3. URL 写错。
4. 使用了 Console 端口 9001，而不是 API 端口 9000。
5. 反向代理没配置对象路径。

解决：

```bash
mc anonymous set download local/avatar
```

然后访问：

```text
http://服务器IP:9000/avatar/1.png
```

---

## 25.4 `The specified bucket does not exist`

bucket 不存在。

解决：

```bash
mc mb local/upload
```

或者在程序启动时自动检测 bucket 是否存在，不存在则创建。

---

## 25.5 `SignatureDoesNotMatch`

常见原因：

1. 密钥错误。
2. endpoint 写错。
3. 服务端时间和客户端时间差异过大。
4. 代理层改了 Host 或协议。
5. 使用 HTTPS / HTTP 不一致。

排查：

```bash
mc alias set local http://正确地址:9000 access secret
mc ls local
```

服务器校准时间：

```bash
timedatectl
sudo timedatectl set-ntp true
```

---

## 25.6 `connection refused`

原因：MinIO 服务没启动，端口没开放，地址写错。

排查：

```bash
curl http://127.0.0.1:9000/minio/health/live
ss -lntp | grep 9000
systemctl status minio
```

Docker：

```bash
docker ps
docker logs minio
```

---

## 25.7 `certificate signed by unknown authority`

使用自签名 HTTPS 证书时可能出现。

临时测试可使用：

```bash
mc --insecure ls local
```

生产环境建议配置可信证书，不建议长期依赖 `--insecure`。

---

## 25.8 命令不存在或参数不支持

原因：`mc` 版本较旧，或者命令名称发生变化。

解决：

```bash
mc --version
mc <command> --help
```

例如：

```bash
mc admin policy --help
mc anonymous --help
mc ilm --help
```

如果旧教程里出现：

```bash
mc policy set download local/avatar
```

新版通常建议使用：

```bash
mc anonymous set download local/avatar
```

---

# 26. 常用命令速查表

## 26.1 alias

| 功能 | 命令 |
|---|---|
| 添加连接 | `mc alias set local http://127.0.0.1:9000 minioadmin minioadmin` |
| 查看连接 | `mc alias ls` |
| 删除连接 | `mc alias rm local` |
| 测试连接 | `mc ls local` |

---

## 26.2 bucket

| 功能 | 命令 |
|---|---|
| 查看 bucket | `mc ls local` |
| 创建 bucket | `mc mb local/upload` |
| 删除空 bucket | `mc rb local/upload` |
| 强制删除 bucket | `mc rb --force local/upload` |
| 查看占用 | `mc du local/upload` |
| 查看信息 | `mc stat local/upload` |

---

## 26.3 object

| 功能 | 命令 |
|---|---|
| 查看对象 | `mc ls local/upload` |
| 递归查看 | `mc ls --recursive local/upload` |
| 上传文件 | `mc cp a.jpg local/upload/` |
| 下载文件 | `mc cp local/upload/a.jpg ./a.jpg` |
| 复制对象 | `mc cp local/upload/a.jpg local/backup/a.jpg` |
| 移动对象 | `mc mv local/upload/a.jpg local/upload/b.jpg` |
| 删除对象 | `mc rm local/upload/a.jpg` |
| 查看对象信息 | `mc stat local/upload/a.jpg` |
| 查看文本内容 | `mc cat local/upload/a.txt` |

---

## 26.4 mirror

| 功能 | 命令 |
|---|---|
| 本地同步到 MinIO | `mc mirror ./data local/backup/data` |
| MinIO 同步到本地 | `mc mirror local/backup/data ./data` |
| 两个 bucket 同步 | `mc mirror local/upload prod/upload` |
| 覆盖更新 | `mc mirror --overwrite ./data local/backup/data` |
| 删除目标多余文件 | `mc mirror --remove ./data local/backup/data` |
| 监听同步 | `mc mirror --watch ./data local/backup/data` |

---

## 26.5 anonymous

| 功能 | 命令 |
|---|---|
| 查看匿名策略 | `mc anonymous get local/avatar` |
| 设置公开下载 | `mc anonymous set download local/avatar` |
| 取消公开 | `mc anonymous set none local/avatar` |
| 导出 JSON | `mc anonymous get-json local/avatar` |
| 导入 JSON | `mc anonymous set-json policy.json local/avatar` |

---

## 26.6 share

| 功能 | 命令 |
|---|---|
| 生成下载链接 | `mc share download local/upload/a.jpg` |
| 生成 1 小时下载链接 | `mc share download --expire 1h local/upload/a.jpg` |
| 生成上传链接 | `mc share upload local/upload/` |
| 生成 30 分钟上传链接 | `mc share upload --expire 30m local/upload/` |

---

## 26.7 admin user

| 功能 | 命令 |
|---|---|
| 查看用户 | `mc admin user ls local` |
| 添加用户 | `mc admin user add local appuser appsecret` |
| 禁用用户 | `mc admin user disable local appuser` |
| 启用用户 | `mc admin user enable local appuser` |
| 删除用户 | `mc admin user rm local appuser` |

---

## 26.8 admin policy

| 功能 | 命令 |
|---|---|
| 查看策略 | `mc admin policy ls local` |
| 查看策略内容 | `mc admin policy info local readwrite` |
| 创建策略 | `mc admin policy create local my-policy policy.json` |
| 绑定到用户 | `mc admin policy attach local my-policy --user appuser` |
| 绑定到组 | `mc admin policy attach local my-policy --group appgroup` |
| 解绑用户 | `mc admin policy detach local my-policy --user appuser` |
| 删除策略 | `mc admin policy rm local my-policy` |

---

## 26.9 version

| 功能 | 命令 |
|---|---|
| 查看版本状态 | `mc version info local/upload` |
| 开启版本控制 | `mc version enable local/upload` |
| 暂停版本控制 | `mc version suspend local/upload` |
| 查看对象版本 | `mc ls --versions local/upload/a.jpg` |

---

## 26.10 lifecycle

| 功能 | 命令 |
|---|---|
| 查看生命周期 | `mc ilm ls local/upload` |
| 30 天后删除 | `mc ilm rule add local/upload --expire-days 30` |
| tmp 前缀 7 天后删除 | `mc ilm rule add local/upload --prefix tmp/ --expire-days 7` |
| 删除规则 | `mc ilm rule rm local/upload <rule-id>` |

---

# 27. 推荐项目实践规范

## 27.1 bucket 命名建议

不要把所有文件都放进一个 bucket。

推荐：

```text
avatar       用户头像
upload       原始上传文件
processed    处理后文件
audio        音频文件
video        视频文件
public       公开资源
backup       备份文件
logs         日志归档
```

---

## 27.2 对象命名建议

对象名应该可追溯、避免冲突。

例如你的识别类项目可以使用：

```text
用户ID-年-月-日-时-分-秒+随机安全标识.扩展名
```

示例：

```text
1-2026-05-07-15-30-12+a8f93c2d.jpg
```

更推荐按日期分层：

```text
user-1/2026/05/07/1-2026-05-07-15-30-12+a8f93c2d.jpg
```

优点：

1. 避免单层对象太多。
2. 方便按用户查询。
3. 方便按日期归档。
4. 方便生命周期规则按前缀处理。

---

## 27.3 权限建议

不要让前端、小程序、设备端长期持有 MinIO 管理员密钥。

推荐：

```text
前端 / 小程序 / 设备端
        ↓
Spring Boot 业务后端鉴权
        ↓
后端使用 MinIO SDK 操作 MinIO
        ↓
MinIO 保存文件
```

如果要直传：

```text
前端 / 设备端 请求后端
        ↓
后端校验身份
        ↓
后端生成预签名上传 URL
        ↓
前端 / 设备端 直接上传 MinIO
```

---

## 27.4 公开访问建议

适合公开的：

```text
头像
公开展示图
公开静态资源
```

不适合公开的：

```text
用户隐私文件
识别原图
音频证据
视频证据
后台日志
数据库备份
```

私有文件建议使用：

```text
后端中转下载
或
后端生成短期预签名 URL
```

---

## 27.5 生产环境安全建议

1. 修改默认账号密码，不要使用 `minioadmin/minioadmin`。
2. 使用 HTTPS。
3. 给不同系统创建不同服务账号。
4. 使用最小权限策略。
5. 开启日志与监控。
6. 重要 bucket 开启版本控制。
7. 设置生命周期清理临时文件。
8. 定期备份重要 bucket。
9. 不要把 AccessKey / SecretKey 写死到前端代码。
10. 不要把 MinIO 管理员账号交给普通业务系统使用。

---

## 27.6 开发环境初始化脚本示例

文件名：`init-minio.sh`

```bash
#!/usr/bin/env bash
set -e

ALIAS="local"
ENDPOINT="http://127.0.0.1:9000"
ROOT_USER="minioadmin"
ROOT_PASS="minioadmin"

mc alias set ${ALIAS} ${ENDPOINT} ${ROOT_USER} ${ROOT_PASS}

mc mb --ignore-existing ${ALIAS}/avatar
mc mb --ignore-existing ${ALIAS}/upload
mc mb --ignore-existing ${ALIAS}/processed
mc mb --ignore-existing ${ALIAS}/audio
mc mb --ignore-existing ${ALIAS}/video

mc anonymous set download ${ALIAS}/avatar

mc ls ${ALIAS}

echo "MinIO 初始化完成"
```

执行：

```bash
chmod +x init-minio.sh
./init-minio.sh
```

---

## 27.7 Windows 初始化脚本示例

文件名：`init-minio.ps1`

```powershell
$Alias = "local"
$Endpoint = "http://127.0.0.1:9000"
$RootUser = "minioadmin"
$RootPass = "minioadmin"

mc alias set $Alias $Endpoint $RootUser $RootPass

mc mb --ignore-existing "$Alias/avatar"
mc mb --ignore-existing "$Alias/upload"
mc mb --ignore-existing "$Alias/processed"
mc mb --ignore-existing "$Alias/audio"
mc mb --ignore-existing "$Alias/video"

mc anonymous set download "$Alias/avatar"

mc ls $Alias

Write-Host "MinIO 初始化完成"
```

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\init-minio.ps1
```

---

# 结语

`mc` 是 MinIO 项目中非常重要的工具。

在开发阶段，它可以帮你快速确认：

```text
文件有没有上传成功
bucket 是否存在
权限是否正确
URL 是否能公开访问
对象路径是否写对
```

在部署和运维阶段，它可以帮你完成：

```text
初始化 bucket
配置权限
批量同步
备份迁移
排查服务状态
生成临时链接
```

推荐把 `mc` 当成 MinIO 的标准运维工具掌握，而业务代码中则使用 MinIO Java SDK / Python SDK / JS SDK 调用对象存储。
