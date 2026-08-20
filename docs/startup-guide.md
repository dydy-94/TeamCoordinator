# TeamCoordinator 本地启动指南

> 前端（Vite dev server，端口 3000）+ 后端（Spring Boot，端口 **18082**）+ 依赖服务（MySQL/Redis/MinIO，docker compose）。
> 前端 proxy 与 API 基地址统一指向 `http://localhost:18082`。

## 0. 前置条件

| 依赖 | 版本/说明 |
|---|---|
| JDK | 21（maven-enforcer 强制） |
| Maven | 3.6+ |
| Node | 18+（前端 Vite、tc CLI 均需要） |
| Docker | 运行 MySQL 8 / Redis 7 / MinIO |

## 1. 启动依赖服务

```bash
# 仓库根目录（首次执行会自动按序执行 db/init/*.sql 建库+灌种子，无需 Flyway）
MYSQL_ROOT_PASSWORD=<你的MySQL密码> \
MINIO_ROOT_USER=<你的MinIO用户名> \
MINIO_ROOT_PASSWORD=<你的MinIO密码> \
docker compose up -d
```

> 环境变量覆盖说明：`compose.yml` 默认密码是 `change-me`/`minioadmin`，
> 上面的值必须与本地后端配置 `src/main/resources/application-local.yml`
> （gitignored，本机私有）**保持完全一致**。若你本机还没有该文件，创建它
> 并填入你自己的凭据：

```yaml
spring:
  datasource:
    username: root
    password: <你的MySQL密码>
digital-team:
  storage:
    access-key: <你的MinIO用户名>
    secret-key: <你的MinIO密码>
```

> 重要：`db/init` 挂载只在 **MySQL 数据卷首次初始化**时执行。
> 已启动过的卷不会重跑初始化——需要重建时：
> `docker compose down -v && docker compose up -d`（会清空本地数据！）

验证依赖服务：

```bash
docker compose ps                    # 三个服务 healthy
curl http://localhost:9000/minio/health/live   # MinIO 存活
```

## 2. 启动后端（端口 18082）

```bash
# 仓库根目录
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18082
```

说明：

- 默认 profile 为 `local`（application-local.yml 提供本机数据库/MinIO 凭据）
- **必须指定 18082**：前端 proxy 与 API 约定都指向该端口（application.yml 默认 8080 是历史值）
- AgentCore 默认 **mock 模式**（`agent-core.mock-enabled=true`），本地无需真实 AgentCore 即可跑通全流程
- 不想用 application-local.yml 时，可用环境变量覆盖：
  `MYSQL_USERNAME=root MYSQL_PASSWORD=xxx MINIO_ACCESS_KEY=xxx MINIO_SECRET_KEY=xxx`

打包运行方式（可选）：

```bash
mvn clean package -DskipTests
java -jar target/teamcoordinator-*.jar --server.port=18082
```

## 3. 启动前端（端口 3000）

```bash
cd frontend
npm install        # 首次
npm run dev        # Vite dev server，/api 与 /mock 代理到 localhost:18082
```

浏览器打开 `http://localhost:3000`。

## 4. 验证清单

```bash
# 后端健康
curl http://localhost:18082/health
curl http://localhost:18082/ready

# 端到端冒烟（可选）：创建项目
curl -X POST http://localhost:18082/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-local" -H "X-User-Id: owner-local" \
  -d '{"name":"冒烟测试"}'
```

前端页面：新建项目 → 新建 task → 发消息 → 观察 SSE 事件流与最终回答。

## 5. 常见问题

| 问题 | 处理 |
|---|---|
| 端口 18082 被占 | `lsof -i :18082` 找到进程处理，或改用其他端口（需同步改 `frontend/vite.config.ts` 的 proxy） |
| 数据库连不上 | `docker compose ps` 确认 mysql healthy；核对 application-local.yml 密码与 compose 启动时的 `MYSQL_ROOT_PASSWORD` 一致 |
| 建表没执行（表不存在） | init 只在空卷首次启动执行：`docker compose down -v && docker compose up -d` 重建 |
| 前端 404 / 代理不通 | 确认后端已起在 18082；vite proxy 只转发 `/api` 与 `/mock` 前缀 |
| 不想用 mock AgentCore | 设置 `AGENTCORE_MOCK_ENABLED=false` 并配置 `AGENTCORE_BASE_URL` / `AGENTCORE_AUTH_VALUE`（真实 AgentCore 地址与凭证） |
| CLI 调试（tc） | 已装 `~/bin/tc`；`TC_BASE_URL=http://localhost:18082 tc health`（详见 `src/main/cli/README.md`） |

## 6. 环境变量速查

| 变量 | 默认 | 用途 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD`（compose） | change-me | 与 application-local.yml 的 datasource 密码对齐 |
| `MINIO_ROOT_USER/PASSWORD`（compose） | minioadmin | 与 application-local.yml 的 storage 凭据对齐 |
| `SERVER 端口` | 18082（启动参数指定） | 前端 proxy 目标 |
| `AGENTCORE_MOCK_ENABLED` | true | 本地无需真实 AgentCore |
| `EXECUTION_WORKER_INTERVAL_MS` | 500 | 执行引擎轮询间隔 |
| `FLYWAY_ENABLED` | false | 已脱离 Flyway，由 db/init 初始化 |
