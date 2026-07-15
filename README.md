# BNBU Sports 学生端与后端

本仓库包含可运行的 Android 学生端和配套 Node.js 后端。Android 已接入真实 API，不再是纯 Mock 工程。

## 当前交付状态

- Android 工程就在仓库根目录，应用模块为 `app/`，Android Studio 应直接打开本仓库根目录。
- Debug 构建默认使用现有联调地址 `http://123.207.5.70:3334/api`。
- `backend/` 是本仓库新增的完整后端实现，默认在 `127.0.0.1:3005` 提供 `/api`；本次仓库交付时，它**尚未部署到上述联调服务器**。联调服务器可访问不代表其运行的是 `backend/` 中的代码。
- Release 构建不接受默认地址或明文 HTTP，必须显式传入正式 HTTPS API 地址。
- 后端的接口契约位于 [`backend/openapi/openapi.yaml`](backend/openapi/openapi.yaml)，更详细的后端说明位于 [`backend/README.md`](backend/README.md)。

## 目录

```text
app/                         Android 应用源码与测试
backend/                     Express + TypeScript + MySQL 后端
backend/db/migrations/       数据库迁移
backend/nginx/               HTTPS 反向代理示例
backend/openapi/             OpenAPI 接口契约
scripts/ci-check.sh          Android 与后端的一键 CI 校验
gradlew / gradlew.bat        Gradle Wrapper
```

仓库内其他独立目录不属于本应用的构建与部署流程。

## 环境要求

- JDK 17
- Android SDK 35；最低支持 Android 8.0（API 26）
- Node.js 20 或更高版本
- MySQL 8
- 可选：Docker Compose、PM2、Nginx、正式域名与 TLS 证书

## Android 运行与后端地址

### Debug

直接构建时会连接现有联调服务：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

如需让 Android 模拟器连接本机后端，使用模拟器访问宿主机的地址 `10.0.2.2`：

```powershell
.\gradlew.bat :app:assembleDebug -PBNBU_API_BASE_URL=http://10.0.2.2:3005/api
```

也可设置同名环境变量。Debug 的明文网络例外仅包含当前联调 IP、`10.0.2.2`、`localhost` 和 `127.0.0.1`；其他地址应使用 HTTPS，不能假设任意 HTTP 地址可用。

### Release

Release 必须显式提供格式正确的 HTTPS 地址，并以 `/api` 结尾；地址不能携带账号、密码、查询参数或片段：

```powershell
.\gradlew.bat :app:assembleRelease -PBNBU_API_BASE_URL=https://api.example.edu.cn/api
```

也可以在受保护的 CI 环境中设置 `BNBU_API_BASE_URL=https://api.example.edu.cn/api`。未配置、使用 HTTP、缺少有效主机名时，构建会主动失败。仓库不保存发布签名密钥；正式分发前还需在受保护的发布环境中配置签名。

## 后端本地启动

以下步骤只应针对新建的空开发数据库。不要把规范迁移直接指向既有生产库。

```powershell
Set-Location backend
Copy-Item .env.example .env
npm ci
npm run db:migrate
# 仅在已显式设置 SEED_STUDENT_* 测试账号时执行：
npm run db:seed
npm run dev
```

运行迁移前，先在 `.env` 中至少完成以下配置：

- `DATABASE_URL`：空开发数据库和最小权限用户；
- `JWT_SECRET`：至少 32 个随机字符，不使用示例值；
- `PUBLIC_BASE_URL=http://localhost:3005`；
- 本地开发保持 `NODE_ENV=development`、`STORAGE_DRIVER=local`。

启动后检查：

```text
GET http://localhost:3005/api/health
```

## 生产部署

### 上线前必须提供的外部信息

仓库不会、也不应包含以下生产秘密和服务器权限；缺少任意一项都不能宣称完成正式上线：

1. 服务器 SSH/运维权限、操作系统与防火墙规则；
2. 正式 API 域名、DNS 解析和有效 TLS 证书；
3. 生产 MySQL 地址、端口、数据库名、最小权限账号和密码；
4. 既有数据库的真实结构导出及数据映射规则；
5. 随机生产 `JWT_SECRET`；
6. 腾讯 COS 的 `COS_SECRET_ID`、`COS_SECRET_KEY`、Bucket 和 Region；
7. 允许的 HTTPS `CORS_ORIGINS`；
8. Android 发布签名及最终的 `https://<域名>/api` 地址。

### 既有数据库限制

`backend/db/migrations/` 描述的是一套可独立重建的规范 schema，不假定线上现有 16 张表的列名、约束和密码格式。若目标库已经存在（例如历史 `bnbu_kuan_week2`）：

1. 先做完整备份并导出表结构、索引、约束和必要样本；
2. 对照规范 schema 编写一次性映射迁移，处理用户 ID、课程、任务、记录、附件和旧密码格式；
3. 在隔离的临时数据库演练迁移、回滚和接口测试；
4. 验证通过后安排维护窗口，再执行生产迁移。

不能直接在未知旧库上运行 `001_initial.sql`，也不能覆盖旧表。迁移工具会记录迁移文件 SHA-256，已执行的迁移文件被修改后会拒绝继续。

### 生产环境配置

以 `backend/.env.example` 为模板，在服务器安全地创建 `.env`，至少调整：

```dotenv
NODE_ENV=production
HOST=127.0.0.1
PORT=3005
PUBLIC_BASE_URL=https://api.example.edu.cn
DATABASE_URL=mysql://<最小权限用户>:<密码>@<数据库主机>:3306/<数据库名>
JWT_SECRET=<至少32字符的随机密钥>
CORS_ORIGINS=https://<允许的站点域名>
TRUST_PROXY=1
STORAGE_DRIVER=cos
COS_SECRET_ID=<由密钥管理系统注入>
COS_SECRET_KEY=<由密钥管理系统注入>
COS_BUCKET=<正式私有桶>
COS_REGION=<实际地域>
```

不要提交 `.env`、数据库备份、证书私钥、COS 密钥、JWT 密钥或 Android 签名文件。

### 上线顺序

1. 在干净环境执行 `cd backend && npm ci && npm run check`。
2. 备份数据库，并在已完成映射演练的前提下执行 `npm run db:migrate`。
3. 执行 `npm run build`。
4. 选择一种运行方式：
   - PM2：`pm2 start ecosystem.config.cjs && pm2 save`；
   - Docker：先设置原始 `MYSQL_PASSWORD`、同值的 URL 百分号编码 `MYSQL_PASSWORD_URLENCODED`、独立的 `MYSQL_ROOT_PASSWORD`，再执行 `docker compose build`、`docker compose run --rm api node dist/src/db/migrate.js`、`docker compose up -d`。
5. 将 `backend/nginx/bnbu-sports.conf` 中的示例域名和证书路径替换为真实值，检查配置后重载 Nginx。
6. 只对公网开放 80/443；API 的 3005 端口只监听本机或容器内网，禁止绕过 Nginx 直连。
7. 配置每小时运行 `npm run cleanup:proofs`（Docker 使用 `docker compose exec -T api npm run cleanup:proofs`），清理超过保留期且从未被业务记录认领的上传对象，并对失败建立告警。
8. 从公网验证 `https://<域名>/api/health`、登录、查询、提交、上传和补材料流程，同时检查数据库、COS、日志和限流响应。
9. 最后用同一域名构建并签名 Android Release 包，完成真机端到端回归。

生产环境必须使用私有 COS 桶。Nginx 请求体上限和后端上传上限均为 120 MB；图片、视频数量与单文件限制见后端 README。

## 校验与 CI

本地快速校验：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
Set-Location backend
npm ci
npm run check
```

Linux/macOS 或 CI 环境可从仓库根目录运行：

```bash
bash scripts/ci-check.sh
```

该脚本会真实执行 Android 单元测试、Android Lint、Debug APK 构建，以及后端的锁文件安装、类型检查、单元测试和构建；任一步失败都会返回非零退出码。
