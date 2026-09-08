# workorder-cli 三期技术架构文档

## 一、概述

本方案旨在设计并实现 `@workOrder/cli` npm包，提供面向AI Agent的工单系统命令行工具。参考飞书CLI的设计模式，采用Go语言实现核心功能，通过npm包分发，Java Agent通过进程调用方式集成。

### 设计原则

| 设计原则 | 说明 |
|----------|------|
| AI友好 | 所有操作通过参数一次性传入，不弹交互式菜单，输出JSON格式 |
| 自描述 | 支持命令发现和Schema内省，Agent可动态获取可用命令和参数说明 |
| 元数据驱动 | Schema从后端CLI Service动态获取，新增API无需修改CLI工具代码 |
| 三层架构 | Shortcuts（快捷命令）、API Commands（API映射）、Raw API（原始调用） |
| 安全可控 | 支持权限校验、命令预览（--dry-run）、输入验证 |

### 核心目标

1. 替代现有的CLI Service（Java实现，端口5000），提供更轻量、高性能的命令行工具
2. 支持Java Agent通过进程调用方式集成
3. 提供与现有CLI Service一致的接口行为，确保平滑迁移
4. 支持人工直接使用，提供良好的命令行体验

---

## 二、技术架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         用户界面 / Java Agent                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                AiAssistant (ReAct Agent)                          │  │
│  │  通过SKILL.md学习如何调用workorder-cli                           │  │
│  │  CliExecutorTools.executeCliCommand() → ProcessBuilder          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                           │
│                              ▼ 进程调用 (ProcessBuilder)                 │
┌─────────────────────────────────────────────────────────────────────────┐
│                     @workOrder/cli (npm包)                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  scripts/run.js (入口脚本)                                         │  │
│  │  通过 execFileSync() 调用 Go 二进制文件                             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                           │
│                              ▼ 进程调用 (execFileSync)                   │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │              Go 编译的二进制文件 (bin/workorder-cli)                │  │
│  │                                                                   │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐   │  │
│  │  │ 认证模块     │  │ 命令解析器    │  │  Schema管理            │   │  │
│  │  │ (Token管理)  │  │ (Cobra框架)  │  │ (动态获取)             │   │  │
│  │  └──────┬───────┘  └──────┬───────┘  └───────────┬───────────┘   │  │
│  │         │                 │                      │               │  │
│  │         └────────┬────────┴──────────────────────┘               │  │
│  │                  ▼                                               │  │
│  │  ┌───────────────────────────────────────────────────────────┐   │  │
│  │  │                    APIClient (HTTP客户端)                  │   │  │
│  │  │  调用后端API，携带 Authorization: <token>                  │   │  │
│  │  └───────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                           │
│                              ▼ HTTP 请求                                 │
┌─────────────────────────────────────────────────────────────────────────┐
│                CLI Service (端口 5000) + Backend (端口 8080)            │
│  ┌──────────────────┐  ┌─────────────────────────────────────────┐     │
│  │ Schema管理接口   │  │        业务API接口                        │     │
│  │ /api/dataCodes   │  │  /workOrder/* /dashboard/* /flow/*      │     │
│  │ /api/schema/*    │  │  /user/login                            │     │
│  └──────────────────┘  └─────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 调用链路说明

#### 链路1：Java Agent → Go CLI工具 → 后端API

```
Java Agent
  │
  ▼ 通过SKILL.md学习命令格式
  │  命令: workorder-cli work_order page --page-num 1 --page-size 10
  │  环境变量: WORKORDER_TOKEN=xxx
  │
  ▼ ProcessBuilder创建进程
  │
  ▼ Go CLI工具执行
  │  1. 解析命令参数
  │  2. 从环境变量读取Token
  │  3. 获取Schema（如需）
  │  4. 构建HTTP请求
  │  5. 调用后端API
  │  6. 返回统一格式JSON结果
  │
  ▼ JSON结果返回到Java Agent
```

#### 链路2：终端用户 → Go CLI工具 → 后端API

```
终端用户
  │
  ▼ 执行命令: workorder-cli work_order page --page-num 1 --page-size 10
  │
  ▼ Go CLI工具执行
  │  1. 解析命令参数
  │  2. 从配置文件读取Token
  │  3. 构建HTTP请求
  │  4. 调用后端API
  │  5. 格式化输出（表格/JSON）
  │
  ▼ 结果输出到终端
```

### 2.3 统一响应格式

与后端CLI Service保持一致，所有响应采用统一格式：

**成功响应**：
```json
{
    "code": 0,
    "message": "success",
    "data": {...},
    "traceId": "xxx"
}
```

**错误响应**：
```json
{
    "code": 404,
    "message": "Schema not found for dataCode: xxx",
    "data": null,
    "traceId": "xxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 状态码，0表示成功，非0表示错误 |
| message | string | 提示信息 |
| data | any | 响应数据 |
| traceId | string | 追踪ID，用于日志追踪 |

---

## 三、功能点

### 3.1 认证管理

| 功能 | 说明 |
|------|------|
| 登录命令 | `workorder-cli auth login --phone xxx --password xxx` |
| 登出命令 | `workorder-cli auth logout` |
| Token状态 | `workorder-cli auth status` |
| Token获取 | 通过 `/user/login` 接口获取 |
| Token存储 | 存储到 `~/.workorder/token` |
| Token传递 | 支持命令行参数、环境变量、配置文件三种方式 |

### 3.2 命令体系

#### 3.2.1 Shortcuts（快捷命令）

带 `+` 前缀的高频操作：

| 命令 | 功能 | 映射API |
|------|------|--------|
| `work_order +list` | 查询工单列表 | work_order_page |
| `work_order +search --keyword xxx` | 关键词搜索 | work_order_search |
| `dashboard +overview` | 数据概览 | dashboard_data |

#### 3.2.2 API Commands（API映射）

与后端API一一对应：

| 命令 | 功能 | 后端端点 |
|------|------|----------|
| `work_order page` | 工单分页查询 | POST /workOrder/page |
| `work_order detail` | 工单详情 | GET /workOrder/detail |
| `work_order search` | 工单搜索 | POST /workOrder/search |
| `dashboard data` | 数据看板 | GET /dashboard/data |
| `dashboard handle_quantity` | 本周处理数量 | GET /dashboard/handleQuantity |
| `dashboard messages` | 消息中心 | POST /dashboard/pageMessages |
| `flow getById` | 流程详情 | GET /flow/getById |
| `flow page` | 流程分页 | POST /flow/page |

#### 3.2.3 Raw API（原始调用）

直接调用任意后端接口：

| 命令 | 功能 |
|------|------|
| `api call --method GET --endpoint /xxx` | GET请求 |
| `api call --method POST --endpoint /xxx --body '{}'` | POST请求 |

### 3.3 Schema内省

| 命令 | 功能 | 后端接口 |
|------|------|----------|
| `list` | 获取所有可用命令列表 | GET /api/dataCodes |
| `schema <dataCode>` | 获取指定命令的Schema | GET /api/schema/:dataCode |

### 3.4 输出格式
输出都是JSON格式，AI友好。

### 3.5 安全特性

| 功能 | 说明 |
|------|------|
| 命令预览 | `--dry-run` 参数，预览请求不实际执行 |
| 权限校验 | 依赖后端API权限控制 |
| Token安全 | 存储在用户主目录，权限600 |

---

## 四、目录结构

### 4.1 npm包结构

```
@workOrder/cli/
├── bin/
│   └── workorder-cli           # Go编译的二进制文件
├── scripts/
│   ├── run.js                  # 入口脚本
│   ├── install.js              # 安装脚本（下载二进制文件）
│   └── install-wizard.js       # 安装向导（可选）
├── schemas/
│   └── data-schemas.json       # 本地缓存（可选）
├── skills/
│   └── workorder-query/
│       └── SKILL.md            # AI Agent技能文档
├── package.json
└── README.md
```

### 4.2 Go项目结构

```
workorder-cli/
├── cmd/
│   ├── root.go                 # 根命令
│   ├── work_order.go           # 工单命令
│   ├── dashboard.go            # 数据看板命令
│   ├── flow.go                 # 流程命令
│   ├── auth.go                 # 认证命令
│   └── api.go                  # Raw API命令
├── internal/
│   ├── api/                    # HTTP客户端
│   ├── auth/                   # 认证管理
│   ├── schema/                 # Schema管理
│   ├── parser/                 # 命令解析
│   ├── output/                 # 输出格式化
│   └── config/                 # 配置管理
├── main.go
├── go.mod
└── go.sum
```

---

## 五、AiAssistant改造方案

### 5.1 改造目标

1. 将HTTP调用CLI Service改为进程调用workorder-cli
2. 保持工具方法签名不变，Agent业务逻辑无需修改
3. Agent通过SKILL.md学习如何调用CLI，而非硬编码

### 5.2 改造前架构

```
AiAssistant → HTTP请求 → CLI Service (端口5000) → Backend (端口8080)
```

### 5.3 改造后架构

```
AiAssistant → 进程调用(ProcessBuilder) → workorder-cli → CLI Service/Backend
```

### 5.4 SKILL.md设计

AiAssistant通过SKILL.md学习CLI调用方式，SKILL.md包含：

| 内容 | 说明 |
|------|------|
| 命令格式 | 如何构造CLI命令字符串 |
| 参数传递 | 如何传递参数（特别是JSON参数） |
| 认证方式 | Token如何获取和传递 |
| 响应解析 | 如何解析CLI输出的JSON结果 |
| 错误处理 | 如何处理不同类型的错误 |
| 示例场景 | 常见操作的命令示例 |

**SKILL.md更新时机**：
- CLI工具新增命令时更新
- CLI命令参数变更时更新
- 后端API变更导致Schema变更时更新

### 5.5 需要修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CliExecutorTools.java` | 将HTTP调用改为进程调用 |
| `application.yml` | 移除CLI Service URL配置 |
| `skills/workorder-query/SKILL.md` | 更新CLI调用说明 |

### 5.6 退出码约定

| 退出码 | 含义 | AiAssistant处理 |
|--------|------|----------------|
| 0 | 成功 | 返回JSON结果 |
| 1 | 通用错误 | 抛出RuntimeException |
| 2 | 认证错误 | 抛出AuthenticationException |
| 3 | 超时 | 抛出TimeoutException |
| 4 | 参数错误 | 抛出IllegalArgumentException |

### 5.7 环境变量配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `WORKORDER_TOKEN` | 认证Token | 从SessionContext获取 |
| `WORKORDER_TRACE_ID` | 追踪ID | 自动生成 |
| `WORKORDER_CLI_PATH` | CLI二进制路径 | `workorder-cli` |

---

## 六、测试方案

### 6.1 CLI工具测试

#### 6.1.1 单元测试

| 测试场景 | 测试命令 | 预期结果 |
|----------|----------|----------|
| 命令解析 | `workorder-cli work_order page --page-num 1` | 参数正确解析 |
| Token读取 | 环境变量设置TOKEN后执行命令 | Token正确传递到请求头 |
| 输出格式 | `workorder-cli work_order page --output json` | 输出JSON格式 |
| 命令预览 | `workorder-cli work_order page --dry-run` | 不实际调用API |

#### 6.1.2 集成测试

| 测试场景 | 测试步骤 | 预期结果 |
|----------|----------|----------|
| 登录功能 | `workorder-cli auth login --phone xxx --password xxx` | 返回成功，Token保存 |
| 工单查询 | `workorder-cli work_order page --page-num 1 --page-size 10` | 返回工单列表 |
| Schema获取 | `workorder-cli schema work_order_page` | 返回参数Schema |
| 命令列表 | `workorder-cli list` | 返回所有可用命令 |

#### 6.1.3 接口脚本

**登录测试**：
```bash
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"phone": "13812345678", "password": "newPassword123!"}'
```

**工单查询测试**：
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."
curl -X POST http://localhost:8080/workOrder/page \
  -H "Authorization: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pageNum": 1, "pageSize": 10}'
```

**Schema获取测试**：
```bash
curl http://localhost:5000/api/dataCodes
curl http://localhost:5000/api/schema/work_order_page
```

### 6.2 AiAssistant集成测试

#### 6.2.1 单元测试

| 测试场景 | 方法 | 预期结果 |
|----------|------|----------|
| 命令执行 | `executeCliCommand("work_order page --page-num 1")` | 返回JSON结果 |
| 命令列表 | `listCliCommands()` | 返回命令列表 |
| Schema获取 | `getCliCommandSchema("work_order_page")` | 返回Schema |
| 认证失败 | 使用无效Token执行命令 | 抛出AuthenticationException |
| 超时 | 执行耗时超过30秒的命令 | 抛出TimeoutException |

#### 6.2.2 集成测试

| 测试场景 | 测试步骤 | 预期结果 |
|----------|----------|----------|
| 完整流程 | 启动Backend → 启动CLI Service → 安装CLI → 启动AiAssistant → 调用聊天接口查询工单 | 返回工单信息 |
| Token传递 | 登录后执行查询命令 | Token正确传递，查询成功 |
| 错误处理 | 使用过期Token执行命令 | 返回认证错误提示 |

#### 6.2.3 接口脚本

**Agent聊天测试**：
```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "帮我查一下处理中的工单",
    "userId": "1"
  }'
```

**登录测试**：
```bash
curl -X POST http://localhost:8081/api/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13812345678",
    "password": "newPassword123!"
  }'
```

---

## 七、调试方法

### 7.1 CLI工具调试

| 方法 | 说明 |
|------|------|
| 日志查看 | `tail -f ~/.workorder/cli.log` |
| 命令调试 | `workorder-cli <command> --debug` |
| 网络抓包 | 使用Wireshark或tcpdump监控CLI与后端的HTTP通信 |
| 环境变量 | 设置 `WORKORDER_DEBUG=true` 开启调试模式 |

### 7.2 AiAssistant调试

| 方法 | 说明 |
|------|------|
| 日志查看 | 查看AiAssistant应用日志 |
| 进程监控 | 使用jps/jstack查看Java进程状态 |
| 命令追踪 | 在CliExecutorTools中添加详细日志 |
| 响应验证 | 验证CLI输出的JSON格式是否正确 |

### 7.3 常见问题排查

| 问题 | 排查方法 |
|------|----------|
| CLI命令不存在 | 检查是否安装成功，检查PATH环境变量 |
| Token无效 | 检查Token是否过期，重新登录 |
| 连接超时 | 检查后端服务是否启动，检查网络连通性 |
| 权限不足 | 检查用户角色，检查API权限配置 |

---

## 八、部署方案

### 8.1 CLI工具部署

#### 8.1.1 安装方式

**npm安装**：
```bash
npm install -g @workOrder/cli
```

**npx使用**：
```bash
npx @workOrder/cli@latest work_order page --page-num 1
```

**手动安装**：
```bash
# 下载对应平台的二进制文件
wget https://github.com/workOrder/cli/releases/download/v1.0.0/workorder-cli_1.0.0_linux_amd64.tar.gz

# 解压
tar -xzf workorder-cli_1.0.0_linux_amd64.tar.gz

# 移动到PATH目录
mv workorder-cli /usr/local/bin/
```

#### 8.1.2 跨平台支持

| 操作系统 | 架构 | 二进制文件名 |
|---------|------|------------|
| Linux | amd64 | workorder-cli_linux_amd64 |
| Linux | arm64 | workorder-cli_linux_arm64 |
| macOS | amd64 | workorder-cli_darwin_amd64 |
| macOS | arm64 | workorder-cli_darwin_arm64 |
| Windows | amd64 | workorder-cli_windows_amd64.exe |

#### 8.1.3 依赖要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| Node.js | ≥ 18 | npm包安装和运行 |
| Go | ≥ 1.23 | 编译CLI工具（开发环境） |

### 8.2 AiAssistant部署

#### 8.2.1 部署步骤

1. 安装workorder-cli：`npm install -g @workOrder/cli`
2. 确保workorder-cli在系统PATH中
3. 修改AiAssistant配置，移除CLI Service URL
4. 启动Backend服务（端口8080）
5. 启动CLI Service（端口5000）
6. 启动AiAssistant服务

#### 8.2.2 配置文件

```yaml
# application.yml
workorder:
  cli:
    timeout-seconds: 30
    cli-path: workorder-cli
```

---

## 九、实现计划

### 9.1 功能点拆分

| 功能模块 | 功能点 | 依赖 |
|----------|--------|------|
| 基础框架 | Go项目初始化、Cobra配置、命令行入口 | 无 |
| 认证模块 | Token获取、存储、传递、登录/登出命令 | 基础框架 |
| HTTP客户端 | API请求封装、响应解析、统一响应格式 | 认证模块 |
| Schema管理 | 动态获取Schema、命令列表、Schema内省 | HTTP客户端 |
| 命令实现 | 工单/看板/流程命令、参数映射、API调用 | Schema管理 |
| Shortcuts | 快捷命令定义、参数默认值、场景封装 | 命令实现 |
| 输出格式化 | JSON/表格/CSV输出、格式化配置 | 命令实现 |
| npm包发布 | 安装脚本、跨平台编译、goreleaser配置 | 所有功能 |
| AiAssistant集成 | CliExecutorTools改造、SKILL.md更新 | npm包发布 |

### 9.2 阶段划分

#### Phase 1：基础框架

| 功能点 | 说明 |
|--------|------|
| Go项目初始化 | 创建项目结构、go.mod配置 |
| Cobra框架配置 | 根命令定义、子命令注册机制 |
| 命令行入口 | main.go实现、参数解析 |
| 目录结构搭建 | internal包划分、配置管理 |

#### Phase 2：认证模块

| 功能点 | 说明 |
|--------|------|
| Token获取 | 调用/login接口获取Token |
| Token存储 | 保存到~/.workorder/token |
| Token传递 | 环境变量/配置文件/命令行参数 |
| 登录命令 | auth login/logout/status命令 |
| Token过期处理 | 自动检测、提示重新登录 |

#### Phase 3：HTTP客户端

| 功能点 | 说明 |
|--------|------|
| API请求封装 | GET/POST请求、Header设置 |
| 响应解析 | 统一响应格式解析并透传 |

#### Phase 4：Schema获取

| 功能点 | 说明 |
|--------|------|
| Schema获取 | 调用CLI Service接口 |
| 命令列表 | list命令实现 |
| Schema内省 | schema命令实现 |
| dataCode映射 | dataCode到API端点的映射 |

#### Phase 5：命令实现

| 功能点 | 说明 |
|--------|------|
| 工单命令 | page/detail/search命令 |
| 看板命令 | data/handle_quantity/messages命令 |
| 流程命令 | getById/page命令 |
| 参数映射 | CLI参数到API参数的转换 |
| 命令路由 | 根据命令分发到对应handler |

#### Phase 6：Shortcuts与输出

| 功能点 | 说明 |
|--------|------|
| Shortcuts定义 | +list/+search/+overview等 |
| 默认参数 | 为Shortcuts设置合理默认值 |
| JSON输出 | 标准JSON格式输出 |
| 表格输出 | 人类友好的表格展示 |
| 命令预览 | --dry-run参数实现 |

#### Phase 7：npm包发布

| 功能点 | 说明 |
|--------|------|
| 安装脚本 | install.js平台检测和下载 |
| 入口脚本 | run.js调用Go二进制文件 |
| goreleaser配置 | 跨平台编译配置 |
| 版本管理 | npm版本发布流程 |
| 校验和验证 | SHA256校验确保完整性 |

#### Phase 8：AiAssistant集成

| 功能点 | 说明 |
|--------|------|
| CliExecutorTools改造 | HTTP调用改为进程调用 |
| 配置更新 | application.yml修改 |
| SKILL.md更新 | CLI调用方式说明 |
| 集成测试 | 端到端测试验证 |
| 文档更新 | 技术文档完善 |

### 9.3 关键里程碑

| 里程碑 | 阶段 | 目标 |
|--------|------|------|
| M1 | Phase 2结束 | 认证功能可用，能获取和使用Token |
| M2 | Phase 4结束 | Schema内省可用，能获取命令列表和参数说明 |
| M3 | Phase 5结束 | 所有API命令可用 |
| M4 | Phase 7结束 | npm包发布成功 |
| M5 | Phase 8结束 | AiAssistant集成完成，全流程验证通过 |

### 9.4 依赖关系图

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8
           │            │            │            │            │
           ▼            ▼            ▼            ▼            ▼
        认证模块      HTTP客户端    Schema管理   命令实现     Shortcuts
                                                               │
                                                               ▼
                                                         npm包发布
                                                               │
                                                               ▼
                                                        AiAssistant集成
```

---

## 十、安全考虑

### 10.1 Token安全

- Token存储在用户主目录下，权限设置为仅用户可读（600）
- Token通过环境变量传递，不在命令行参数中暴露
- 支持Token过期自动检测和清理

### 10.2 输入验证

- 所有命令参数进行类型校验
- 禁止命令注入攻击
- JSON参数进行严格解析

### 10.3 日志安全

- 日志中不记录完整Token
- 敏感参数（password等）在日志中脱敏

### 10.4 权限控制

- 依赖后端API的权限校验
- 支持命令预览（--dry-run）模式

---

## 十一、与二期的兼容性

### 11.1 接口兼容

| 二期CLI Service接口 | 三期CLI工具命令 | 兼容性 |
|-------------------|---------------|--------|
| `/api/dataCodes` | `workorder-cli list` | 兼容 |
| `/api/schema/:dataCode` | `workorder-cli schema <dataCode>` | 兼容 |
| `/api/query` | `workorder-cli <command>` | 兼容 |

### 11.2 Token格式兼容

- Token存储格式与二期 `TokenFileManager` 一致
- Token有效期保持一致
- AiAssistant无需修改认证逻辑

### 11.3 输出格式兼容

- JSON输出格式与二期CLI Service一致
- 保持 `code/message/data/traceId` 结构
- 错误响应格式一致