---
name: workorder-query
description: 通过 workorder-cli 操作工单系统；所有操作先发现 dataCode、查询业务 Schema 与 CLI 实际参数契约，写操作还必须 dry-run 完整核验并等待 JSON 确认
---

# 工单系统 CLI 技能

## 核心原则

本技能适用于所有 dataCode，不保存任何具体接口的字段映射或正确命令。

运行时 `schema` 同时描述业务字段和 CLI 传输契约，dry-run 描述最终会发送的参数。二者必须按业务语义和值一致，不得从历史样例猜测参数。

## 每次操作的强制状态机

不得跳过或交换以下状态：

1. `DISCOVER`：执行 `workorder-cli list`，从实时列表选择 dataCode。
2. `SCHEMA`：只使用 `workorder-cli schema <dataCode>` 查询所选 dataCode 的最新 Schema（禁止使用 `schema --dataCode ...`）。结合字段名、类型、`required`、描述、`cliTransport` 和 `cliFlag` 理解业务输入及传输方式。
3. `EXPECT`：建立期望参数清单。记录用户明确提供的每项业务约束、会话中需要复用的上一步结果、对应 Schema 字段和值。
4. `CONTRACT`：读取所选 dataCode 的 CLI 实际参数契约。
5. `PREVIEW`：写操作使用已确认的 CLI flag 执行 dry-run。
6. `VERIFY`：将 dry-run 的“参数详情”与期望参数清单按业务语义和值逐项核对。
7. `WAIT_CONFIRM`：仅在全部期望项验证通过后，原样展示预演并等待 JSON 确认。
8. `EXECUTE`：确认后只删除已验证命令中的 `--dry-run`，其它字符不变，执行一次并停止。

读操作完成 `CONTRACT` 后直接执行，不需要 dry-run。

用户明确提供的 ID、类型、标题等值都是目标操作的输入参数。除非对应 Schema 或后端错误明确要求先查询验证，否则不得把这些参数误认成其他资源 ID、不得额外调用详情查询，也不得改变用户请求的操作类型。例如创建工单中的 `flowId` 应直接按创建 Schema 传入，不能当作工单 ID 查询。

## CONTRACT：所有接口共用的参数契约查询

每次只使用本次 `schema` 返回的传输元数据：

- `cliTransport=flag`：使用同一字段定义中的 `cliFlag`，不得自行改名或机械推导。
- `cliTransport=body`：把这些字段按 Schema 的层级组成 JSON，通过 `--body` 传输；多个 body 字段必须合并成一个 JSON。
- 缺少 `cliTransport`，或 transport 为 `flag` 但缺少 `cliFlag`：停止并报告运行时契约不完整，不得猜参数。

建立当前操作的临时映射“业务语义 → Schema 字段 → CLI 传输方式”，不得从历史操作复用，也不得读取项目源码来补足运行时契约。

## 写操作与 dry-run

创建、处理、分配、删除、取消、审批工单，以及新增、编辑、删除流程，均为写操作。

首次写命令格式：

```text
workorder-cli --dry-run <dataCode> <CONTRACT确认过的flags>
```

dry-run 返回成功、退出码为 0 或包含 `[dry-run]`，都不代表参数正确。必须只根据“参数详情”核验：

- 每个期望业务项都真实出现；
- 实际值与期望值相同；
- 实际参数的业务语义正确；
- 没有会改变本次业务含义的额外关键参数。

只有“期望项数量 = 已验证项数量”且无错误关键参数时，才能进入 `WAIT_CONFIRM`。此时原样展示 CLI 预演文本，保留 `[dry-run]`、操作类型、目标端点和参数详情，并请求 JSON 确认。

## 参数丢失时的强制恢复

如果传入的业务值没有出现在 dry-run“参数详情”中，说明该 flag 被 CLI 忽略或映射错误：

1. 立即把这个 flag 加入当前操作的“已拒绝 flag 集合”。
2. 禁止请求用户确认，禁止执行真实写命令。
3. 重新执行 `DISCOVER → SCHEMA → CONTRACT`，只接受刷新后的运行时契约。
4. 契约发生变化后重新 dry-run，再完整执行 `VERIFY`。

后续命令绝对不能再次使用“已拒绝 flag”。禁止原样重试；禁止只改变引号；禁止通过追加备注、详情、说明等无关字段来补偿缺失的业务参数。

若没有找到有代码依据的新 flag，停止并明确报告 Schema 与 CLI 契约不一致，不得输出不完整的预演确认提示。

## JSON 确认协议

确认：

```json
{"action":"confirm_execute","target":"last_dry_run","confirmed":true}
```

仅当最近一次 dry-run 已通过完整 `VERIFY` 时有效。从会话历史找到该次 tool call 的完整命令，只删除 `--dry-run`，参数名、顺序和值全部保持不变，执行一次。执行后立即停止工具调用，只原样展示 CLI 返回 JSON。

如果最近一次 dry-run 缺少任一期望项，即使用户发送确认 JSON，也必须拒绝执行并返回 `CONTRACT` 恢复，不得把“不完整预演”变成真实写入。

取消：

```json
{"action":"cancel_execute","target":"last_dry_run","confirmed":false}
```

收到取消后不调用任何工具，直接回复：“已取消本次预演，不会执行真实写操作。”不要把它解释为取消一个已存在工单。

用户修改或补充参数时，更新期望参数清单，重新执行 `CONTRACT → PREVIEW → VERIFY`。

## 执行结果

真实写命令完成后立即停止，不查询详情或触发后续业务。最终回答原样保留 CLI JSON 的 `code`、`message`、`data`、`isSuccess`、`traceId` 等字段。

## 认证恢复

CLI 返回 401 时：

1. 立即停止当前操作，不得调用 `login`、`auth login`、刷新接口或读取任何本地账号/Token 文件。
2. 不得向用户索要、记录或传递手机号、密码、Refresh Token 或完整 Access Token。
3. 明确告知用户当前会话凭证无效或已过期，请调用方重新登录并用新的 Bearer Access Token 重新请求 `/assistant/chat`。
4. 只有新的 HTTP/Channel 请求携带有效凭证后才能重试；Agent 不得在同一轮自行替换身份。

AiAssistant 执行 CLI 时会把当前请求的 Access Token 隔离注入子进程。CLI 命令中禁止出现 `login`、`auth login`、`--password` 或 `--token`。

## 错误处理

- 参数错误：回到 `SCHEMA → EXPECT → CONTRACT`，不得原样重试。
- 401：按认证恢复流程停止本轮；等待调用方用新凭证重新发起请求。
- 403：停止并报告权限不足。
- 404：重新执行 `DISCOVER`，不得猜 dataCode。
- 网络超时：最多重试 3 次，间隔 1、2、4 秒。
- 服务端错误：最多重试 2 次；业务校验错误不属于可直接重试的服务端错误。

`WORKORDER_TRACE_ID` 由执行器注入。命令必须直接以 `workorder-cli` 开头，不要拼接环境变量、`&&`、`;` 或管道。
