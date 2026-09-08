# 飞书机器人接入、部署与验收测试方案

## 1. 目标与范围

本文用于把工单 Agent 接入飞书企业自建应用，并完成真实单聊验收。范围包括：

- 飞书开放平台应用、机器人、权限、长连接事件及卡片回调配置；
- Backend、CLI Service、workorder-cli、AiAssistant 的构建与配置；
- 飞书身份与工单账号的一次性安全绑定；
- 查询工单；
- 创建工单的 `list → schema → dry-run → 卡片确认 → execute` 完整链路；
- 身份隔离、幂等、安全、日志、异常和数据清理。

当前实现只接受飞书机器人单聊文本消息，不支持群聊、图片、文件和语音。事件与卡片回调均使用飞书 SDK WebSocket 长连接，服务器只需能主动访问飞书公网，不需要公网 IP、域名或 Webhook。

## 2. 系统链路

```text
飞书单聊用户
  → 飞书开放平台 WebSocket（im.message.receive_v1）
  → FeishuLongConnection / FeishuMessageParser
  → messageId 幂等、用户限流、同会话串行
  → 飞书 tenantKey + unionId 查询工单账号绑定
  → AiAssistant 使用内部服务凭证换取短期 channel_proxy Token
  → AgentLoop → workorder-cli → CLI Service → Backend
  → Backend 按真实工单用户执行鉴权和数据权限检查
  → 普通文本回复，或创建写操作确认卡片
  → card.action.trigger → 原请求人确认 → 一次性执行
```

飞书 App ID/App Secret 只证明机器人应用身份；它不能代表工单用户。每位飞书用户必须先绑定自己的工单账号，后端才会签发短期代理 Access Token。

## 3. 前置条件

- Java 17、Maven、Go 已安装。
- MySQL、MongoDB、Redis、RabbitMQ 可用；Elasticsearch按环境配置启用。
- Backend 数据库已执行：
  - `backend/workorder-server/src/main/resources/auth_stage_one_migration.sql`
  - `backend/workorder-server/src/main/resources/auth_stage_two_migration.sql`
- 准备独立的测试企业、企业管理员和两个普通测试用户 A/B。
- A/B 在工单系统中分别有可登录账号和可区分的测试数据权限。
- 准备一个有效流程 ID，用于创建工单；禁止使用生产流程和生产账号。
- AiAssistant 所在机器可以访问 `open.feishu.cn` 及飞书 WebSocket 服务。

## 4. 飞书开放平台配置

### 4.1 创建应用

1. 登录飞书开放平台，进入“开发者后台”。
2. 创建“企业自建应用”；长连接模式不适用于商店应用。
3. 在“凭证与基础信息 → 应用凭证”获取 App ID 和 App Secret。
4. 不要把 App Secret 写入 Git、截图、测试报告或聊天消息。

### 4.2 开启机器人

1. 在“添加应用能力”中添加“机器人”。
2. 设置机器人名称、头像和简介，例如“工单助手（测试）”。
3. 当前版本只测试用户与机器人的单聊，不把机器人加入业务群。

### 4.3 配置权限

在“权限管理”申请并由企业管理员批准下列最小权限。控制台展示名可能随版本略有变化，应以权限标识和用途核对：

| 用途 | 权限建议 |
|---|---|
| 接收用户发给机器人的单聊消息 | `im:message.p2p_msg:readonly`；如果控制台只提供历史权限，则选对应的 `im:message.p2p_msg` |
| 以应用身份发送和回复消息 | 发送消息相关的 `im:message` 应用权限 |
| 取得稳定用户身份用于绑定 | 事件返回 `open_id`、`union_id` 所需的用户 ID 权限；不要申请通讯录全量权限 |

不要申请群消息、群成员、全量通讯录等本期不需要的敏感权限。飞书官方说明，单聊消息事件需要开启机器人，并具备单聊消息读取权限；重复推送应使用 `message_id` 去重，而不是只依赖 `event_id`。

### 4.4 配置事件订阅

1. 进入“事件与回调 → 事件配置/事件订阅”。
2. 订阅方式选择“使用长连接接收事件”。
3. 添加“消息与群组 → 接收消息 v2.0”，事件类型为 `im.message.receive_v1`。
4. 不填写请求地址、Verification Token 或 Encrypt Key；长连接 SDK 已封装建连鉴权。
5. AiAssistant 必须先以正确 App ID/App Secret 启动并建立连接，控制台才能完成长连接配置检查。

官方约束：事件处理需在 3 秒内返回，否则飞书可能重推；当前实现接收线程只做解析、去重和异步投递，不同步等待 Agent。

### 4.5 配置卡片回调

1. 在同一应用的“事件与回调 → 回调配置”选择“使用长连接接收回调”。
2. 添加新版“卡片回传交互”，回调类型 `card.action.trigger`。
3. 不要同时配置旧版 `card.action.trigger_v1`，否则一次点击可能产生两类回调。
4. 当前代码使用 `onP2CardActionTrigger`，确认卡片按钮值包含：

```json
{"action":"confirm","operationId":"服务端生成的ID"}
```

取消按钮使用 `action=cancel`。回调处理必须在 3 秒内响应；真实执行结果由服务端持久化并通过 Toast/卡片状态反馈。

### 4.6 应用发布与可用范围

1. 创建测试版本并提交发布。
2. 可用范围只加入测试用户 A/B 和测试管理员。
3. 确认权限、事件和回调变更已包含在当前发布版本中。
4. 在飞书客户端搜索机器人并打开单聊；若搜不到，优先检查应用是否发布、用户是否在可用范围内。

## 5. 项目配置与部署

### 5.1 密钥和服务配置

通过环境变量配置 AiAssistant：

```powershell
$env:FEISHU_ENABLED = "true"
$env:FEISHU_APP_ID = "cli_xxx"
$env:FEISHU_APP_SECRET = "从安全凭证系统注入"
```

以下两处服务密钥必须相同，生产环境应改为高熵密钥并通过环境变量/密钥系统注入：

- `AiAssistant/application.yaml`：`workorder.channel.service-key`
- Backend：`auth.channel-service-key`

关键配置：

| 配置 | 默认值 | 说明 |
|---|---:|---|
| `workorder.channel.feishu.enabled` | `false` | 是否启动飞书长连接 |
| `dedup-ttl` | `PT24H` | 飞书 messageId 去重周期 |
| `confirmation-ttl` | `PT10M` | 写确认有效期 |
| `confirmation-retention` | `P7D` | 确认审计保留时间 |
| `max-requests-per-minute` | `30` | 单飞书身份限流 |
| Backend `channel-access-ttl` | `5m` | 用户代理 Token 有效期 |

### 5.2 构建

```powershell
Set-Location backend
mvn clean package

Set-Location ..\cli-service
mvn clean package

Set-Location ..\workorder-cli
go build -o workorder-cli.exe .

Set-Location ..\AiAssistant
mvn clean package
```

### 5.3 启动顺序与检查

启动依赖后按顺序启动 Backend（8080）、CLI Service（5000）、AiAssistant（8081）。

```powershell
java -jar backend\workorder-server\target\workorder-server-1.0.0-SNAPSHOT.jar
java -jar cli-service\target\cli-service-0.0.1-SNAPSHOT.jar
java -jar AiAssistant\target\AiAssistant-0.0.1-SNAPSHOT.jar
```

检查项：

- Backend 8080、CLI Service 5000、AiAssistant 8081 均监听；
- `GET http://localhost:8081/assistant/` 返回 `AiAssistant is running`；
- `/actuator/health` 中 `feishuChannel` 不为 `DOWN`；
- AiAssistant 日志出现飞书 WebSocket 建连信息；
- 日志中不出现完整 App Secret、Bearer Token、密码。

## 6. 首次账号绑定

用户不能在飞书聊天中发送工单账号或密码。

1. 用户 A 在可信终端登录：

```powershell
workorder-cli auth login --phone 13812345678 --password newPassword123!
```

2. 获取一次性绑定码：

```powershell
workorder-cli channel feishu bind-code
```

3. 在有效期内，把输出的绑定码作为一条独立文本发给机器人。
4. 预期机器人回复“绑定成功”，只展示脱敏姓名和工号。
5. 重发同一绑定码应失败；绑定码不得写入长期文档。
6. 解绑：

```powershell
workorder-cli channel feishu unbind
```

绑定使用 `tenantKey + unionId` 识别飞书用户。正常消息到达后，AiAssistant 使用内部服务密钥换取该用户的短期 `channel_proxy` Token；CLI 和 Backend 仍按真实用户逐请求鉴权。

## 7. 测试数据和验收记录

每次测试记录：版本/提交、环境、执行人、测试用户、飞书 messageId、sessionId、operationId、Agent/CLI traceId。不得记录密码、Refresh Token、完整 Access Token 或 App Secret。

建议准备：

- 用户 A 可查看的工单 `QA-A-001`；
- 用户 B 可查看而 A 不可查看的工单 `QA-B-001`；
- 有效流程 ID；
- 唯一创建标题：`飞书验收-创建-<日期时间>`。

## 8. 测试 Case：查询工单

### FEISHU-Q-001 按工单编号查询

前置条件：用户 A 已绑定，`QA-A-001` 存在且 A 有权限。

操作：用户 A 单聊发送：

```text
查询工单 QA-A-001 的详情
```

预期：

1. 机器人在可接受时间内回复；
2. Agent 依次发现 dataCode、读取对应 Schema，再执行查询；
3. 回复包含正确编号、标题、状态等授权字段；
4. 不出现登录提示、密码请求、乱码或完整 Token；
5. Backend/CLI 日志显示真实用户 A，HTTP 鉴权成功；
6. 同一个飞书 `messageId` 重放时不重复调用 Agent、不重复回复。

### FEISHU-Q-002 数据权限隔离

操作：用户 A 查询仅用户 B 可见的 `QA-B-001`。

预期：返回无权限或不存在，不泄露 B 的工单详情。随后用户 B 查询同一工单，应按 B 的权限正常返回。

### FEISHU-Q-003 未绑定查询

操作：解绑 A 后发送查询。

预期：只返回绑定引导，不进入 Agent、不调用 CLI、不暴露任何业务数据。

### FEISHU-Q-004 无效/重复消息

- 发送图片或文件：提示仅支持文本；
- 快速超过限流：提示稍后再试；
- 重放 messageId：只处理一次；
- 两位用户同时查询：结果和会话历史互不串线。

## 9. 测试 Case：创建工单

### FEISHU-C-001 dry-run 后确认创建

前置条件：A 已绑定且有创建权限，流程 ID 有效。

发送：

```text
帮我创建一个工单，类型为需求类，标题“飞书验收-创建-20260813-01”，详情“飞书机器人创建验收”，高优先级，流程ID为<有效流程ID>
```

预期阶段一（预演）：

1. Agent 执行 `workorder-cli list`；
2. 查询 `work_order_create` Schema；
3. 执行 `workorder-cli --dry-run work_order_create ...`；
4. dry-run 参数详情准确包含：`type=0`、标题、内容、`priorityLevel=0`、flowId；
5. 此时数据库没有新增工单；
6. 飞书收到“请确认写操作”交互卡片，含摘要、到期时间、确认和取消按钮；
7. 卡片与日志不包含密码、Token、App Secret 或 Authorization Header。

点击“确认执行”。预期阶段二（执行）：

1. 只有原请求用户可以确认；
2. 服务端校验 operationId、用户、会话、命令摘要、有效期和 PENDING 状态；
3. 只删除已验证命令中的 `--dry-run`，其余参数不变；
4. 真实命令只执行一次；
5. 返回创建成功及工单编号/ID；
6. 数据库只新增一条记录；
7. 确认状态为 `PENDING → EXECUTING → SUCCEEDED`；
8. 重复点击确认不产生第二张工单。

### FEISHU-C-002 取消创建

重新发送一个唯一标题的创建请求，收到卡片后点击“取消”。

预期：状态转为 `CANCELLED`，数据库无新增；之后点击确认提示已处理，不执行命令。

### FEISHU-C-003 非原用户确认

让用户 B 尝试操作 A 的待确认卡片。

预期：提示只能由原请求人处理；状态仍为 PENDING；数据库不变化。

### FEISHU-C-004 过期确认

测试环境将 `confirmation-ttl` 临时设为 `PT10S`，收到卡片后等待超过 10 秒再确认。

预期：状态为 `EXPIRED`，不执行真实命令。测试后恢复 `PT10M`。

### FEISHU-C-005 参数缺失和修改

先发送缺少 flowId 的创建请求。预期 Agent 提示补充参数且不生成可执行确认。补充 flowId 后应重新 Schema/dry-run。修改标题或优先级后必须重新 dry-run，旧卡片不能执行修改后的操作。

### FEISHU-C-006 故障与结果未知

在点击确认前停止 Backend 或 CLI Service。

预期：操作进入 FAILED 或 UNKNOWN；系统不自动重试真实写命令。恢复服务后由管理员按 operationId/traceId 核查，用户需发起新的预演。

## 10. 自动化与组件测试

```powershell
Set-Location AiAssistant
mvn test

Set-Location ..\backend
mvn test

Set-Location ..\cli-service
mvn test

Set-Location ..\workorder-cli
go test ./...
```

重点覆盖：`ChannelRouterComponentTest`、`ChannelStageThreeFourTest`、`WriteConfirmationServiceTest`、身份 Token 测试、CLI Schema/鉴权测试。

## 11. 日志和数据核查

- MongoDB `channel_write_confirmations`：检查 operationId、用户、会话、摘要、状态；
- Redis `channel:dedup:*`：messageId 幂等；
- Redis `channel:session:*`：按 tenant、bot、user 隔离会话；
- 关联飞书 messageId、sessionId、userId、operationId、traceId；
- 扫描日志中的 `Bearer `、`password`、App Secret 和已知测试凭证，除脱敏占位符外不得出现；
- 飞书发送失败只能重试发送消息，不能重新执行写操作。

## 12. 常见故障

| 现象 | 排查 |
|---|---|
| 搜不到机器人 | 应用未发布、用户不在可用范围、机器人能力未启用 |
| 收不到消息 | 长连接未建立、未订阅 `im.message.receive_v1`、缺少单聊消息权限 |
| 能收不能回 | 缺少发送消息权限、App Secret 错误、飞书 API 限流 |
| 卡片有但按钮无响应 | 未订阅 `card.action.trigger`、配置了错误/旧版回调、长连接断开 |
| 一直提示未绑定 | tenantKey/unionId 不一致、绑定码过期/已用、Backend 服务密钥不一致 |
| CLI 返回 401 | channel_proxy Token 过期、用户/绑定被禁用、CLI 仍是旧二进制 |
| 重复回复或重复写入 | messageId 去重/Redis异常；写操作需进一步检查 operationId 状态机 |
| 中文乱码 | 全链路使用 UTF-8；客户端不要按 ISO-8859-1 解码 SSE/JSON |

## 13. 清理与通过标准

测试完成后解绑 A/B，恢复或删除测试工单，只清理本轮 operationId 和明确记录的 Redis 测试键，撤销测试会话和终端环境变量；禁止通配删除共享数据。

通过标准：查询结果正确且不越权；未绑定不能访问业务；创建操作未确认绝不写入；确认只执行一次；取消、越权、过期和结果未知均不写入；无身份串用、重复写入、中文乱码和敏感凭证泄漏。

## 14. 官方参考

- [使用长连接接收事件](https://open.feishu.cn/document/server-docs/event-subscription-guide/event-subscription-configure-/request-url-configuration-case?lang=zh-CN)
- [接收消息事件](https://open.feishu.cn/document/server-docs/im-v1/message/events/receive?lang=zh-CN)
- [使用长连接接收回调](https://open.feishu.cn/document/event-subscription-guide/callback-subscription/receive-and-handle-callbacks?lang=zh-CN)
- [处理卡片回调](https://open.feishu.cn/document/uAjLw4CM/ukzMukzMukzM/feishu-cards/handle-card-callbacks?lang=zh-CN)
