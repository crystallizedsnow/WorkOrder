# workorder-cli 预演能力迁移至 cli-service 技术方案

## 一、概述

### 1.1 背景

当前写命令的 `--dry-run` 预演完全运行在 Go CLI 本地：命令完成参数解析后，由 `internal/dryrun` 中的硬编码操作定义、风险等级、影响说明和枚举翻译生成文本计划，并在发起 HTTP 请求前直接返回。

该实现能够保证预演不产生远程副作用，但也形成了两套写操作定义：真实写路由维护在 `cli-service` 的 `WriteDataCodeEnum`、`WriteService` 和 Feign Client 中，预演元数据维护在 Go CLI 的 `operationDefs` 与 `paramDescriptions` 中。新增或修改写接口时，两端必须同步修改，存在能力漂移和漏配风险；同时 CLI 无法感知服务端 Schema、权限和业务约束。

### 1.2 建设目标

本期将预演计划的构建职责迁移到 `cli-service`，由服务端基于写 dataCode 与 Schema 统一生成预演结果，Go CLI 仅负责：

1. 保留现有 `--dry-run` 参数和命令形式；
2. 将已经完成本地解析与校验的 `dataCode + params` 提交给预演接口；
3. 将服务端结构化预演结果渲染为现有文本格式；
4. 在任何预演路径中都不调用真实写接口。

### 1.3 非目标

本期不修改 backend 业务接口，不通过事务回滚模拟执行，不改变写操作“预演—用户确认—正式执行”的交互流程，也不把预演升级为业务数据影响分析。迁移后的第一阶段能力与当前保持等价：展示操作、端点、参数、风险等级和预期影响。

### 1.4 兼容性目标

| 兼容项 | 要求 |
|---|---|
| 命令兼容 | 原有 `workorder-cli --dry-run <写命令> ...` 全部保持可用 |
| 参数兼容 | 参数名、默认值、必填校验和 `--body @文件` 行为不变 |
| 展示兼容 | 保留 `[dry-run]`、`执行计划预览`、`操作类型`、`参数详情`、`风险等级`、`预期影响`、`不会实际执行` 等现有关键词 |
| 退出码兼容 | 预演成功仍返回 0；参数错误仍返回 4；认证错误返回 2；其他服务错误返回 1 |
| 安全兼容 | 带 `--dry-run` 时绝不调用 `/api/execute`，绝不调用 backend 写接口 |
| Agent 兼容 | AiAssistant 的命令生成、确认机制和 `CliExecutorTools` 无需修改 |
| 读链路兼容 | `/api/query` 及所有读命令不受影响 |

---

## 二、现状分析

### 2.1 当前调用链

```text
用户 / Agent
  -> workorder-cli 解析写命令
  -> checkDryRun()
  -> dryrun.Engine.BuildPlan(dataCode, params)
  -> dryrun.Engine.PrintPlan(plan)
  -> 进程退出码 0

注意：整个过程不访问 cli-service。
```

相关实现：

| 文件 | 当前职责 |
|---|---|
| `workorder-cli/cmd/work_order.go` | 检测 `--dry-run` 并拦截真实执行 |
| `workorder-cli/internal/dryrun/plan.go` | 定义操作名称、风险、影响并生成/打印计划 |
| `workorder-cli/internal/dryrun/translator.go` | 枚举值翻译和参数中文说明 |
| `workorder-cli/test-dryrun-mechanism.ps1` | 验证 8 类写操作和现有输出关键词 |
| `cli-service/.../WriteDataCodeEnum.java` | 定义真实写 dataCode、端点和权限 |
| `cli-service/.../WriteService.java` | 将真实写请求路由到 Feign Client |
| `cli-service/src/main/resources/data-schemas.json` | 定义写操作输入输出 Schema |

### 2.2 核心问题

1. **元数据重复维护**：操作名称、端点、参数说明分别存在于枚举、Schema 和 Go 预演代码中。
2. **变更容易漂移**：修改写接口后，即使真实执行正常，预演仍可能展示旧字段或旧影响。
3. **扩展成本高**：每个新增写 dataCode 都必须修改 Go 的 `operationDefs`；若需要字段翻译，还要修改 `translator.go`。
4. **无法利用 Schema**：CLI 预演没有使用 `data-schemas.json` 中已经存在的字段类型、必填性和描述。
5. **无法集中治理**：风险分级和影响说明分散在客户端版本中，旧版 CLI 无法及时获得最新预演定义。

---

## 三、总体设计

### 3.1 目标架构

```text
用户 / Agent
  -> workorder-cli 解析写命令并执行现有本地参数校验
  -> 检测到 --dry-run
  -> POST cli-service /api/preview {dataCode, params}
  -> PreviewService 校验写 dataCode
  -> PreviewMetadataRegistry 合并枚举、Schema、风险及影响元数据
  -> 返回结构化 PreviewResponse
  -> workorder-cli 按原格式渲染文本
  -> 进程退出码 0

正式执行仍为：
workorder-cli -> POST /api/execute -> WriteService -> Feign -> backend
```

### 3.2 关键设计决策

#### 3.2.1 使用独立 `/api/preview`，不复用 `/api/execute`

预演接口与执行接口物理分离。`ApiController.preview()` 只依赖 `PreviewService`，`PreviewService` 不注入 `WriteService` 和任何写 Feign Client，从结构上避免 `dryRun` 布尔值判断遗漏后误入执行分支。

#### 3.2.2 服务端返回结构化数据，CLI 保留展示职责

`cli-service` 不直接返回终端文本，而是返回稳定 JSON。CLI 继续打印现有格式，避免服务端绑定终端样式，也保证当前 PowerShell 测试和 Agent 关键词识别不受影响。

#### 3.2.3 Schema 作为参数描述的单一来源

字段中文说明从 `data-schemas.json.inputSchema` 读取；操作名称、描述、HTTP 方法、端点和权限从 `WriteDataCodeEnum` 读取。预演只额外维护无法从两者推导的 `riskLevel` 与 `impact`。

#### 3.2.4 全量切换，不保留本地回退

新版本 CLI 的 `--dry-run` 全量调用 `/api/preview`。本地 `BuildPlan()`、操作元数据与枚举翻译在同一版本中删除，不提供 `local` 或 `service-fallback` 开关，避免服务端拒绝后被客户端回退绕过。cli-service 不可用或预演失败时，CLI 明确报错并终止，绝不转为真实执行。

---

## 四、接口设计

### 4.1 预演接口

```http
POST /api/preview
Authorization: Bearer <access-token>
Content-Type: application/json
X-Trace-ID: <trace-id>
```

请求体沿用现有 `QueryRequest` 结构，避免 CLI 新增另一套协议：

```json
{
  "dataCode": "work_order_create",
  "params": {
    "type": 0,
    "title": "服务器故障",
    "content": "CPU使用率持续超过90%",
    "priorityLevel": 0,
    "flowId": 1
  }
}
```

### 4.2 成功响应

外层继续使用统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "operation": "work_order_create",
    "operationCN": "创建工单",
    "httpMethod": "POST",
    "endpoint": "/workOrder/create",
    "params": {
      "type": 0,
      "title": "服务器故障",
      "content": "CPU使用率持续超过90%",
      "priorityLevel": 0,
      "flowId": 1
    },
    "readableParams": [
      {
        "key": "type",
        "value": 0,
        "displayValue": "0(需求)",
        "description": "工单类型（0需求，1故障）"
      }
    ],
    "impact": "新增一条工单记录，分配给处理人",
    "riskLevel": "medium",
    "executable": true,
    "schemaVersion": "1"
  },
  "traceId": "..."
}
```

字段约定：

| 字段 | 含义 |
|---|---|
| `operation` | 稳定写 dataCode |
| `operationCN` | 中文操作名称 |
| `httpMethod/endpoint` | `WriteDataCodeEnum` 中登记的后端目标，仅用于展示 |
| `params` | 原始参数，便于审计和兼容 |
| `readableParams` | 经过描述和枚举翻译的参数列表 |
| `impact` | 预期影响说明 |
| `riskLevel` | `low/medium/high/unknown` |
| `executable` | 元数据和 Schema 校验是否通过；本期不代表后端业务一定允许执行 |
| `schemaVersion` | 预演协议版本，便于后续兼容扩展 |

### 4.3 错误响应

| 场景 | code | 说明 |
|---|---:|---|
| dataCode 为空或参数格式错误 | 400 | CLI 映射为退出码 4 |
| Token 无效或缺失 | 401 | CLI 映射为退出码 2 |
| dataCode 不存在 | 404 | 返回 `dataCode not found` |
| dataCode 是读操作 | 400 | 返回 `preview only supports write dataCode` |
| Schema/元数据缺失 | 500 | 阻止生成不完整的服务端预演并终止命令 |

---

## 五、cli-service 详细改造

### 5.1 新增 PreviewService

建议新增：

```text
cli-service/src/main/java/com/example/workorder/cli/service/PreviewService.java
```

职责：

1. 使用 `WriteDataCodeEnum.fromDataCode()` 确认目标是已登记写操作；
2. 从 `SchemaService` 获取输入 Schema；
3. 执行无副作用的 Schema 校验；
4. 从 `PreviewMetadataRegistry` 获取风险和影响；
5. 构建 `readableParams`；
6. 返回 `PreviewPlanDTO`；
7. 记录预演审计日志，但不调用 `WriteService` 或 Feign。

伪代码：

```java
public PreviewPlanDTO preview(String dataCode,
                              Map<String, Object> params,
                              String token,
                              String traceId) {
    WriteDataCodeEnum operation = requireWriteOperation(dataCode);
    SchemaDTO schema = schemaService.getSchema(dataCode);
    schemaValidator.validate(schema.getInputSchema(), params);

    PreviewMetadata metadata = metadataRegistry.require(dataCode);
    List<ParamDisplayDTO> readableParams =
            translator.translate(params, schema.getInputSchema());

    return PreviewPlanDTO.builder()
            .operation(dataCode)
            .operationCN(operation.getName())
            .httpMethod(operation.getHttpMethod())
            .endpoint(operation.getEndpoint())
            .params(params)
            .readableParams(readableParams)
            .impact(metadata.getImpact())
            .riskLevel(metadata.getRiskLevel())
            .executable(true)
            .schemaVersion("1")
            .build();
}
```

### 5.2 新增预演 DTO

建议新增：

```text
dto/response/PreviewPlanDTO.java
dto/response/ParamDisplayDTO.java
```

DTO 字段必须与 Go 当前 `dryrun.Plan`、`ParamDisplay` 的 JSON 标签保持一致，从而让 Go 可以直接反序列化到现有结构，减少展示逻辑变化。

### 5.3 新增 PreviewMetadataRegistry

风险和影响无法完全从 Schema 推导，服务端增加唯一注册表：

```java
private static final Map<String, PreviewMetadata> METADATA = Map.of(
    "work_order_create", metadata("medium", "新增一条工单记录，分配给处理人"),
    "work_order_handle", metadata("medium", "变更工单状态，可能通知相关人员"),
    "work_order_delete", metadata("high", "物理删除工单记录，不可恢复"),
    "work_order_cancel", metadata("medium", "将工单状态变更为已取消"),
    "work_order_approval", metadata("medium", "审批通过/拒绝，变更工单生命周期"),
    "flow_create", metadata("high", "新增流程定义，影响后续工单的处理流程"),
    "flow_edit", metadata("high", "修改流程定义，可能影响使用该流程的工单"),
    "flow_delete", metadata("high", "删除流程定义，不可恢复")
);
```

启动阶段执行完整性校验：`WriteDataCodeEnum.values()` 中的每个写操作都必须同时存在 Schema 和 PreviewMetadata；缺失时应用启动失败或健康检查不通过，避免接口上线后才发现预演缺失。

长期可将 `riskLevel`、`impact` 合并到 dataCode 元数据或 Schema 文件中，但本期采用独立注册表以控制改动范围。

### 5.4 参数翻译

字段描述直接读取 `inputSchema[field].description`。枚举显示保持当前兼容规则：

| 字段 | 翻译 |
|---|---|
| `priorityLevel` | `0(高)`、`1(中)`、`2(低)` |
| `type` | `0(需求)`、`1(故障)` |
| `handleType` | `1(分配)` 至 `6(仍有问题)` |
| `isApproved` | `true(通过)`、`false(拒绝)` |
| `assignedUserId` | `<值>(用户ID)` |
| `flowId` | `<值>(流程ID)` |
| `id` | `<值>(工单ID)` |

第一阶段应逐项复制现有 Go 翻译结果并建立契约测试，不能在迁移同时调整中文文案。

### 5.5 Controller

在 `ApiController` 新增：

```java
@PostMapping("/preview")
public ApiResponse<PreviewPlanDTO> preview(
        @Valid @RequestBody QueryRequest request,
        @RequestHeader("Authorization") String token) {
    String traceId = MDC.get("traceId");
    PreviewPlanDTO plan = previewService.preview(
            request.getDataCode(), request.getParams(), token, traceId);
    return ApiResponse.success(plan, traceId);
}
```

预演日志中 Token 必须继续以 `***` 脱敏。建议单独记录 `PREVIEW-AUDIT`，明确区别于 `WRITE-AUDIT`；预演不能产生 `pending/success` 写审计记录。

### 5.6 安全依赖约束

使用 ArchUnit 或 Spring Context 测试确保：

- `PreviewService` 不依赖 `WriteService`；
- `PreviewService` 不依赖 `*FeignClient`；
- `/api/preview` 代码路径不能调用 `/api/execute`；
- 预演不发布领域事件、不发送通知、不写数据库。

本期预演只读取本地枚举与 Schema，因此正常情况下不会访问 backend。Token 仍必须携带并经过现有 cli-service 认证拦截器，保持与真实写命令一致的用户边界。

---

## 六、workorder-cli 详细改造

### 6.1 API Client 新增 Preview

在 `internal/api/client.go` 增加：

```go
func (c *Client) Preview(
    ctx context.Context,
    dataCode string,
    params map[string]interface{},
    headers map[string]string,
) (*ApiResponse, error) {
    // POST {cliServiceURL}/api/preview
}
```

请求体继续使用 `QueryRequest`。不要将 `dryRun` 参数放入 `params`，避免它被误传给业务接口。

### 6.2 handleDryRun 改造

保留各写命令现有调用位置，避免改变参数解析和校验顺序：

```go
if checkDryRun() {
    handleDryRun("work_order_create", params)
    return
}
```

只替换 `handleDryRun` 内部实现：

```go
func handleDryRun(dataCode string, params map[string]interface{}) {
    client := api.NewClient()
	resp, err := client.Preview(
		context.Background(), dataCode, params, api.GetAuthHeaders())
	if err != nil {
		// 明确报错并终止，禁止转为真实执行
	}

    plan, err := dryrun.DecodePlan(resp.Data)
    if err != nil {
        // 协议错误，不允许误转真实执行
    }
    dryrun.NewEngine().PrintPlan(plan)
}
```

### 6.3 保留原有文本渲染器

迁移后保留 `Engine.PrintPlan()` 和 `riskLabel()`，使终端输出与测试断言保持一致；同时删除 `BuildPlan()`、本地 `operationDefs`、`paramDescriptions` 和枚举翻译，防止形成两套预演定义。

服务端响应字段顺序不应影响终端参数顺序。当前 Go 使用 `map` 构建 `readableParams`，顺序本就不稳定；本期不扩大范围。如果后续要求稳定输出，应由 Schema 字段顺序或显式 `order` 决定。

### 6.4 错误处理原则

无论发生何种错误，`--dry-run` 分支只能：

1. 输出服务端预演；
2. 返回错误并退出。

禁止在预演失败后自动调用 `client.Execute()`。这是必须通过单元测试锁定的安全不变量。

---

## 七、全量发布方案

本次按一个版本直接完成服务端能力和 CLI 切换：

1. cli-service 上线 `/api/preview`、PreviewService、DTO、元数据与完整性测试；
2. Go CLI 的 `--dry-run` 改为只调用 `/api/preview`；
3. 删除 Go 本地操作定义、参数说明和枚举翻译，仅保留计划 DTO、解码及文本渲染；
4. 保留现有 PowerShell 测试的命令和断言关键词；
5. 发布前完成 8 个写 dataCode 的接口、CLI 和 Agent 回归；
6. 任一步预演失败均阻断写操作，回滚方式为整体回滚 CLI 与 cli-service 版本，而不是运行时回退到本地预演。

---

## 八、测试方案

### 8.1 cli-service 单元测试

| 用例 | 预期 |
|---|---|
| 8 个写 dataCode 均生成计划 | operation、endpoint、risk、impact 完整 |
| 未知 dataCode | 404，不调用任何 Feign |
| 读 dataCode 请求预演 | 400 |
| 必填参数缺失 | 400，返回明确字段信息 |
| 数字、布尔、字符串翻译 | 与当前 Go 输出完全一致 |
| Flow 嵌套 body | 原始结构完整保留，不丢字段 |
| Token 不存在或无效 | 401 |
| 所有写枚举均有 Schema 和元数据 | 启动完整性测试通过 |
| PreviewService 依赖约束 | 不依赖 WriteService 和 Feign |

### 8.2 Go 单元测试

| 用例 | 预期 |
|---|---|
| Preview 请求 URL | 只访问 `/api/preview` |
| Header 透传 | Authorization 正确，Token 不进入 body |
| 服务端计划解码 | 正确转换为现有 `Plan` |
| 文本渲染 | 保留当前所有关键词和中文翻译 |
| 预演成功 | 退出码 0 |
| 服务端 401 | 退出码 2，不本地回退 |
| 服务端 400 | 退出码 4，不本地回退 |
| 服务网络失败 | 返回错误，绝不执行写请求 |
| 预演响应非法 | 返回错误，绝不调用 `/api/execute` |

### 8.3 契约测试

为现有 11 个 `test-dryrun-mechanism.ps1` 场景建立服务端预演结果，忽略 `traceId` 和参数顺序后验证：

- `work_order_create`
- `work_order_handle`
- `work_order_approval`
- `work_order_delete`
- `work_order_cancel`
- `flow_create`
- `flow_edit`
- `flow_delete`
- priorityLevel 翻译
- type 翻译
- handleType 翻译

现有脚本必须原样运行通过，用它验证对用户可见行为没有退化。

### 8.4 安全测试

使用 MockWebServer/WireMock 记录所有下游请求：

1. 调用 `/api/preview` 后，断言 backend 收到 0 个请求；
2. CLI 带 `--dry-run` 时，断言 `/api/execute` 收到 0 个请求；
3. 让 `/api/preview` 返回 500、超时、非法 JSON，断言仍无 `/api/execute` 请求；
4. 对所有写 dataCode 参数化执行上述断言；
5. 检查 `WRITE-AUDIT` 中没有预演记录，`PREVIEW-AUDIT` 中存在脱敏记录。

### 8.5 端到端测试

```powershell
workorder-cli --dry-run work_order_create `
  --type 0 `
  --title "Test Ticket" `
  --content "Test content" `
  --priority-level 0 `
  --flow-id 1
```

验证：

- CLI 文本与迁移前兼容；
- cli-service 收到 `/api/preview`；
- backend 和数据库均无变化；
- 用户确认后去掉 `--dry-run`，才调用 `/api/execute`；
- 正式执行结果与迁移前一致。

---

## 九、监控与审计

### 9.1 指标

建议增加：

```text
cli_preview_requests_total{dataCode,result}
cli_preview_duration_seconds{dataCode}
cli_preview_validation_failure_total{dataCode,field}
```

### 9.2 日志

预演日志至少包含：

- traceId；
- userId（从 Token 解析，禁止记录 Token）；
- dataCode；
- 参数摘要；
- riskLevel；
- 结果与失败原因。

复杂参数、附件 URL 和备注可能包含敏感信息，日志应复用现有脱敏策略，不输出完整正文。

### 9.3 告警

- `/api/preview` 5xx 比例异常；
- 某写 dataCode 连续预演失败；
- 发现带 dry-run 的 traceId 出现在 `WRITE-AUDIT`；
- PreviewMetadata 与 WriteDataCodeEnum 完整性检查失败。

---

## 十、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| cli-service 不可用导致预演不可用 | Agent 无法完成写操作确认流程 | 明确失败并阻断写操作；恢复服务或整体回滚版本 |
| 服务端文案与旧 CLI 不一致 | 脚本或 Agent 关键词识别失败 | golden 契约测试；CLI 保留旧文本渲染器 |
| 预演误调用写链路 | 产生真实副作用 | 独立 endpoint/service；禁止依赖 Feign；下游零请求安全测试 |
| 新写接口漏配预演 | 新命令执行前无法预演 | 启动完整性测试：写枚举、Schema、Metadata 一一对应 |
| 新旧 CLI 与服务协议不兼容 | 解析失败 | `schemaVersion`；新增字段保持向后兼容；CLI 与 cli-service 作为同一发布单元验证 |
| 预演与正式执行之间数据变化 | 展示与执行结果可能不同 | 本期在输出中明确预演仅为计划；后续引入 backend 业务预演与 planToken |

---

## 十一、代码改造清单

### 11.1 cli-service

新增：

```text
controller/ApiController.java                 增加 POST /api/preview
service/PreviewService.java                   预演编排
service/PreviewSchemaValidator.java           无副作用参数校验
service/PreviewValueTranslator.java           可读值转换
service/PreviewMetadataRegistry.java          风险与影响唯一注册表
dto/response/PreviewPlanDTO.java              计划 DTO
dto/response/ParamDisplayDTO.java             参数展示 DTO
dto/response/PreviewMetadata.java             元数据 DTO
```

修改：

```text
service/SchemaService.java                    提供预演所需字段定义读取能力
config/WebConfig.java                         确认 /api/preview 走认证与 Trace 拦截器
```

### 11.2 workorder-cli

修改：

```text
internal/api/client.go                        新增 Preview()
cmd/work_order.go                             handleDryRun 改为服务端调用
internal/dryrun/plan.go                       仅保留 DTO、响应解码与文本渲染
internal/dryrun/translator.go                 删除
test-dryrun-mechanism.ps1                     保留既有用例，可增加来源诊断但不改原断言
```

### 11.3 AiAssistant

`CliExecutorTools`、Tool Schema 和命令字符串均无需改变。只需更新技术文档，明确预演现在会访问 cli-service，但仍不会调用 backend 写接口。

---

## 十二、验收标准

迁移完成必须同时满足：

1. 现有 8 个写 dataCode 的预演全部由 `/api/preview` 正确生成；
2. `test-dryrun-mechanism.ps1` 原有测试全部通过；
3. 原有命令、参数、文本关键词和退出码无变化；
4. dry-run 成功、失败、超时、非法响应等所有路径均不会调用 `/api/execute` 或 backend；
5. 写枚举、Schema、预演元数据具有自动完整性校验；
6. 新增写 dataCode 时，无需修改 Go 预演元数据与翻译逻辑；
7. AiAssistant 的“预演—确认—执行”链路回归通过；
8. 预演日志不泄漏 Token 和敏感正文；
9. Go 本地业务元数据已删除，预演定义只有 cli-service 一份；
10. CLI 与 cli-service 全量切换后仍保留相同预演展示能力。

---

## 十三、后续演进

本期完成的是“预演元数据集中化”，不是最终的业务级预演。后续可由 backend 增加只读的 `prepare/preview` 能力，使预演进一步返回：

- 目标工单或流程当前状态；
- 当前用户是否具备实际操作权限；
- 预计状态变化；
- 受影响对象数量；
- 通知、消息和流程推进等副作用；
- 带有效期的 `planToken`，正式执行时校验预演后数据是否变化。

该演进仍应遵守同一安全原则：预演逻辑显式只读，不能通过“执行真实逻辑后回滚事务”实现，因为消息、缓存、搜索索引、文件和第三方调用等副作用无法由数据库事务完整回滚。
