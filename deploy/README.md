# Docker Compose 部署说明

本方案用于本地开发、项目演示和功能体验。Compose 启动前端 Nginx、Spring Boot 后端、MySQL、Redis 和 RabbitMQ，首次初始化数据库时自动导入表结构和演示数据。

当前采用**先在本机打包，再由 Dockerfile 构建运行镜像**的方式，不是容器内编译源码。首次启动前必须完成下面的打包步骤。

## 1. 环境要求

- Docker Engine 或已启动的 Docker Desktop，使用 Linux 容器。
- Docker Compose V2，能够执行 `docker compose version`。
- 打包机器安装 JDK 17，以及 Node.js 22.18+（22 系列）或 24.12+，使用随 Node.js 安装的 npm。
- 能够访问 Maven、npm 依赖仓库和所需的容器镜像仓库。
- 宿主机的 8082 端口未被占用。

不需要额外安装 MySQL、Redis、RabbitMQ 或 Nginx。已有的开发环境可以保留，本方案的中间件不映射宿主机端口。

## 2. 文件与构建路径

```text
campus-pass/
├── pom.xml
├── mvnw / mvnw.cmd
├── target/campus-pass-0.0.1-SNAPSHOT.jar   # 打包生成
├── frontend/
│   ├── package.json
│   └── dist/                            # 打包生成
├── sql/
│   ├── CampusPass.sql                   # 表结构
│   └── CampusPass-test-data.sql         # 演示用户和活动
└── deploy/
    ├── compose.yaml
    ├── Dockerfile.backend
    ├── Dockerfile.frontend
    ├── nginx.conf
    ├── .env                             # 随仓库提供，使用前替换占位值
    └── README.md
```

两个镜像的构建上下文均为项目根目录。SQL 文件通过只读挂载提供给 MySQL，并映射为 `01-schema.sql`、`02-demo-data.sql`，确保先建表再插入数据。

## 3. 打包前后端

先进入克隆后的 `campus-pass` 项目根目录。

Windows PowerShell：

```powershell
.\mvnw.cmd clean package
cd frontend
npm ci
npm run build
cd ..
```

Linux / macOS：

```bash
sh ./mvnw clean package
cd frontend
npm ci
npm run build
cd ..
```

确认所有命令成功，生成 `target/campus-pass-0.0.1-SNAPSHOT.jar` 和 `frontend/dist/index.html` 后继续。项目版本号改变时，需要同步修改后端 Dockerfile 中的 JAR 文件名。

如果在 Windows 打包、在 Linux 虚拟机运行，需要将 `deploy`、`sql`、生成的 JAR 和 `frontend/dist` 一起传到虚拟机，并保持上述相对目录结构。运行机器无需安装 JDK、Node.js 或 Maven。

## 4. 填写环境配置

仓库直接提供 `deploy/.env`，不需要复制模板。先进入 `deploy` 目录：

```bash
cd deploy
```

编辑 `.env`，将中文占位提示替换为实际配置值。预设的 `campuspass` 用户名和虚拟主机名可以保留，也可以自行替换：

| 变量 | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL 管理员密码 |
| `MYSQL_PASSWORD` | 应用数据库账号 `campuspass_app` 的密码 |
| `RABBITMQ_USERNAME` | RabbitMQ 账号，示例为 `campuspass` |
| `RABBITMQ_PASSWORD` | RabbitMQ 账号密码 |
| `RABBITMQ_VIRTUAL_HOST` | RabbitMQ 虚拟主机，示例为 `campuspass` |
| `CAMPUS_PASS_JWT_SECRET` | 随机生成的 Base64 编码 JWT 密钥 |

这些是中间件凭据，与网页演示用户的登录密码无关。建议密码使用随机字母和数字；使用 `$`、`#` 等字符时注意 dotenv 的插值与注释规则。

JWT 密钥可以在安装了 Node.js 的打包机器上生成，再复制到 `.env`：

```bash
node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"
```

`.env` 是完整文件名，不要保存成 `.env.txt`。本仓库对这个配置文件进行版本管理，但仅保留公开的预设值和占位提示；在本地填入真实密码或 JWT 密钥后，不要将这些值随代码一起提交。提交前检查 `.env` 的差异。中文占位提示不是可直接使用的配置，尤其 JWT 密钥必须按上面的命令生成并替换。

## 5. 启动与验证

以下 Compose 命令都在 `deploy` 目录执行：

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

首次需要拉取镜像和初始化数据库，可能耗时较长。查看启动日志：

```bash
docker compose logs --tail=100 mysql rabbitmq backend frontend
```

MySQL、Redis、RabbitMQ 配置了健康检查，应逐步显示 `healthy`。前后端未配置健康检查，显示 `Up` 属于正常现象，仍需实际打开网页验证；前端启动也不意味着后端此刻已就绪。

- 在运行 Docker 的机器上访问：`http://localhost:8082`。
- 在 Windows 浏览器访问 Linux 虚拟机：`http://虚拟机IP:8082`，例如 `http://192.168.132.128:8082`。

只有前端端口 `8082:80` 对宿主机开放。浏览器请求 `/api/` 时，由前端 Nginx 转发至 Compose 网络中的 `backend:8080`，并移除 `/api/` 前缀。后端使用服务名 `mysql`、`redis`、`rabbitmq` 连接中间件，无需填写虚拟机 IP。

## 6. 演示账号与体验流程

首次初始化会创建 6 个账号，统一密码为 **`password`**，使用密码登录。

| 角色 | 手机号 |
| --- | --- |
| 管理员 | `13800000000` |
| 活动发布者 | `13800001001` |
| 普通用户 | `13800001002`～`13800001005` |

预置 9 个活动，日期相对于导入时间计算，包括报名窗口为 30、60、90、180、365 天的活动，以及未开放活动、草稿和单名额候补体验活动。数据不会随重启自动刷新时间。

建议等待后端启动后的缓存初始化任务完成（约 30～60 秒），再按下面的顺序体验：

1. 普通用户登录，搜索活动并报名，查看报名记录与最终处理结果。
2. 用两个普通用户分别报名“单名额报名与候补体验”，验证正式报名与候补。
3. 取消正式报名，等待异步处理后检查候补用户是否递补成功。
4. 用活动发布者查看“我的活动”、参与名单和草稿。
5. 用管理员进入平台管理，查看用户与活动。

这些是公开的演示账号，手机号是占位数据，不应用于真实短信发送。正式对公网部署前应替换演示账号与密码，并另行配置 HTTPS 等生产环境设置。

## 7. 数据持久化与初始化规则

MySQL、Redis 和 RabbitMQ 分别使用命名数据卷保存数据。普通容器重建不会删除这些卷。

MySQL 初始化目录中的 SQL **仅在数据库数据目录为空时自动执行**。因此：

- 首次部署：自动先建表，再导入演示数据。
- 重启或重新构建：保留现有账号、报名记录和其他数据，不会重新导入。
- 修改 SQL 后：已有数据库不会自动更新，需要按实际情况执行迁移或手动导入。
- 修改 `.env` 中的 MySQL/RabbitMQ 初始化账号密码：不等于修改已有数据卷中的账号密码，需要同步修改服务中的实际凭据。

如果已有数据库已建表，但从未导入这些演示数据，可以手动执行一次：

```bash
docker compose stop backend
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE" < /docker-entrypoint-initdb.d/02-demo-data.sql'
```

确认没有报错后再启动：

```bash
docker compose start backend
```

演示数据脚本不支持重复导入，会因账号唯一约束报错；不要使用 `--force` 忽略 SQL 错误。不要为了重新执行初始化而直接删除保存业务数据的数据卷。

## 8. 更新与日常操作

查看状态和日志：

```bash
docker compose ps
docker compose logs -f --tail=100 backend
```

日志跟随模式按 `Ctrl+C` 退出，不会停止后台容器。

停止后恢复运行：

```bash
docker compose stop
docker compose start
```

删除本方案的容器与网络，但保留数据卷：

```bash
docker compose down
```

之后使用 `docker compose up -d` 重新创建。不要随意添加 `-v`，它会同时删除本方案声明的数据卷。

代码更新后，先回项目根目录重新执行对应的 Maven 或 npm 打包命令，再进入 `deploy` 执行：

```bash
docker compose up -d --build
```

单独执行 `docker compose restart` 不会重新构建镜像，也不会载入新的 JAR 或前端构建文件。

## 9. 常见问题

| 现象 | 检查方法 |
| --- | --- |
| 构建提示找不到 JAR 或 dist | 先完成第 3 步打包，确认目录和文件名没有变化 |
| 提示环境变量未设置 | 检查 `deploy/.env` 是否存在、文件名是否正确、必填值是否为空 |
| 浏览器无法连接 | 检查 `docker compose ps`、8082 端口、防火墙及虚拟机 IP |
| 打开了旧的 Nginx 欢迎页 | 确认访问的是 `8082` 端口，而不是宿主机原有的 80 端口 |
| 页面正常但接口 502 | 查看 backend 日志，确认启动成功和数据库连接正常 |
| 账号不存在或数据库缺表 | 查看 MySQL 初始化日志；确认是否复用了已经初始化过的数据卷 |
| SQL 文件不存在 | 检查仓库 `sql` 中两个文件，以及容器内 `/docker-entrypoint-initdb.d/` 的挂载 |
| 登录成功但刚启动时报名失败 | 等待缓存初始化，再检查后端与 RabbitMQ 日志和请求记录 |

本方案固定使用 Compose 项目名 `campuspass-full`。如果机器上已有同名部署，会操作同一组服务和卷；若需另建独立实例，应更换项目名并调整前端映射端口。
