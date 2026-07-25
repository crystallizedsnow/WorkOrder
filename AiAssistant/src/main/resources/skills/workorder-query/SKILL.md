---
name: 工单系统CLI查询技能
description: 通过workorder-cli命令行工具查询工单系统数据，支持工单列表查询、详情查询、关键词搜索、数据看板等功能
type: query
version: 1.0.0
---

# 工单系统CLI查询技能

## 概述

本技能用于通过workorder-cli命令行工具查询工单系统的数据。Agent需要按照以下流程执行CLI命令：
1. 获取所有可查询的数据类型（dataCode列表）
2. 获取指定dataCode的参数Schema
3. 根据Schema拼接完整命令并执行

## Token管理机制

### Token存储位置

Token由workorder-cli管理，存储在用户家目录下的配置文件中：`~/.workorder/token`，参考格式：
{
  "token": $token$,
  "phone": $phone$,
  "expireTime": $expireTime$,
  "createTime": $createTime$
}

账号由workorder-cli管理，存储在用户家目录下的配置文件中：`~/.workorder/account`，参考格式：
{
  "phone": $phone$,
  "password": $password$
}

### Token获取流程

1. 执行任意CLI命令时，workorder-cli会自动从配置文件读取Token
2. 如果Token不存在或已过期，命令会返回401错误
3. 当收到401错误时，Agent必须先从账号文件中获取用户账号密码，调用登录命令获取新Token
4. 当账号不存在或登录失败，命令返回的code为0，msg为登录失败原因，立即停止，并将登录失败原因返回用户，要求用户重新输入手机号和密码。Agent收到新手机号和密码，再用新手机号和密码重试登录命令。
5. Agent登录成功，需要重写账号文件，将新手机号和密码保存到账号文件`~/.workorder/account`中。
6. Agent获取到新Token，需要重写Token配置文件`~/.workorder/token`

### 登录命令

当命令返回401错误时，Agent应立即尝试读取配置文件`~/.workorder/account`中的账号密码。调用登录命令：

```
executeCliCommand("workorder-cli auth login --phone $phone$ --password $password$")
```

**登录成功响应**：
```json
{
    "code": 1,
    "msg": "success",
    "data": $token$
}
```
**登录失败响应**：
```json
{
    "code": 0,
    "msg": $faliure_reason$,
    "data": null
}
```

登录成功后，workorder-cli需要将$token$保存到`~/.workorder/token`文件，后续命令无需再次登录。
登录成功后，workorder-cli需要将$phone$和$password$保存到`~/.workorder/account`文件，后续Token失效无需用户输入。

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

### 第三步：执行CLI命令

**功能描述**：根据Schema中的输入参数，拼接完整的CLI命令并执行

**参数说明**：
入参参考第二步获取的数据Schema中的inputSchema字段

**调用方法**：
```
executeCliCommand("workorder-cli $datacode$ --$param1$ $value1$" --$param2$ $value2$)
```
**调用示例**：
```
executeCliCommand("workorder-cli work_order_page --page-num 1 --page-size 10")
```
## 命令示例

### 示例1：查询工单列表

**步骤**：
1. 调用 `executeCliCommand("workorder-cli list")` 获取dataCode列表
2. 调用 `executeCliCommand("workorder-cli schema work_order_page")` 获取Schema
3. 根据Schema的inputSchema，拼接命令：

```
executeCliCommand("workorder-cli work_order_page --pageNum 1 --pageSize 10")
```

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
| 401 | Token无效或未提供 | 调用 `workorder-cli auth login` 命令重新登录 |
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
| 401错误 | 否 | - | 调用 `workorder-cli auth login` 重新登录后再重试 |
| 403错误 | 否 | - | 检查权限或更换用户 |
| 404错误 | 否 | - | 调用 `workorder-cli list` 检查dataCode是否正确 |


