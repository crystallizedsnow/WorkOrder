# workorder-cli 写服务四期技术方案

## 一、概述

### 1.1 背景与目标

目前 CLI 已具备读数据能力，覆盖工单查询、流程查询、数据看板等场景，形成了「鉴权 → 查 dataCode → 查数据」的完整读服务链路。本期（四期）在此基础上筹备 CLI 写服务改造，让 Agent 能够通过 CLI 执行工单与流程的写操作（创建、处理、删除、取消、审批、流程编辑等）。

写操作具有副作用且不可逆，因此本期核心目标除包装写接口外，还需引入 **dry-run（预演）机制**：Agent 在执行任何写操作前，必须先完整推理出执行计划并展示给用户确认，绝不真正执行任何有副作用的操作。该机制参考飞书 lark-cli 的 `--dry-run` 设计。

### 1.2 改造范围

本期改造覆盖 8 个写接口，分布如下：

| 模块 | 接口数 | 写接口 |
|------|--------|--------|
| 工单管理 | 5 | 创建、处理（handle）、删除、取消、审批 |
| 流程管理 | 3 | 新增、编辑、删除 |
| 合计 | 8 | - |

### 1.3 设计原则

| 原则 | 说明 |
|------|------|
| 复用读服务架构 | 鉴权、TraceID、dataCode 路由、Schema 内省、响应归一化等机制与读服务保持一致 |
| 写操作强制预演 | 所有写命令必须先 dry-run 展示执行计划，确认后再真实执行 |
| 双层防护 | CLI 层硬性阻断（dry-run 不发请求）+ SKILL.md 层流程约束（Agent 必须先预演） |
| 元数据驱动 | 写接口 Schema 统一在 data-schemas.json 维护，新增写接口无需改 CLI 主流程 |
| 最小侵入 | 读服务链路完全不变，写服务作为独立链路并行存在 |

---

## 二、技术架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         用户界面 / Java Agent                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                AiAssistant (ReAct Agent)                          │  │
│  │  通过 SKILL.md 学习写数据流程（鉴权→查dataCode→dry-run→执行）    │  │
│  │  CliExecutorTools.executeCliCommand() → ProcessBuilder            │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                           │
│                              ▼ 进程调用 (ProcessBuilder)                 │
┌─────────────────────────────────────────────────────────────────────────┐
│                     @workOrder/cli (npm包)                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │              Go 编译的二进制文件 (bin/workorder-cli)                │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐   │  │
│  │  │ 认证模块     │  │ 命令解析器    │  │  Schema管理            │   │  │
│  │  │ (Token管理)  │  │ (Cobra框架)  │  │ (动态获取)             │   │  │
│  │  └──────────────┘  └──────┬───────┘  └───────────────────────┘   │  │
│  │                           │                                         │  │
│  │           ┌───────────────┴────────────────┐                       │  │
│  │           ▼                                ▼                       │  │
│  │  ┌──────────────────┐         ┌────────────────────────────┐      │  │
│  │  │ dry-run 预演引擎  │         │  APIClient (HTTP客户端)     │      │  │
│  │  │ (写操作拦截/计划) │         │  调用cli-service写接口      │      │  │
│  │  └──────────────────┘         └────────────────────────────┘      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                              │                                           │
│                              ▼ HTTP 请求                                 │
┌─────────────────────────────────────────────────────────────────────────┐
│                CLI Service (端口 5000) + Backend (端口 8080)            │
│  ┌──────────────────────┐  ┌─────────────────────────────────────┐     │
│  │ 读服务（已有，不变）  │  │  写服务（本期新增）                   │     │
│  │ /api/query           │  │  /api/execute                        │     │
│  │ /api/dataCodes       │  │  /api/dataCodes（读写共用）          │     │
│  │ /api/schema/*        │  │  /api/schema/*（读写共用）           │     │
│  └──────────────────────┘  └──────────────┬──────────────────────┘     │
│                                           │ OpenFeign RPC               │
│                                           ▼                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Backend 写接口                                                  │   │
│  │  /workOrder/create /handle /delete /cancel /approval            │   │
│  │  /flow/create /edit /delete                                     │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 写服务调用链路

写服务复用读服务的「鉴权 → 查 dataCode → 查 Schema」前序流程，在「执行」环节前插入 dry-run 预演节点：

```
Agent 接收写需求
  │
  ▼ 第一步：鉴权（复用读服务）
  │  workorder-cli 从环境变量/配置读取 Token，cli-service 通过 AuthFeignClient 调后端 /auth/validate
  │
  ▼ 第二步：查 dataCode（复用读服务）
  │  listCliCommands() → /api/dataCodes 返回读写全部 dataCode
  │
  ▼ 第三步：查 Schema（复用读服务）
  │  getCliCommandSchema(dataCode) → /api/schema/{dataCode} 返回写接口入参 Schema
  │
  ▼ 第四步：dry-run 预演（本期新增，写操作强制）
  │  executeCliCommand("workorder-cli work_order create ... --dry-run")
  │  workorder-cli 构建完整请求，打印执行计划，不发送任何 HTTP 请求
  │  Agent 将执行计划展示给用户，等待用户确认
  │
  ▼ 第五步：真实执行（用户确认后）
  │  executeCliCommand("workorder-cli work_order create ...")  （去掉 --dry-run）
  │  workorder-cli → cli-service /api/execute → OpenFeign → Backend 写接口
  │  返回归一化 JSON 结果
  │
  ▼ 返回执行结果给用户
```

### 2.3 与读服务的架构对比

| 维度 | 读服务（已有） | 写服务（本期） |
|------|---------------|---------------|
| cli-service 入口 | `/api/query` | `/api/execute` |
| dataCode 路由 | DataCodeEnum（8个读） | WriteDataCodeEnum（8个写，独立枚举） |
| Feign 客户端 | WorkOrderFeignClient 等（仅读方法） | 新增写方法或独立 WriteFeignClient |
| Schema 来源 | data-schemas.json（read 段） | data-schemas.json（write 段，新增） |
| 响应归一化 | QueryService.normalizeResponse | WriteService 复用同一归一化逻辑 |
| 预演机制 | 无（查询无副作用） | dry-run 强制预演 |
| Agent 流程 | 鉴权→查dataCode→查数据 | 鉴权→查dataCode→dry-run→执行 |

### 2.4 dry-run 机制设计（核心）

#### 2.4.1 设计目标

参考飞书 lark-cli 的 `--dry-run` 机制，解决「AI 执行写操作的信任问题」：让用户在每次写操作前都能确认「Agent 要干的事和我要的是一回事」。

#### 2.4.2 工作原理

写命令带 `--dry-run` 参数时，workorder-cli 会：

1. 完整构建写请求（包括 dataCode、请求参数、目标后端端点、操作类型）
2. 将执行计划以可读格式打印到终端
3. **不发送任何 HTTP 请求**，不改变任何数据，不调用 cli-service

#### 2.4.3 dry-run 输出格式

```
[DRY RUN] 工单创建
操作类型: 新建工单 (work_order_create)
后端端点: POST /workOrder/create
请求参数:
  type          : 0          (需求类工单)
  title         : 服务器故障告警
  content       : 生产服务器CPU使用率持续超过90%
  priorityLevel : 0          (高优先级)
  flowId        : 1
  deadlineTime  : 1754179200 (2025-08-03 00:00:00)
预期影响: 将在系统中创建一条新的工单记录，并进入审核流程

→ 以上写操作未执行。确认无误后去掉 --dry-run 重新运行。
```

#### 2.4.4 双层防护策略

| 防护层 | 实现位置 | 作用 | 强制性 |
|--------|---------|------|--------|
| 第一层：CLI 硬阻断 | workorder-cli Go 代码 | `--dry-run` 时不构建 HTTP 请求，直接输出计划 | 硬性，代码级保证 |
| 第二层：SKILL.md 流程约束 | SKILL.md 写数据流程章节 | 规定 Agent 执行写操作前必须先 dry-run 并等待确认 | 软性，Prompt 约束 |

#### 2.4.5 何时必须 dry-run

| 操作类型 | 是否必须 dry-run | 原因 |
|---------|-----------------|------|
| 工单创建 | 是 | 创建后进入流程，影响后续处理人 |
| 工单处理（分配/完成等） | 是 | 改变工单状态，推动流程流转 |
| 工单删除 | 是 | 删除不可恢复 |
| 工单取消 | 是 | 终止流程 |
| 工单审批 | 是 | 审批结果影响工单走向 |
| 流程新增/编辑/删除 | 是 | 影响所有使用该流程的工单 |
| 所有读操作 | 否 | 查询无副作用 |

#### 2.4.6 Agent 写操作工作流

```
用户: 帮我创建一个工单，标题"服务器故障"，高优先级

Agent 思考: 需要创建工单，先查 dataCode 和 Schema
Agent 行动1: listCliCommands() → 获取写命令列表，找到 work_order_create
Agent 行动2: getCliCommandSchema("work_order_create") → 获取入参 Schema

Agent 思考: 参数已明确，写操作必须先 dry-run 预演
Agent 行动3: executeCliCommand("workorder-cli work_order create --type 0 --title 服务器故障 --priority-level 0 --flow-id 1 --dry-run")
结果3: 输出 [DRY RUN] 执行计划

Agent: 向用户展示执行计划，询问是否确认执行

用户: 确认

Agent 行动4: executeCliCommand("workorder-cli work_order create --type 0 --title 服务器故障 --priority-level 0 --flow-id 1")
结果4: 返回创建结果（工单id、编号）

Agent: 工单已创建，编号为 WO202607270001
```

---

## 三、功能点

### 3.1 cli-service 写接口包装

在 cli-service 中包装 8 个写接口，提供统一的 `/api/execute` 入口，复用读服务的鉴权、TraceID、Schema、响应归一化机制。

| 功能 | 说明 |
|------|------|
| 统一写入口 | 新增 `POST /api/execute`，请求体 `{dataCode, params}`，与 `/api/query` 结构一致 |
| 写 dataCode 路由 | 新增 `WriteDataCodeEnum`，路由到对应 Feign 写方法 |
| 写 Schema 管理 | 在 data-schemas.json 新增 write 段，SchemaService 统一管理读写 Schema |
| 写 Feign 客户端 | 在现有 Feign 客户端新增写方法，或独立 WriteFeignClient |
| 响应归一化 | 复用 QueryService 的 normalizeResponse 逻辑，统一为 code/message/data/traceId |
| 权限校验 | 复用 AuthService，按 dataCode 的 permission 字段校验角色 |
| dataCode 列表合并 | `/api/dataCodes` 同时返回读写 dataCode，通过 type 字段区分（read/write） |

### 3.2 SKILL.md 写数据流程

在 SKILL.md 中新增「写数据流程」章节，与读数据流程并列。

| 内容 | 说明 |
|------|------|
| 写流程步骤 | 鉴权 → 查 dataCode → 查 Schema → **dry-run 预演** → 用户确认 → 真实执行 |
| dry-run 强制规则 | 明确规定所有写操作必须先带 `--dry-run` 调用一次，展示计划后等待确认 |
| 写 dataCode 列表 | 列出 8 个写 dataCode 及其参数说明 |
| 写命令示例 | 提供创建、处理、审批、流程编辑等场景的 dry-run + 执行示例 |
| 确认话术约束 | Agent 展示执行计划后必须询问用户，未获确认不得去掉 `--dry-run` |
| 错误处理 | 写操作失败的错误码处理与重试策略（写操作默认不自动重试） |

### 3.3 workorder-cli 写命令实现

参考现有 workorder-cli 读命令（work_order.go、flow.go）的实现模式，新增写子命令。

| 命令 | 子命令 | 功能 | dataCode |
|------|--------|------|----------|
| `work_order create` | - | 新建工单 | work_order_create |
| `work_order handle` | - | 工单处理（分配/协助/催单/完成/确认/仍有问题） | work_order_handle |
| `work_order delete` | - | 删除工单 | work_order_delete |
| `work_order cancel` | - | 取消工单 | work_order_cancel |
| `work_order approval` | - | 审批工单 | work_order_approval |
| `flow create` | - | 新增流程 | flow_create |
| `flow edit` | - | 编辑流程 | flow_edit |
| `flow delete` | - | 删除流程 | flow_delete |

每个写子命令支持 `--dry-run` 全局参数，命中时进入预演分支。

### 3.4 dry-run 机制

| 功能 | 说明 |
|------|------|
| dry-run 拦截 | 写命令执行前检查 `--dry-run`，命中则进入预演分支，不调用 APIClient |
| 执行计划构建 | 从 dataCode + 参数构建可读的执行计划（操作类型、端点、参数、预期影响） |
| 参数可读化 | 枚举值翻译为中文描述（如 priorityLevel 0 → 高优先级），时间戳转可读时间 |
| 预期影响说明 | 根据 dataCode 输出预期影响提示（如"将创建一条新工单"） |
| 退出码约定 | dry-run 模式退出码为 0，便于 Agent 识别预演成功 |
| 读操作豁免 | dry-run 仅对写命令生效，读命令带 `--dry-run` 无副作用（透传或不影响） |

---

## 四、改造方案

### 4.1 cli-service 改造

| 改造项 | 说明 |
|--------|------|
| 新增 WriteDataCodeEnum | 定义 8 个写 dataCode，含 dataCode/name/description/httpMethod/endpoint/permission |
| 新增 WriteService | 仿 QueryService，实现 `execute(dataCode, params, token, traceId)`，路由到 Feign 写方法 |
| ApiController 新增 /api/execute | `POST /api/execute`，接收 `{dataCode, params}`，调用 WriteService |
| dataCodes 接口扩展 | `/api/dataCodes` 返回项增加 `type` 字段（read/write），合并读写枚举 |
| Schema 扩展 | data-schemas.json 新增 8 个写 dataCode 的 inputSchema/outputSchema |
| Feign 客户端扩展 | WorkOrderFeignClient 新增 create/handle/delete/cancel/approval 方法；FlowFeignClient 新增 create/edit/delete 方法 |
| 归一化复用 | WriteService 复用 QueryService 的 normalizeResponse 逻辑（抽为公共方法） |

### 4.2 workorder-cli 改造

| 改造项 | 说明 |
|--------|------|
| root.go 路由扩展 | `work_order`、`flow` 子命令分发新增 create/handle/delete/cancel/approval/edit 分支 |
| work_order.go 扩展 | 新增 handleWorkOrderCreate/Handle/Delete/Cancel/Approval 处理函数 |
| flow.go 扩展 | 新增 handleFlowCreate/Edit/Delete 处理函数 |
| dry-run 预演引擎 | 新增 internal/dryrun 包，构建执行计划、参数可读化、输出格式化 |
| 写命令统一调用 /api/execute | 写命令通过 APIClient 调用 cli-service 的 `/api/execute`（读命令继续调 `/api/query`） |
| dry-run 分支 | 写命令入口检查 `--dry-run`，命中则调用 dryrun 引擎输出计划并 return |
| 参数解析增强 | 支持 `--body` 传入 JSON 复杂参数（如 FlowNode 列表），适配流程创建/编辑 |

### 4.3 SKILL.md 改造

| 改造项 | 说明 |
|--------|------|
| 新增「写数据流程」章节 | 描述鉴权→查dataCode→查Schema→dry-run→确认→执行的完整流程 |
| 新增写 dataCode 列表 | 8 个写 dataCode 的参数说明表格 |
| 新增 dry-run 强制规则 | 明确写操作必须先 dry-run，展示计划后等待用户确认 |
| 新增写操作示例 | 创建工单、处理工单、审批、流程编辑的 dry-run + 执行示例 |
| 新增确认话术模板 | Agent 展示执行计划后的标准询问话术 |

### 4.4 AiAssistant 改造

| 改造项 | 说明 |
|--------|------|
| CliExecutorTools 无需改动 | 进程调用机制不变，写命令通过同一 executeCliCommand 执行 |
| SKILL.md 资源更新 | 将更新后的 SKILL.md（含写流程）放入 resources，供 getSkillContent 加载 |

**注意**：executeCliCommand 的 @Tool 描述不需要描述补充写操作需先 dry-run，它是一个通用的命令执行工具。

### 4.5 写接口 dataCode 清单与 Schema

#### 4.5.1 写 dataCode 清单

| dataCode | 名称 | 后端端点 | 参数类 | 返回类 |
|----------|------|---------|--------|--------|
| work_order_create | 新建工单 | POST /workOrder/create | WorkOrderCreateParam | Result |
| work_order_handle | 工单处理 | POST /workOrder/handle | WorkOrderHandleParam | WorkOrderUpdateStatusVO |
| work_order_delete | 删除工单 | POST /workOrder/delete | WorkOrderDeleteParam | WorkOrderUpdateStatusVO |
| work_order_cancel | 取消工单 | POST /workOrder/cancel | WorkOrderCancelParam | WorkOrderUpdateStatusVO |
| work_order_approval | 工单审批 | POST /workOrder/approval | WorkOrderApprovalParam | Result |
| flow_create | 新增流程 | POST /flow/create | FlowCreateParam | FlowCreateVO |
| flow_edit | 编辑流程 | POST /flow/edit | FlowUpdateParam | FlowCreateVO |
| flow_delete | 删除流程 | POST /flow/delete | FlowIdParam | boolean |

#### 4.5.2 关键写接口参数说明

**work_order_create（新建工单）**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | Integer | 是 | 工单类型（0需求，1故障） |
| title | String | 是 | 工单标题 |
| content | String | 是 | 详情 |
| priorityLevel | Integer | 是 | 优先级（0高，1中，2低） |
| flowId | Long | 是 | 流程Id |
| deadlineTime | Long | 否 | 截止时间（时间戳） |
| accessoryUrl | String | 否 | 附件url |
| accessoryName | String | 否 | 附件文件名 |

**work_order_handle（工单处理）**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 工单id（与code二选一） |
| code | String | 否 | 工单编号（与id二选一） |
| handleType | Integer | 是 | 1分配/2请求协助/3催单/4完成/5确认完成/6仍有问题 |
| assignedUserId | Long | 否 | 分配/协助时必填 |
| remark | String | 否 | 操作备注 |

**work_order_approval（工单审批）**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 工单id（与code二选一） |
| code | String | 否 | 工单编号 |
| remark | String | 否 | 操作备注 |
| isApproved | Boolean | 是 | true通过/false拒绝 |

**flow_create（新增流程）**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flowName | String | 是 | 流程名 |
| nodes | List\<FlowNode\> | 是 | 审核节点列表 |
| distributeNode | FlowNode | 是 | 分配节点 |
| checkNode | FlowNode | 是 | 验收节点 |

FlowNode 结构：handlerId(Long,必填) + handlerName(String,必填)

**技术风险点：嵌套 JSON 参数反序列化**

flow_create 和 flow_edit 需要传入 FlowNode 列表等嵌套 JSON 结构。现有读服务的参数转换（Map→强类型 Param）都是扁平字段映射，写服务需要额外设计嵌套对象的反序列化逻辑：

- **CLI层**：通过 `--body` 参数传入完整 JSON 字符串，Go 层使用 `json.Unmarshal` 解析为 `map[string]interface{}`，再传递给 cli-service
- **cli-service层**：WriteService 在调用 Feign 写方法前，需要将 `Map<String, Object>` 中的嵌套结构（如 nodes、distributeNode、checkNode）反序列化为对应的强类型对象（FlowNode），可通过 FastJSON 的 `TypeReference` 实现

**flow_edit（编辑流程）**：继承 flow_create 全部字段，额外增加 flowId(Long,必填)

**work_order_delete / work_order_cancel**：id(Long) 与 code(String) 二选一

**flow_delete**：flowId(Long,必填)

#### 4.5.3 data-schemas.json 扩展结构

在现有 data-schemas.json 中为每个写 dataCode 新增条目，结构与读 Schema 一致（name/description/inputSchema/outputSchema），并在顶层增加 `type: "write"` 字段标识写操作，供 dry-run 引擎识别副作用类型。

---

## 五、测试方案

### 5.1 cli-service 测试

#### 5.1.1 基础功能测试

| 测试场景 | 测试方法 | 预期结果 |
|---------|---------|---------|
| 写接口路由 | POST /api/execute 传入各写 dataCode | 正确路由到对应 Feign 写方法 |
| 参数转换 | Map params 转 WorkOrderCreateParam 等强类型 | 字段正确映射，类型正确 |
| 响应归一化 | 后端返回 Result/VO/boolean | 统一为 code/message/data/traceId |
| 权限校验 | admin 权限 dataCode 用普通用户调用 | 返回 403 |
| dataCode 列表 | GET /api/dataCodes | 返回读写全部 dataCode，含 type 字段 |
| Schema 查询 | GET /api/schema/{写dataCode} | 返回写接口 inputSchema/outputSchema |
| 错误处理 | 后端写接口抛异常 | 返回归一化错误响应 |

#### 5.1.2 写操作接口测试（与 CLI/Agent 测试场景一致）

| 测试场景 | 测试方法 | 预期结果 |
|---------|---------|---------|
| 工单创建 | POST /api/execute `{"dataCode":"work_order_create","params":{"type":0,"title":"test","content":"test","priorityLevel":0,"flowId":1}}` | 返回创建结果，code=0，data 含工单 id 和编号 |
| 工单处理（完成） | POST /api/execute `{"dataCode":"work_order_handle","params":{"id":1,"handleType":4,"remark":"已处理"}}` | 返回处理结果，code=0，data 含工单 id 和 code |
| 工单处理（分配） | POST /api/execute `{"dataCode":"work_order_handle","params":{"id":1,"handleType":1,"assignedUserId":2,"remark":"分配给张三"}}` | 返回分配结果，code=0 |
| 工单审批（通过） | POST /api/execute `{"dataCode":"work_order_approval","params":{"id":1,"isApproved":true,"remark":"审批通过"}}` | 返回审批结果，code=0 |
| 工单审批（拒绝） | POST /api/execute `{"dataCode":"work_order_approval","params":{"id":1,"isApproved":false,"remark":"审批拒绝"}}` | 返回审批结果，code=0 |
| 工单取消 | POST /api/execute `{"dataCode":"work_order_cancel","params":{"id":1}}` | 返回取消结果，code=0 |
| 工单删除 | POST /api/execute `{"dataCode":"work_order_delete","params":{"id":1}}` | 返回删除结果，code=0 |
| 流程创建 | POST /api/execute `{"dataCode":"flow_create","params":{"flowName":"test","nodes":[{"handlerId":1,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"李四"},"checkNode":{"handlerId":3,"handlerName":"王五"}}}` | 返回流程 id，code=0 |
| 流程编辑 | POST /api/execute `{"dataCode":"flow_edit","params":{"flowId":1,"flowName":"updated"}}` | 返回编辑结果，code=0 |
| 流程删除 | POST /api/execute `{"dataCode":"flow_delete","params":{"flowId":1}}` | 返回删除结果，code=0 |
| 嵌套参数反序列化 | flow_create 传入完整 FlowNode 列表 | 参数正确反序列化为强类型对象，调用成功 |
| 工单编号查询参数 | work_order_handle 使用 code 替代 id | 正确查询并处理工单 |

### 5.2 workorder-cli 测试

| 测试场景 | 测试命令 | 预期结果 |
|---------|---------|---------|
| 工单创建 | `workorder-cli work_order create --type 0 --title xxx --content xxx --priority-level 0 --flow-id 1` | 返回创建结果 |
| 工单处理 | `workorder-cli work_order handle --id 1 --handle-type 4` | 返回处理结果 |
| 工单审批 | `workorder-cli work_order approval --id 1 --is-approved true` | 返回审批结果 |
| 流程创建 | `workorder-cli flow create --flow-name xxx --body '{...}'` | 返回流程id |
| 流程编辑 | `workorder-cli flow edit --flow-id 1 --flow-name xxx --body '{...}'` | 返回编辑结果 |
| 删除/取消 | `workorder-cli work_order delete --id 1` / `cancel --id 1` | 返回操作结果 |
| JSON复杂参数 | `--body` 传入 FlowNode 列表 | 参数正确解析 |
| 退出码 | 写命令成功/失败 | 0/非0 退出码正确 |

### 5.3 dry-run 机制测试

| 测试场景 | 测试方法 | 预期结果 |
|---------|---------|---------|
| dry-run 不发请求 | 写命令带 `--dry-run`，监控网络 | 无 HTTP 请求发出 |
| 执行计划完整性 | dry-run 输出检查 | 含操作类型、端点、全部参数、预期影响 |
| 参数可读化 | 枚举/时间戳参数 dry-run | 翻译为中文描述和可读时间 |
| dry-run 退出码 | dry-run 执行 | 退出码 0 |
| 去掉 dry-run 执行 | 同一命令去掉 `--dry-run` | 真实调用后端，数据变更 |
| 读命令 dry-run | 读命令带 `--dry-run` | 正常执行查询（无影响） |
| 各写命令 dry-run | 8 个写命令分别 dry-run | 均输出正确执行计划 |

### 5.4 端到端集成测试

| 测试场景 | 测试步骤 | 预期结果 |
|---------|---------|---------|
| 创建工单全流程 | 启动 backend+cli-service → Agent 查 dataCode → 查 Schema → dry-run → 确认 → 执行 | 工单创建成功，返回编号 |
| 处理工单全流程 | 查工单 → dry-run 处理 → 确认 → 执行 | 工单状态正确流转 |
| 审批工单全流程 | 查待审批工单 → dry-run 审批 → 确认 → 执行 | 审批结果生效 |
| 流程编辑全流程 | 查流程 → dry-run 编辑 → 确认 → 执行 | 流程更新成功 |
| 未确认直接执行 | Agent 跳过 dry-run 直接执行写操作 | SKILL.md 约束下 Agent 应先 dry-run |
| Token 失效 | 写操作时 Token 过期 | 返回 401，提示重新登录 |
| 超时处理 | 写操作后端响应慢 | 超时返回错误，不产生部分写入 |

### 5.5 测试脚本方案

参考现有测试脚本 [test_cli_tools.py](file:///d:/JavaCode/-WorkOrder/AiAssistant/test_cli_tools.py)，新增写服务测试脚本 `test_cli_write_service.py`，采用 Python 编写，通过 requests 库调用 AiAssistant 聊天接口，模拟 Agent 写操作流程。

#### 5.5.1 测试流程：先 cli-service 再 CLI 再 Agent
需要拆开三个测试脚本，分开实现。
**步骤一：cli-service 层独立验证**

在 CLI 和 Agent 测试前，先通过 curl 直接调用 `/api/execute` 验证写接口包装层能力，确保底层接口正确：

| 验证项 | 命令示例 | 验证点 |
|--------|---------|--------|
| 工单创建 | `curl -X POST http://localhost:5000/api/execute -H "Authorization: xxx" -H "X-Trace-ID: xxx" -H "Content-Type: application/json" -d '{"dataCode":"work_order_create","params":{"type":0,"title":"test","content":"test","priorityLevel":0,"flowId":1}}'` | 返回成功，code=0，data 含工单 id 和编号 |
| 工单处理 | `curl -X POST http://localhost:5000/api/execute -H "Authorization: xxx" -H "X-Trace-ID: xxx" -H "Content-Type: application/json" -d '{"dataCode":"work_order_handle","params":{"id":1,"handleType":4}}'` | 返回处理结果，code=0 |
| 工单审批 | `curl -X POST http://localhost:5000/api/execute -H "Authorization: xxx" -H "X-Trace-ID: xxx" -H "Content-Type: application/json" -d '{"dataCode":"work_order_approval","params":{"id":1,"isApproved":true}}'` | 返回审批结果，code=0 |
| 流程创建 | `curl -X POST http://localhost:5000/api/execute -H "Authorization: xxx" -H "X-Trace-ID: xxx" -H "Content-Type: application/json" -d '{"dataCode":"flow_create","params":{"flowName":"test","nodes":[{"handlerId":1,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"李四"},"checkNode":{"handlerId":3,"handlerName":"王五"}}}'` | 返回流程 id，code=0 |
| 数据变更验证 | 执行写操作后查询数据库 | 数据正确变更 |

**步骤二：CLI 层独立验证**

通过命令行直接验证写命令和 dry-run 机制：

| 验证项 | 命令示例 | 验证点 |
|--------|---------|--------|
| 写命令正常执行 | `workorder-cli work_order create --type 0 --title test --content xxx --priority-level 0 --flow-id 1` | 返回成功，数据库新增记录 |
| dry-run 不写数据 | `workorder-cli work_order create --type 0 --title test --content xxx --priority-level 0 --flow-id 1 --dry-run` | 输出执行计划，数据库无新增 |
| dry-run 输出格式 | 检查 dry-run 输出 | 含操作类型、端点、参数、预期影响 |
| 删除/取消/审批 | 各写命令分别执行 | 返回成功，数据状态正确变更 |
| 流程写命令 | `workorder-cli flow create --flow-name test --body {...}` | 返回流程id，流程表新增记录 |

**步骤三：Agent 层场景验证**

通过 AiAssistant 聊天接口模拟用户写操作需求，验证 Agent 能正确执行「鉴权→查 dataCode→dry-run→确认→执行」的完整流程。



#### 5.5.3 测试场景清单

| 场景编号 | 场景描述 | 测试层级 | 验证点 |
|---------|---------|---------|--------|
| 6001 | 创建工单 | cli-service | POST /api/execute work_order_create，返回成功 |
| 6002 | 处理工单（完成） | cli-service | POST /api/execute work_order_handle（handleType=4），返回成功 |
| 6003 | 处理工单（分配） | cli-service | POST /api/execute work_order_handle（handleType=1），返回成功 |
| 6004 | 审批工单（通过） | cli-service | POST /api/execute work_order_approval（isApproved=true），返回成功 |
| 6005 | 审批工单（拒绝） | cli-service | POST /api/execute work_order_approval（isApproved=false），返回成功 |
| 6006 | 取消工单 | cli-service | POST /api/execute work_order_cancel，返回成功 |
| 6007 | 删除工单 | cli-service | POST /api/execute work_order_delete，返回成功 |
| 6008 | 创建流程（含嵌套参数） | cli-service | POST /api/execute flow_create（含 FlowNode 列表），返回成功 |
| 6009 | 编辑流程 | cli-service | POST /api/execute flow_edit，返回成功 |
| 6010 | 删除流程 | cli-service | POST /api/execute flow_delete，返回成功 |
| 6011 | 嵌套参数反序列化 | cli-service | flow_create 传入完整 FlowNode 列表，参数正确解析 |
| 6012 | 工单编号查询参数 | cli-service | work_order_handle 使用 code 替代 id，正确处理 |
| 5001 | dry-run 不写数据（CLI层） | CLI | 带 --dry-run 执行后数据库无变更 |
| 5002 | dry-run 输出格式（CLI层） | CLI | 输出含操作类型、端点、参数、预期影响 |
| 5003 | 去掉 --dry-run 写数据（CLI层） | CLI | 去掉后数据正确写入 |
| 5004 | 读命令 dry-run 无影响（CLI层） | CLI | 读命令带 --dry-run 正常执行查询 |
| 4001 | 创建工单（高优先级） | Agent | dry-run 展示计划 → 确认 → 创建成功 |
| 4002 | 处理工单（完成操作） | Agent | dry-run 展示计划 → 确认 → 状态变更 |
| 4003 | 审批工单（通过） | Agent | dry-run 展示计划 → 确认 → 审批通过 |
| 4004 | 审批工单（拒绝） | Agent | dry-run 展示计划 → 确认 → 审批拒绝 |
| 4005 | 取消工单 | Agent | dry-run 展示计划 → 确认 → 工单取消 |
| 4006 | 删除工单 | Agent | dry-run 展示计划 → 确认 → 工单删除 |
| 4007 | 创建流程 | Agent | dry-run 展示计划 → 确认 → 流程创建 |
| 4008 | 编辑流程 | Agent | dry-run 展示计划 → 确认 → 流程更新 |
| 4009 | 删除流程 | Agent | dry-run 展示计划 → 确认 → 流程删除 |
| 4010 | 分配工单给指定人 | Agent | dry-run 展示计划 → 确认 → 分配成功 |

#### 5.5.4 测试执行顺序

```
1. 启动依赖服务：backend（8080）、cli-service（5000）、AiAssistant（8081）
2. 运行测试脚本：python cli_write_service_test.py/test_cli_write_service.py/test_agent_write_service.py
3. 脚本执行顺序：
   a. cli-service层验证（6001-6012）→ 确保写接口包装层正确，与5.1.2接口测试一一对应
   b. CLI层验证（5001-5004）→ 确保写命令和dry-run机制正确
   c. Agent层验证（4001-4010）→ 确保Agent能正确执行完整写流程
4. 验证方式：响应输出检查 + 数据库状态检查
```

#### 5.5.5 验证标准

| 验证维度 | 标准 |
|---------|------|
| dry-run 安全性 | 带 `--dry-run` 的写命令不产生任何数据变更 |
| 执行计划完整性 | dry-run 输出包含操作类型、目标端点、全部参数、预期影响 |
| 参数可读化 | 枚举值翻译为中文（如 priorityLevel 0 → 高优先级） |
| Agent 流程正确性 | Agent 严格按照「鉴权→查dataCode→dry-run→确认→执行」流程执行 |
| 数据一致性 | 执行写操作后，数据库状态与预期一致 |
| 错误处理 | Token失效返回401，参数错误返回400，权限不足返回403 |

---

## 六、实施计划

### 6.1 阶段划分

| 阶段 | 内容 | 依赖 |
|------|------|------|
| Phase 1：cli-service 写接口包装 | WriteDataCodeEnum、WriteService、/api/execute、Feign 写方法、Schema 扩展 | 无 |
| Phase 2：workorder-cli 写命令 | work_order/flow 写子命令、参数解析、/api/execute 调用 | Phase 1 |
| Phase 3：dry-run 预演引擎 | internal/dryrun 包、执行计划构建、参数可读化、输出格式 | Phase 2 |
| Phase 4：SKILL.md 写流程 | 写数据流程章节、写 dataCode 列表、dry-run 规则、示例 | Phase 3 |
| Phase 5：AiAssistant 集成 | 工具描述补充、SKILL.md 资源更新 | Phase 4 |
| Phase 6：测试与验证 | 单元测试、集成测试、端到端测试 | Phase 5 |

### 6.2 里程碑

| 里程碑 | 阶段 | 目标 |
|--------|------|------|
| M1 | Phase 1 结束 | cli-service 写接口可用，curl 直接调 /api/execute 能完成写操作 |
| M2 | Phase 2 结束 | workorder-cli 写命令可用，能通过 CLI 执行写操作 |
| M3 | Phase 3 结束 | dry-run 机制可用，写命令带 `--dry-run` 能输出执行计划且不发请求 |
| M4 | Phase 4 结束 | SKILL.md 写流程完整，Agent 可学习写数据流程 |
| M5 | Phase 6 结束 | 端到端验证通过，Agent 能完成 dry-run→确认→执行的完整写流程 |

### 6.3 依赖关系

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
(写接口)  (写命令)  (dry-run)  (SKILL)   (集成)    (测试)
```

Phase 1-2 为基础能力，Phase 3 为核心安全机制，Phase 4-5 为 Agent 集成，Phase 6 为质量保障。dry-run 机制（Phase 3）是本期重点，建议在 Phase 2 写命令实现时同步预留 dry-run 分支入口。

---

## 七、安全考虑

| 维度 | 说明 |
|------|------|
| dry-run 硬保证 | CLI 层代码级保证 dry-run 不发请求，不依赖 Agent 自觉 |
| cli-service 审计日志 | /api/execute 端点记录所有写操作的审计日志（操作类型、dataCode、参数摘要、用户信息、TraceID、执行时间），作为 dry-run 绕过时的第二道追踪防线 |
| 写操作不自动重试 | 写操作失败默认不重试，避免重复写入（与读操作的指数退避重试策略不同） |
| 权限校验 | 写 dataCode 按权限字段校验角色，admin 级写操作限制管理员 |
| Token 安全 | Token 通过环境变量传递，日志脱敏，与读服务一致 |
| 参数校验 | CLI 层和 cli-service 层双重校验必填参数，避免无效写请求到达 backend |
| 审计追踪 | 写操作携带 TraceID，backend 日志记录完整写操作链路，便于回溯 |
| 确认机制 | SKILL.md 约束 Agent 展示执行计划后必须等待用户确认，不得自动执行 |

**cli-service 写操作审计日志方案**：在 WriteService.execute() 方法中，执行写操作前后记录审计日志，格式如下：
```
[WRITE-AUDIT] traceId=<traceId> userId=<userId> dataCode=<dataCode> 
action=<action> params=<params摘要> status=<success/fail> error=<错误信息>
```

**注意**：dry-run 仅在 workorder-cli（Go层）实现，cli-service 的 `/api/execute` 端点本身没有写操作保护。若 Agent 或人工直接调用 `/api/execute` 可绕过 dry-run，因此审计日志是重要的第二道防线。
