# 智能工单系统（Work Order System）

一个面向企业运维场景的多入口工单管理系统。系统覆盖工单创建、逐级审批、派单、处理、确认、取消、查询与统计等完整流程，并提供 Go CLI、AI 对话助手和飞书渠道接入能力。

## 功能概览

- **工单管理**：创建、分页查询、详情、全文搜索、审批、派单、处理、确认、取消、删除、导出和打印。
- **流程管理**：配置审核/处理流程，支持多级审核与多人协作处理。
- **组织与权限**：公司、部门、员工管理，JWT 登录认证和接口权限控制。
- **数据看板**：工单状态/类型统计、处理量统计、待办和消息中心。
- **命令行操作**：通过结构化 JSON 输出查询或修改工单，支持 Schema 内省和写操作 `--dry-run` 预览。
- **AI 助手**：自然语言问答、CLI 工具调用、RAG 知识库、短期会话记忆和流式响应。
- **多渠道接入**：支持 Web API，并可选接入飞书长连接与账号绑定。

## 工单流转

```text
创建工单
   ↓
未审核(100) → 审核中(200) ──驳回──→ 审核失败(270)
                   │通过
                   ↓
              未派单(300)
                   ↓
               处理中(400) ──超时──→ 已超时(410)
                   ↓
               已完成(500)
                   ↓
             确认完成(600)
                   └──确认不通过──→ 确认失败(670) → 重新处理

任意允许取消的阶段 ──→ 已取消(700)
```

工单类型：`0` 需求、`1` 故障；优先级：`0` 高、`1` 中、`2` 低。

## 系统架构

```text
Web / App / Postman ───────────────────────────────→ Backend :8080
                                                           ↑
AI Assistant :8081 → workorder-cli → CLI Service :5000 ───┘
       │
       ├── MongoDB：会话记忆
       ├── Redis：渠道会话与消息去重
       └── Elasticsearch + 本地 ONNX：RAG 检索

Backend → MySQL（业务数据）
        → RabbitMQ（工单消息）
        → Canal → Elasticsearch（工单搜索索引）
```

| 模块 | 端口 | 说明 |
| --- | ---: | --- |
| `backend/workorder-api` | - | 共享实体、DTO、枚举与服务接口 |
| `backend/workorder-server` | 8080 | 核心业务、认证、流程、组织、看板和搜索服务 |
| `cli-service` | 5000 | CLI 统一查询/写入网关，通过 OpenFeign 调用核心后端 |
| `workorder-cli` | - | Go 命令行客户端，适合人工、脚本和 Agent 调用 |
| `AiAssistant` | 8081 | AI Agent、RAG、会话记忆与飞书渠道 |

## 技术栈

- Java 17、Spring Boot 3.2/3.3、Spring Security、OpenFeign
- MyBatis-Plus、MySQL 8、Elasticsearch 8、Canal、RabbitMQ
- MongoDB、Redis、本地 BGE 中文 ONNX 向量模型
- 智谱 GLM HTTP API、SSE、飞书 OpenAPI
- Go 1.25、Cobra、Viper

## 目录结构

```text
.
├── backend/
│   ├── workorder-api/                 # 公共 API 模块
│   └── workorder-server/              # 核心后端
├── cli-service/                       # CLI 网关
├── workorder-cli/                     # Go CLI
├── AiAssistant/                       # AI 助手
├── CLI_architecture.md                # CLI 架构说明
├── AiAssistant_technical_design.md    # AI 助手设计
├── feishu-channel_technical_design.md # 飞书渠道设计
└── RAG运维说明.md                     # RAG 运维说明
```

## 快速开始

### 1. 环境要求

核心工单链路需要：

- JDK 17、Maven 3.9+
- MySQL 8.x
- Elasticsearch 8.x
- RabbitMQ 3.x
- Canal 1.1.x（用于 MySQL 到 Elasticsearch 的增量同步）
- Go 1.25（使用 CLI 时）

启用 AI 助手还需要：

- MongoDB 6.x、Redis 7.x
- 智谱 API Key
- `BAAI/bge-small-zh-v1.5` ONNX 模型文件

### 2. 初始化数据库

连接 MySQL 后执行：

```sql
source backend/workorder-server/src/main/resources/workorder.sql;
source backend/workorder-server/src/main/resources/organization.sql;
source backend/workorder-server/src/main/resources/auth_stage_one_migration.sql;
source backend/workorder-server/src/main/resources/auth_stage_two_migration.sql;
source backend/workorder-server/src/main/resources/workorder_migration.sql;
```

`workorder.sql` 会创建并切换到 `wos` 数据库。迁移脚本应根据数据库当前版本按需执行；重复执行前请先阅读脚本，避免覆盖已有数据。

### 3. 修改本地配置

核心后端配置位于：

```text
backend/workorder-server/src/main/resources/application.yaml
```

至少确认 MySQL、Elasticsearch、Canal、RabbitMQ（Spring Boot 默认连接 `localhost:5672`）可用。仓库配置中的数据库密码、JWT 密钥和渠道服务密钥均为开发值，部署前必须替换，且不要提交真实凭据。

AI 助手支持加载不入库的本地覆盖配置：

```text
AiAssistant/src/main/resources/application-local.yaml
```

示例：

```yaml
llm:
  api-key: ${ZHIPU_API_KEY}

workorder:
  channel:
    service-key: ${WORKORDER_CHANNEL_SERVICE_KEY}
    feishu:
      enabled: false

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/assistant
    redis:
      host: localhost
      port: 6379
```

后端的 `workorder.auth.channel-service-key` 必须与 AI 助手的 `workorder.channel.service-key` 一致。

### 4. 启动服务

推荐顺序：MySQL → RabbitMQ → Elasticsearch → Canal → Backend → CLI Service → AI Assistant。

```powershell
# 核心后端（先构建共享 API）
cd backend
.\mvnw.cmd clean install
.\mvnw.cmd -pl workorder-server spring-boot:run

# CLI Service（新终端）
cd cli-service
.\mvnw.cmd spring-boot:run

# Go CLI（新终端）
cd workorder-cli
go build -o workorder-cli.exe .

# AI 助手（可选，新终端）
cd AiAssistant
mvn spring-boot:run
```

> `cli-service` 依赖 `workorder-api:1.0.0-SNAPSHOT`，因此首次启动前需要先在 `backend` 执行 `clean install`。

### 5. 验证服务

- 后端 API 文档：<http://localhost:8080/doc.html>
- CLI Service：<http://localhost:5000/api/dataCodes>
- AI 助手首页：<http://localhost:8081/assistant/>
- AI 健康检查：<http://localhost:8081/actuator/health>

## 使用方式

### REST API

登录接口：

```bash
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"你的手机号","password":"你的密码"}'
```

登录成功后，受保护接口需要携带返回的 Access Token。具体 Header 格式及请求模型以 Knife4j 接口文档为准。

常用接口：

| 能力 | 接口 |
| --- | --- |
| 工单分页/详情/搜索 | `POST /workOrder/page`、`POST /workOrder/detail`、`GET /workOrder/search` |
| 创建/处理/审批 | `POST /workOrder/create`、`POST /workOrder/handle`、`POST /workOrder/approval` |
| 取消/删除 | `POST /workOrder/cancel`、`POST /workOrder/delete` |
| 流程管理 | `/flow/create`、`/flow/edit`、`/flow/delete`、`/flow/page` |
| 数据看板 | `/dashboard/data`、`/dashboard/todo`、`/dashboard/status`、`/dashboard/type` |
| 组织管理 | `/admin/company/*`、`/admin/department/*`、`/admin/staff/*` |

### Go CLI

CLI 默认连接 Backend `http://localhost:8080` 和 CLI Service `http://localhost:5000`。

```powershell
cd workorder-cli

# 登录并在本机保存 Token
.\workorder-cli.exe auth login --phone 13800000000 --password your-password
.\workorder-cli.exe auth status

# 查看可用 dataCode 与参数 Schema
.\workorder-cli.exe list
.\workorder-cli.exe schema work_order_page

# 查询工单
.\workorder-cli.exe work_order page --page-num 1 --page-size 10
.\workorder-cli.exe work_order detail --code WO20260001
.\workorder-cli.exe work_order search --keyword 服务器

# 查询流程和看板
.\workorder-cli.exe flow page --page-num 1 --page-size 10
.\workorder-cli.exe dashboard data
.\workorder-cli.exe dashboard messages --page-num 1 --page-size 10

# 写操作先预览，再执行
.\workorder-cli.exe --dry-run work_order create --type 1 --title "服务器告警" --content "CPU持续高负载" --priority-level 0 --flow-id 1
.\workorder-cli.exe work_order create --type 1 --title "服务器告警" --content "CPU持续高负载" --priority-level 0 --flow-id 1
```

也可通过环境变量覆盖地址或传入 Token：

```powershell
$env:WORKORDER_BACKEND_URL = "http://localhost:8080"
$env:WORKORDER_CLI_SERVICE_URL = "http://localhost:5000"
$env:WORKORDER_TOKEN = "your-access-token"
```

涉及流程节点等复杂 JSON 时，推荐使用文件，避免 Shell 转义问题：

```powershell
.\workorder-cli.exe --dry-run flow create --flow-name "故障处理流程" --body @flow.json
```

### AI 助手

`/assistant/chat` 返回 `text/event-stream` 流式响应：

```bash
curl -N -X POST http://localhost:8081/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"memoryId":10001,"message":"帮我查询处理中的故障工单"}'
```

同一个 `memoryId` 会复用短期会话记忆；清除会话：

```bash
curl -X DELETE http://localhost:8081/assistant/memory/10001
```

AI 助手调用 CLI 时需要能找到已编译的 `workorder-cli` 可执行文件；具体路径通过 `AiAssistant/application.yaml` 中的 `workorder.cli` 配置调整。RAG 模型目录默认为 `AiAssistant/models/bge-small-zh-v1.5`，应包含 `tokenizer.json` 与 `onnx/model.onnx`。

## 测试

```powershell
# 后端单元测试
cd backend
.\mvnw.cmd test

# CLI Service 单元测试
cd cli-service
.\mvnw.cmd test

# AI 助手单元测试
cd AiAssistant
mvn test

# Go CLI
cd workorder-cli
go test ./...
```

仓库还提供以下端到端脚本和用例：

- `backend/workorder-api/test-api.ps1`
- `cli-service/test-cli-service.ps1`
- `cli-service/test-cli-service-write.ps1`
- `workorder-cli/test-cli-write-commands.ps1`
- `workorder-cli/VERIFICATION_CASES.md`
- `FEISHU_STAGE5_STAGE6_TEST_GUIDE.md`

运行集成测试前，请确保依赖服务已启动，并检查脚本中的地址、账号和测试数据是否适合当前环境。

## 常见问题

### CLI Service 提示找不到 `workorder-api`

先在 `backend` 目录运行 `.\mvnw.cmd clean install`，将共享模块安装到本地 Maven 仓库。

### 工单能写入 MySQL，但搜索不到

检查 Elasticsearch 和 Canal 是否运行、Canal destination 是否为 `example`，并确认 ES 索引同步状态。

### AI 助手启动失败

依次检查 MongoDB、Redis、Elasticsearch、ONNX 模型路径和 LLM API Key。若暂时不使用 RAG，可在本地配置中关闭 `llm.embedding.enabled`。

### 请求返回 401/Token 过期

CLI 执行 `auth refresh` 或重新 `auth login`；REST 客户端则调用 `/api/auth/refresh` 或重新登录。

## 延伸文档

- [CLI 架构](CLI_architecture.md)
- [CLI 技术设计](workorder-cli_technical_design.md)
- [CLI 写服务技术设计](workorder-cli-write-service_technical_design.md)
- [AI 助手技术设计](AiAssistant_technical_design.md)
- [飞书渠道技术设计](feishu-channel_technical_design.md)
- [RAG 运维说明](RAG运维说明.md)
- [认证阶段一](AUTH_STAGE_ONE.md) / [认证阶段二](AUTH_STAGE_TWO.md)

## 安全提示

本仓库默认配置面向本地开发。上线前务必更换数据库密码、JWT Secret、渠道 Service Key 和 LLM API Key，通过环境变量或密钥管理服务注入；同时关闭不必要的调试日志、限制 Knife4j/Actuator 暴露范围，并为 MySQL、RabbitMQ、Redis、MongoDB 和 Elasticsearch 启用认证与网络访问控制。
