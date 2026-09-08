# 智能工单系统（Work Order System）

面向企业运维场景的多入口工单管理系统，覆盖从工单创建、审批、派单、处理到确认完成的全流程，并提供 Web 前端、Go CLI、AI 助手和飞书渠道接入。

> 环境准备、配置、数据库初始化、启动顺序和验证方法请见 [部署与启动方案](部署和启动文档/部署与启动方案.md)。

## 功能

- **工单管理**：创建、查询、全文检索、审批、派单、处理、确认、取消、删除、导出和打印。
- **流程编排**：配置多级审核与多人协作处理流程。
- **组织与权限**：管理公司、部门和员工，提供 JWT 认证与接口权限控制。
- **看板与消息**：展示工单状态、类型、处理量、待办和消息，支持延期提醒。
- **命令行工具**：查询或修改工单，支持 Schema 内省、JSON 输出和写操作 `--dry-run` 预览。
- **AI 助手**：支持自然语言对话、CLI 工具调用、RAG 知识库、短期记忆和 SSE 流式输出。
- **多渠道接入**：提供 Web API，并可选接入飞书长连接和账号绑定。

## 架构

```text
浏览器 :5173 ────────────────────────────────────────┐
                                                    │
飞书 / Web 对话 → AI Assistant :8081 → Go CLI → CLI Service :5000 ─┼→ Backend :8080 → MySQL
                         │                         │                  ├→ RabbitMQ
                         ├→ MongoDB（会话记忆）      │                  └→ Elasticsearch
                         ├→ Redis（会话/去重）       │                       ↑
                         └→ Elasticsearch + ONNX（RAG）                Canal ← MySQL
```

| 模块 | 端口 | 职责 |
| --- | ---: | --- |
| `frontend` | 5173 | Vue 3 管理端和 AI 对话界面 |
| `backend/workorder-api` | - | 共享实体、DTO、枚举与服务接口 |
| `backend/workorder-server` | 8080 | 工单、流程、组织、认证、看板和搜索 |
| `cli-service` | 5000 | CLI 查询/写入网关，通过 OpenFeign 调用后端 |
| `workorder-cli` | - | 面向人工、脚本和 Agent 的 Go 命令行客户端 |
| `AiAssistant` | 8081 | AI Agent、RAG、会话记忆与飞书渠道 |

## 核心技术

- Java 17、Spring Boot 3、Spring Security、OpenFeign、MyBatis-Plus
- Vue 3、TypeScript、Vite、Element Plus、Pinia、ECharts
- MySQL 8、RabbitMQ、Elasticsearch 8、Canal、MongoDB、Redis
- Go 1.25、Cobra、Viper
- 智谱 GLM HTTP API、本地 BGE 中文 ONNX 向量模型、飞书 OpenAPI

## 目录

```text
.
├── frontend/              # Web 前端
├── backend/               # 共享 API 与核心后端
├── cli-service/           # CLI 服务网关
├── workorder-cli/         # Go CLI
├── AiAssistant/           # AI 助手
├── 技术方案文档/          # 模块与改造方案
└── 部署和启动文档/        # 启动、部署和运维说明
```

## 工单流转

```text
创建 → 未审核 → 审核中 → 未派单 → 处理中 → 已完成 → 确认完成
                └→ 审核失败          └→ 已超时        └→ 确认失败 → 重新处理
允许取消的阶段 → 已取消
```

## 文档

- [部署与启动方案](部署和启动文档/部署与启动方案.md)
- [RAG 运维说明](部署和启动文档/RAG运维说明.md)
- [CLI 架构](技术方案文档/CLI_architecture.md)
- [AI 助手技术设计](技术方案文档/AiAssistant_technical_design.md)
- [飞书渠道技术设计](技术方案文档/feishu-channel_technical_design.md)


