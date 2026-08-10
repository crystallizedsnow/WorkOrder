---
name: 工单系统CLI技能
description: 通过workorder-cli命令行工具操作工单系统；读操作直接查询，写操作必须先dry-run预演并等待用户确认，CLI参数必须使用kebab-case
type: query,write
version: 2.0.0
---

# 工单系统CLI技能

## 概述

本技能用于通过workorder-cli命令行工具操作工单系统，包含查询和写入两大能力。Agent需要按照以下流程执行CLI命令：
1. 生成18位随机数字作为traceId
2. 获取所有可查询的数据类型（dataCode列表）
3. 获取指定dataCode的参数Schema
4. （**预演步骤，写操作强制，读操作不需要**）根据用户输入的参数，拼接完整命令和--dry-run，预执行命令，返回预执行结果，用户确认后执行命令，用户指出错误，根据用户意见修改参数。**写操作必须先预演，绝对不能跳过预演。**
5. 根据Schema拼接完整命令并执行

## 写操作绝对规则（最高优先级）

以下规则优先于其它示例和推理：
- 用户请求创建、处理、删除、取消、审批工单，或新增、编辑、删除流程时，均属于写操作。
- 写操作的第一次 `executeCliCommand` 必须带 `--dry-run`，例如 `workorder-cli --dry-run work_order_create ...`。
- dry-run 成功后必须把 CLI 返回的预演内容**原文展示**给用户，保留 `[dry-run]`、`操作类型`、`目标端点`、`参数详情` 等文本，不要改写成摘要；随后请求用户确认。用户确认前禁止执行真实写命令。
- `checkLoginStatus`、认证状态检查、`list` 或 `schema` 都只是前置/中间步骤，不是写操作完成态。除非用户只是在询问登录状态，否则 `checkLoginStatus` 返回 `Token有效` 后必须继续执行用户原始请求对应的 `list/schema/dry-run` 流程，不能停下来回答。
- 仅完成 `list` 或 `schema` 查询不是写操作的完成态，不能回答“已预演”“已创建”“已处理”。拿到 schema 后必须继续拼接并执行带 `--dry-run` 的预演命令，直到工具结果中真实包含 `[dry-run]`。
- dry-run 工具结果真实包含 `[dry-run]` 后，当前轮次必须停止工具调用，只把预演原文返回给用户并等待确认、取消或修改；不要在同一轮继续执行去掉 `--dry-run` 的真实写命令。
- dry-run 后的用户确认/取消必须使用 JSON 协议，Agent 只能根据 JSON 的 `action` 字段进入确认或取消分支，不能把自然语言"确认执行"、"确认删除"等自由文本当作执行授权。
- 用户初始创建请求中的标题或详情字段可能包含"取消"、"不会实际创建"、"不要创建"等字样，这些只是字段值文本，不能当作取消执行意图。只有用户消息整体是 `cancel_execute` JSON 协议时才表示取消本次 dry-run。
- 当当前用户输入 JSON 且 `action` 为 `confirm_execute`、`confirmed` 为 `true`、`target` 为 `last_dry_run` 时，表示确认执行上一次 dry-run 预演；必须从当前会话历史中定位**最近一次 assistant tool_call 参数里带 `workorder-cli --dry-run` 的命令字符串**，复制该命令，去掉 `--dry-run`，其它参数名、顺序、值保持不变，再执行真实写命令。禁止根据历史中的工单ID、知识库状态说明或"确认"字面含义改成详情查询、处理工单、确认工单等其它命令。
- `confirm_execute` 分支是终止分支：真实写命令执行完成后，必须立即停止工具调用，绝对不要继续查询详情、处理工单、确认工单、取消工单或发起任何新的 dry-run。
- 当当前用户输入 JSON 且 `action` 为 `cancel_execute`、`confirmed` 为 `false`、`target` 为 `last_dry_run` 时，表示取消上一次 dry-run 预演；这是终止分支，必须停止，不要执行任何 CLI 命令，不要补做 dry-run，不要执行真实写命令，也不要调用 `work_order_cancel`。
- `cancel_execute` 只表示"取消本次预演后的执行"，不是"取消已存在工单"，不要引用工单状态流程、不要说明"已取消(700)"、不要建议使用取消工单功能，直接回复"已取消本次预演，不会执行真实写操作"。
- 真实写命令执行完成后，必须把 CLI 返回的 JSON 执行结果**原样展示**给用户，至少保留 `"code"`、`"message"`、`"data"`、`"isSuccess"` 等字段；最终回答只能包含该 JSON，最多在 JSON 前加一句"执行成功"。不要查询详情，不要追加工单流程/确认流程/知识库说明，不要改写成纯自然语言摘要。
- 读操作（列表、详情、搜索、看板、流程查询等）不需要 dry-run。
- 从 Schema 获取到的驼峰字段名只能用于理解含义，不能直接作为 CLI 参数名；执行 CLI 时必须转为 kebab-case。

## 重要：Schema字段名与CLI参数名映射规则（必须遵守）

`workorder-cli schema $dataCode` 返回的 `inputSchema` 字段名是**驼峰命名**（如 `priorityLevel`、`flowId`、`pageNum`、`createTimeTo`），
但实际 CLI 命令的参数名必须使用**连字符分隔（kebab-case）**（如 `--priority-level`、`--flow-id`、`--page-num`、`--create-time-to`）。
**Agent必须做转换，绝对不能直接用驼峰名作为参数名。**

转换规则：
- Schema 字段名中每个大写字母 → CLI 参数名中改为 `-` + 该字母的小写
- 示例：`priorityLevel` → `--priority-level`、`flowId` → `--flow-id`、`pageNum` → `--page-num`、`pageSize` → `--page-size`

反例（错误写法，会报错"参数不能为空"）：`--priorityLevel`、`--flowId`、`--pageNum`、`--pageSize`
正例（正确写法）：`--priority-level`、`--flow-id`、`--page-num`、`--page-size`

## dry-run确认JSON协议（必须遵守）

写操作 dry-run 后，下一轮用户输入可能是 JSON 字符串。Agent 必须先尝试把用户消息按 JSON 理解：

### 确认执行

```json
{"action":"confirm_execute","target":"last_dry_run","confirmed":true}
```

含义：用户确认执行最近一次 dry-run 预演的同一条写命令。Agent 必须从当前会话历史中找到最近一次带 `--dry-run` 的 `workorder-cli` 命令，复制该命令并仅删除 `--dry-run`，然后调用 `executeCliCommand` 执行真实写操作。不要把 `confirm_execute` 理解为工单处理命令中的"确认"动作，不要改成 `work_order_handle`。

执行真实写命令后必须立即停止，最终回答只展示本次真实写命令的返回结果。例如创建工单成功后只返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "code": "WO...",
    "id": 48,
    "isSuccess": true
  },
  "traceId": ""
}
```

不要在创建成功后继续调用 `work_order_detail`、`work_order_handle`、`work_order_cancel` 或任何其它命令。不要在 JSON 后追加"后续确认流程"、"流程说明"、"建议查询状态"等知识库内容。

### 取消执行

```json
{"action":"cancel_execute","target":"last_dry_run","confirmed":false}
```

含义：用户取消最近一次 dry-run 预演。Agent 必须停止，不要调用真实写命令，直接回复已取消。

取消回复必须简短，例如："已取消本次预演，不会执行真实写操作。" 不要把它解释成取消一个已存在工单，不要提到工单状态 700、流程终止、取消工单功能。

### 修改参数

如果用户不是上述 JSON，而是要求修改参数、修改命令类型或补充参数，Agent 必须根据用户修改后的信息重新执行 dry-run，再次等待 JSON 确认或取消。

## TraceId生成机制

### 生成规则

每次查询前必须生成18位随机数字作为traceId，用于日志追踪和问题排查。

**生成方式**：
```
traceId = 18位随机数字（范围：0-9的随机组合，首位不能为0）
```

**生成示例**：
```python
import random
traceId = str(random.randint(100000000000000000, 999999999999999999))
```

### 传递方式

`WORKORDER_TRACE_ID` 由系统执行器注入到 workorder-cli 进程环境中，Agent **不要**把环境变量设置语句拼进 `executeCliCommand` 的命令字符串。

正确命令只包含 `workorder-cli` 本体及其参数：

```bash
workorder-cli work_order_page --page-num 1 --page-size 10
```

不要使用 shell 环境变量语法或命令连接符，例如在命令字符串中加入环境变量设置、`&&`、`;`、管道等。Windows 执行器不会解释这些 shell 语句，命令字符串必须直接以 `workorder-cli` 开头。

### 注意事项

- Agent 调用 `executeCliCommand` 时不要手动设置 traceId；执行器会为每次 CLI 进程设置环境变量
- traceId必须为18位数字，不能包含字母或特殊字符
- 响应结果中会返回相同的traceId，用于确认请求链路

## Token管理机制

### Token和账号存储位置

两个文件都在 `C:\Users\Crystal\.workorder\` 目录下（即 `~/.workorder/`）：
- `token`：保存登录Token，格式 `{"token":"...","phone":"...","expireTime":"...","createTime":"..."}`
- `account`：保存账号凭证，格式 `{"phone":"...","password":"..."}`

`token` 由登录工具或 `workorder-cli auth login` 写入。`account` 中保存的是重登用凭证，Token过期时优先从这里读取账号密码。

### 401认证失败处理流程（重要）

执行任意CLI命令时，workorder-cli会自动读取 `token` 文件。如果Token不存在或已过期，命令会返回401错误：
```json
{"code": 401, "message": "Missing Authorization header", "data": null, "traceId": ""}
```

**收到401错误时，Agent必须先恢复登录，不要直接把401返回给用户。标准流程如下：**

1. **读取账号文件**：
   ```
   readTxtFile({"filepath":"C:\\Users\\Crystal\\.workorder\\account"})
   ```
   读取结果是 JSON 字符串，解析其中的 `phone` 和 `password`。

2. **调用登录接口工具**：
   ```
   login({"phone":"<account.phone>","password":"<account.password>"})
   ```
   `login` 成功后会自动把新 Token 写入 `C:\Users\Crystal\.workorder\token`，并同步更新会话Token缓存。

3. **登录成功后，立刻重试原来失败的CLI命令**。例如 `getCliCommandSchema("work_order_create")` 返回401，登录成功后必须重新调用：
   ```
   getCliCommandSchema({"dataCode":"work_order_create"})
   ```

4. **如果账号文件不存在、读取失败、JSON缺少phone/password，或登录失败**，再向用户索要手机号和密码，然后调用：
   ```
   login({"phone":"用户提供的手机号","password":"用户提供的密码"})
   ```
   登录成功后继续重试原命令。

5. **等价备用方式**：也可以调用 `loginFromStoredAccount()` 或 `executeCliCommand("workorder-cli auth login")`，它们都会从 `~/.workorder/account` 读取凭证并写入新 Token。无论使用哪种方式，登录成功后都必须重试原命令。

### 注意事项
- 401不是最终失败；必须先尝试读取account并重新登录
- 不要在最终回答中暴露密码或完整Token
- `checkLoginStatus` 只用于快速判断状态；CLI调用返回401时以401为准，立即执行上述重登流程
- 同一会话中Token过期只需登录一次，登录成功后立即重试原命令

### 认证状态查询

```
executeCliCommand("workorder-cli auth status")
```

**响应示例**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "loggedIn": true,
    "phone": "13812345678",
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "createTime": "2026-07-21 10:00:00",
    "expireTime": "2026-07-28 10:00:00",
    "isExpired": false
  },
  "traceId": ""
}
```


## 查询数据流程

### 第一步：获取所有可查询的数据类型（dataCode列表）

**功能描述**：获取所有可查询的数据类型列表，每个数据类型包含dataCode、名称、描述和权限

**调用方式**：
```
executeCliCommand("workorder-cli list")
```

**返回示例**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "dataCode": "work_order_page",
      "name": "工单分页查询",
      "description": "分页查询工单列表",
      "permission": "all"
    }
  ],
  "traceId": ""
}
```

### 第二步：获取数据Schema

**功能描述**：获取指定dataCode的入参和出参Schema说明，用于构建完整的查询命令

**参数说明**：
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| dataCode | String | 是 | 数据类型标识 |

**调用方式**：
```
executeCliCommand("workorder-cli schema $datacode$")
```
**调用示例**：
```
executeCliCommand("workorder-cli schema work_order_page")
```

**返回示例**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dataCode": "work_order_page",
    "name": "工单分页查询",
    "inputSchema": {
      "pageNum": {"type": "Integer", "required": true, "description": "当前页数"},
      "pageSize": {"type": "Integer", "required": true, "description": "每页大小"},
      "title": {"type": "String", "required": false, "description": "工单标题"},
      "code": {"type": "String", "required": false, "description": "工单编号"},
      "type": {"type": "Integer", "required": false, "description": "工单类型（0需求，1故障）"},
      "content": {"type": "String", "required": false, "description": "工单关键词"},
      "createTimeTo": {"type": "Long", "required": false, "description": "创建时间止"}
    },
    "outputSchema": {
      "records": {"type": "List", "description": "工单列表"},
      "total": {"type": "Long", "description": "总数量"},
      "current": {"type": "Integer", "description": "当前页码"},
      "pages": {"type": "Integer", "description": "总页数"},
      "size": {"type": "Integer", "description": "每页大小"}
    }
  },
  "traceId": ""
}
```
### 第三步：dry-run 预演（写操作强制，读操作不需要）

**写操作必须先执行本步骤预演，绝对不能跳过预演直接执行写操作（第四步）。读操作跳过本步骤直接进入第四步。**

**功能描述**：根据第二步获取的 Schema 拼接命令，并加上 `--dry-run` 参数执行预演。预演结果为 string 类型的执行计划预览（不会实际写数据）。将预演结果返回给用户，明确请求用户确认；用户指出错误时根据用户意见修改参数后再次预演。

**参数拼接规则**：
- dataCode 之前加 `--dry-run` 参数
- Schema 的 inputSchema 字段名（驼峰）必须转换为 CLI 参数名（连字符分隔）
- 具体转换规则参见开头的"Schema字段名与CLI参数名映射规则"章节

**调用方法**：
```
executeCliCommand("workorder-cli --dry-run $datacode$ --$param-name1$ $value1$ --$param-name2$ $value2$")
```

**调用示例**（flow_create 写操作预演）：
```
executeCliCommand("workorder-cli --dry-run flow_create --flow-name Test Flow --body {"nodes":[{"handlerId":1,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"李四"},"checkNode":{"handlerId":3,"handlerName":"王五"}}")
```

### 第四步：执行CLI命令

**功能描述**：根据 Schema 拼接完整的 CLI 命令并执行。

**参数说明**：
- 入参：参考第二步 inputSchema，同样遵守驼峰→连字符映射规则
- 写操作：直接复制第三步预演成功且用户确认的完整命令，去掉 `--dry-run` 参数即可（参数顺序和值不要改变）
- 出参：参考第二步 outputSchema

**调用方法**：
```
executeCliCommand("workorder-cli $datacode$ --$param-name1$ $value1$ --$param-name2$ $value2$")
```

**调用示例**（读操作，参数均为连字符）：
```
executeCliCommand("workorder-cli work_order_page --page-num 1 --page-size 10")
```

## 命令示例

### 示例1：查询工单列表（读操作）

**背景**：用户问"帮我查第一页工单列表，每页10条"

**步骤**：
1. 调用 `executeCliCommand("workorder-cli list")` 获取 dataCode 列表
2. 调用 `executeCliCommand("workorder-cli schema work_order_page")` 获取 Schema。Schema 中 inputSchema 字段名是驼峰（`pageNum`、`pageSize`、`title`、`type` 等）
3. 读操作跳过 dry-run。将 Schema 驼峰字段名转换为 CLI 连字符参数名后执行：
   - `pageNum` → `--page-num`
   - `pageSize` → `--page-size`

```
executeCliCommand("workorder-cli work_order_page --page-num 1 --page-size 10")
```

注意：绝对不能写 `--pageNum`、`--pageSize`（驼峰写法会报错"参数不能为空"）。

**响应示例**：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "current": 1,
    "pages": 10,
    "size": 10
  },
  "traceId": ""
}
```

### 示例2：创建工单（写操作，先 dry-run 预演再执行）

**背景**：用户说"创建一个需求类工单，标题'测试工单-确认执行场景'，详情'这是一个确认执行的测试工单'，高优先级，流程ID 2081909482228682752"

**步骤**：
1. 调用 `executeCliCommand("workorder-cli list")` 获取 dataCode 列表
2. 调用 `executeCliCommand("workorder-cli schema work_order_create")` 获取 Schema。Schema inputSchema 字段名是驼峰：`type`、`title`、`content`、`priorityLevel`、`flowId`
3. **写操作必须先 dry-run 预演（不能跳过）**。将驼峰字段转换为连字符参数名，拼接 `--dry-run`：
   - `priorityLevel` → `--priority-level`
   - `flowId` → `--flow-id`
   ```
   executeCliCommand("workorder-cli --dry-run work_order_create --type 0 --title \"测试工单-确认执行场景\" --content \"这是一个确认执行的测试工单\" --priority-level 0 --flow-id 2081909482228682752")
   ```
   **拿到预演结果后必须原样返回给用户并请求 JSON 确认**，例如："以下是本次创建工单的预执行计划：\n<CLI dry-run原文结果>\n如需执行，请回复：{\"action\":\"confirm_execute\",\"target\":\"last_dry_run\",\"confirmed\":true}；如需取消，请回复：{\"action\":\"cancel_execute\",\"target\":\"last_dry_run\",\"confirmed\":false}"。不要只总结参数；必须保留 `[dry-run]`、`操作类型`、`参数详情` 等原文标记。如果用户指出参数错误，修改参数后回到步骤3重新预演。
4. 用户输入 `{"action":"confirm_execute","target":"last_dry_run","confirmed":true}` 确认预演结果无误后，复制步骤3的命令，去掉 `--dry-run` 参数后执行写操作：
   ```
   executeCliCommand("workorder-cli work_order_create --type 0 --title \"测试工单-确认执行场景\" --content \"这是一个确认执行的测试工单\" --priority-level 0 --flow-id 2081909482228682752")
   ```
   不要改变参数顺序和值，避免与预演不一致。

**第三步预演返回的 string 类型结果示例**：
```
==================================================
[dry-run] 执行计划预览
==================================================
  操作类型: 创建工单 (work_order_create)
  目标端点: /api/execute
  风险等级: 中 (状态变更)
  预期影响: 新增一条工单记录，分配给处理人
--------------------------------------------------
  参数详情:
    priorityLevel: 0 (0(高))
      [优先级]
    flowId: 2081909482228682752 (2081909482228682752(流程ID))
      [关联流程ID]
    type: 0 (0(需求))
      [工单类型]
    title: 测试工单-确认执行场景
      [工单标题]
    content: 这是一个确认执行的测试工单
      [工单详情]
==================================================
  注意: 此为 dry-run 预览模式，不会实际执行写操作。
  确认执行请去掉 --dry-run 参数。
==================================================
```

**第四步实际执行结果示例**（符合 Schema outputSchema）：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "code": "WO202607292082470073854988288",
    "id": 38,
    "isSuccess": true
  },
  "traceId": ""
}
```
## 工单状态说明

| 状态码 | 状态名称 | 说明 |
|--------|----------|------|
| 100 | 未审核 | 工单已提交，等待审核 |
| 200 | 审核中 | 正在审核流程中 |
| 270 | 审核失败 | 审核未通过 |
| 300 | 未派单 | 审核通过，等待分配处理人 |
| 400 | 处理中 | 正在处理中 |
| 410 | 已超时 | 处理超时 |
| 500 | 已完成 | 处理完成，等待确认 |
| 600 | 已确认完成 | 确认通过，流程结束 |
| 670 | 确认失败 | 确认未通过 |
| 700 | 已取消 | 工单被取消 |

## 工单类型说明

| 类型码 | 类型名称 |
|--------|----------|
| 0 | 需求类工单 |
| 1 | 故障类工单 |

## 优先级说明

| 优先级码 | 优先级名称 |
|----------|------------|
| 0 | 高优先级 |
| 1 | 中优先级 |
| 2 | 低优先级 |

## 错误处理

### 常见错误码

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 4 | 参数错误（提示"xxx参数不能为空"） | 将Schema驼峰字段名转换为连字符格式（如 `priorityLevel` → `--priority-level`），或补齐缺失的必填参数。参见开头"Schema字段名与CLI参数名映射规则" |
| 401 | Token无效或未提供 | 先用 `readTxtFile({"filepath":"C:\\Users\\Crystal\\.workorder\\account"})` 读取 phone/password，再调用 `login({"phone":"...","password":"..."})` 写入新Token，成功后重试原命令；也可使用 `loginFromStoredAccount()` 或 `workorder-cli auth login` 作为备用 |
| 403 | 权限不足 | 检查用户角色是否有权限访问该dataCode |
| 404 | dataCode不存在 | 调用 `workorder-cli list` 获取正确的dataCode |
| 500 | 服务器内部错误 | 查看响应中的traceId，联系管理员排查 |

### 退出码约定

| 退出码 | 含义 |
|--------|------|
| 0 | 成功 |
| 1 | 通用错误 |
| 2 | 认证错误 |
| 3 | 超时 |
| 4 | 参数错误 |

### 重试机制

当请求失败时，可根据错误类型决定是否重试：

| 错误类型 | 是否重试 | 重试次数 | 间隔 |
|----------|----------|----------|------|
| 网络超时 | 是 | 3次 | 1s, 2s, 4s（指数退避） |
| 500错误 | 是 | 2次 | 1s, 2s |
| 401错误 | 否 | - | 先读取 `~/.workorder/account` 并调用 `login` 重登，成功后重试原命令；若失败则提示用户提供手机号密码 |
| 403错误 | 否 | - | 检查权限或更换用户 |
| 404错误 | 否 | - | 调用 `workorder-cli list` 检查dataCode是否正确 |
