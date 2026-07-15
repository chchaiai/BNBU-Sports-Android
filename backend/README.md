# BNBU Sports Backend

这是 BNBU Sports Android 学生端配套的 Node.js、Express、TypeScript、MySQL 8 后端。成功响应保持 Android DTO 所需的原始对象/数组结构；所有应用层错误统一返回：

```json
{
  "code": "ERROR_CODE",
  "message": "可显示的错误信息",
  "requestId": "排查请求所需的 ID"
}
```

## 已提供能力

- 学号/邮箱登录、JWT 学生鉴权、账号状态及 token 版本校验
- 体育时长汇总、打卡记录、课程、任务、成绩、通知、身份信息
- 打卡提交与补材料、每日提交及任务重复提交数据库约束
- 800m/1000m 体测免测、校队/社团免打卡及各自的补材料接口
- 图片/视频真实文件签名校验、本地开发存储、腾讯云 COS 生产存储
- 私有 COS 文件按每次查询重新生成签名 URL
- 请求 ID、Helmet、CORS 白名单、账号维度登录限流、校园 NAT 友好的全局限流
- 请求体稳定哈希幂等、上传并发阀、统一 JSON 的 403/413/429 错误
- 带校验和及数据库咨询锁的迁移、受控演示种子、Docker、PM2、Nginx、OpenAPI

## 本地开发

要求 Node.js 20+ 和 MySQL 8。

```powershell
Copy-Item .env.example .env
# 修改 .env 中的数据库密码、JWT_SECRET 和演示账号密码
npm ci
npm run db:migrate
npm run db:seed
npm run dev
```

健康检查：`GET http://localhost:3005/api/health`。健康状态不仅检查数据库连接，还检查迁移记录、核心表和对象存储。

`db:seed` 只应在显式配置 `SEED_STUDENT_*` 后运行，且绝不能在正式数据库执行演示种子。

## 关键环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PUBLIC_BASE_URL` | `http://localhost:3005` | 生产环境必须为 HTTPS |
| `DATABASE_URL` | 无 | 最小权限 MySQL 连接串；用户名/密码中的保留字符必须百分号编码 |
| `JWT_SECRET` | 无 | 至少 32 字符；生产禁止示例值 |
| `CORS_ORIGINS` | 空 | 逗号分隔；生产至少包含一个 HTTPS 来源 |
| `GLOBAL_RATE_LIMIT_PER_15_MIN` | `60000` | 每 IP 全局额度；为校园共享 NAT 预留空间 |
| `LOGIN_RATE_LIMIT_PER_15_MIN` | `20` | 每个规范化账号的登录额度，不按校园出口 IP 计数 |
| `STORAGE_DRIVER` | `local` | 生产环境强制为 `cos` |
| `UPLOAD_MAX_REQUEST_BYTES` | `120000000` | 单次 multipart 总请求上限 |
| `UPLOAD_MAX_CONCURRENT` | `2` | 单进程同时进入内存上传流程的请求数 |
| `UNCLAIMED_UPLOAD_TTL_HOURS` | `24` | 未被业务记录认领的上传保留时长 |
| `UNCLAIMED_UPLOAD_CLEANUP_BATCH` | `100` | 单次清理最多处理的对象数，范围 2–500，保证新对象与重试队列均有处理名额 |
| `COS_SIGNED_URL_TTL_SECONDS` | `900` | 私有对象临时 URL 有效期 |

完整示例见 `.env.example`。正式环境还必须配置 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET`、`COS_REGION`。

## 数据库迁移安全

```text
npm run db:migrate
```

迁移器会：

- 在整个迁移期间持有 MySQL `GET_LOCK` 咨询锁，阻止两个实例并发迁移；
- 验证 `schema_migrations` 必需列和已执行文件的 SHA-256；
- 在未执行迁移发现同名业务表时立即中止，不会因 `IF NOT EXISTS` 静默接管未知结构；
- 明确提示 MySQL DDL 不能事务回滚，失败后必须先检查并修复部分创建的表。

本目录提供的是可独立重建的规范 schema。接管已有 `bnbu_kuan_week2` 或其他旧库前，必须先导出真实结构并编写显式基线/映射迁移，不能直接覆盖。迁移前务必备份。

## 幂等规则

打卡新建、打卡补材料、免测/免打卡新建及补材料支持 `Idempotency-Key`，格式为 8–128 位安全字符。

- 网络重试应复用同一个 key 和完全相同的逻辑请求内容；
- 服务端对校验并规范化后的请求体生成稳定 SHA-256；
- 同一学生、接口作用域和 key 的相同请求返回第一次结果；
- 同一个 key 被用于不同内容时返回 `409 IDEMPOTENCY_KEY_REUSED`。

## 免测约束

- 体测免测只接受 `800m`/`1000m`，`organization` 必须省略或为 `null`；
- 免打卡只接受 `team`/`club`，`organization` 必填；
- 新建及补材料都必须引用至少一份属于当前学生且尚未被使用的上传凭证；
- 驳回或要求补材料的打卡不会把对应课程任务标记为已完成。

## 上传规则

- multipart 字段名固定为 `files`；
- 最多 6 张图片，每张不超过 8MB；最多 1 个视频，不超过 100MB；
- 单次最多 7 个文件，总请求不超过 `UPLOAD_MAX_REQUEST_BYTES`；
- 服务端检查二进制文件签名，不信任扩展名或客户端 MIME；
- 上传前先在数据库预留随机对象键；上传凭证只能由所属学生成功认领一次；
- 上传或补偿删除中断时仍保留可清理登记，不会形成无法追踪的永久对象；
- 当前使用内存 multipart，因此默认每个进程仅允许 2 个并发上传。若需更大并发，应改为磁盘临时文件或直传/流式 COS，不应仅调大阀值。

Nginx 和应用都会限制请求大小。它们产生的 413/429 响应均为带 `requestId` 的 JSON。

上传完成但客户端未继续提交业务表单时，对象会暂时处于未认领状态。生产环境应每小时运行：

```text
npm run cleanup:proofs
```

该命令运行已构建的 `dist` 并会读取当前目录的 `.env`；开发环境未先构建时可用 `npm run cleanup:proofs:dev`。任务使用数据库咨询锁避免重入，先把过期 `upload` 行提交为不可认领的 `deleting` 墓碑，再删除对象，最后删除登记；任一步中断都能在后续运行安全重试。失败对象有冷却与轮转，不会永久饿死后续对象；任务只要出现失败就返回非零状态供监控告警。它不会删除已经关联到打卡或免测申请的凭证。Docker 环境可用 `docker compose exec -T api npm run cleanup:proofs`。

记录和免测列表支持 `limit`（1–500，默认 500）与 `offset`（0–1000000），排序同时使用时间和 ID 保持分页顺序确定，历史数据不会被固定上限永久截断。

## Docker Compose（生产式配置）

Compose 会显式强制 API 使用 `NODE_ENV=production`，因此 `.env.example` 不能未经修改直接用于 Compose。请先在 `.env` 配置正式 HTTPS 域名、HTTPS CORS 来源、强 JWT 密钥、COS 及 MySQL 密码：

```text
MYSQL_PASSWORD=...
MYSQL_PASSWORD_URLENCODED=...
MYSQL_ROOT_PASSWORD=...
PUBLIC_BASE_URL=https://api.example.edu.cn
CORS_ORIGINS=https://app.example.edu.cn
STORAGE_DRIVER=cos
COS_SECRET_ID=...
COS_SECRET_KEY=...
COS_BUCKET=...
COS_REGION=ap-guangzhou
```

`MYSQL_PASSWORD` 是 MySQL 容器初始化所需的原始密码；`MYSQL_PASSWORD_URLENCODED` 必须是同一密码在 URL 中的百分号编码形式。例如原始密码中的 `#` 应写成 `%23`。两者必须一致，根密码应另设。

然后执行：

```text
docker compose build
docker compose run --rm api node dist/src/db/migrate.js
docker compose up -d
```

API 只映射到宿主机 `127.0.0.1:3005`，由 Nginx 提供公网 TLS。镜像已预建并授权 `/app/uploads`，避免非 root 的 `node` 用户挂载卷后无写权限。

## 生产部署检查

1. 替换 `nginx/bnbu-sports.conf` 的域名和证书路径，并先执行 `nginx -t`。
2. 使用独立、最小权限 MySQL 用户；备份后执行迁移。
3. COS 桶保持私有，密钥只放在服务端环境；不要记录 Authorization、Cookie 或 COS 密钥。
4. 执行 `npm ci && npm run check && npm audit --omit=dev`。
5. 使用 Docker Compose 或 `pm2 start ecosystem.config.cjs && pm2 save` 启动。
6. 关闭公网 3005/3334，只开放 80/443；Android Release 地址使用 `https://正式域名/api`。
7. 用真实临时 MySQL 和测试 COS 桶完成登录、上传、提交、查询、幂等重试及补偿删除验收。

Nginx 的 API IP 阈值为 100 请求/秒、突发 300，应用默认每 IP 15 分钟 60000 次，以降低校园共享 NAT 误封；登录仍由每账号 15 分钟 20 次的独立限制保护。

## 校验与接口文档

```text
npm run check
npm audit --omit=dev
```

OpenAPI 契约位于 `openapi/openapi.yaml`。单元/契约测试不连接生产数据库；上线前仍需在临时 MySQL、Docker 和 COS 环境执行集成验证。

新账号密码使用 bcrypt。迁移账号还支持自描述格式 `pbkdf2_sha512$迭代次数$salt$hex或base64摘要`；旧库若把盐和算法拆列存储，应在迁移时合并为该格式或执行一次性重哈希。
