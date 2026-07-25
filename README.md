# 工单系统（Work Order System）

基于 Spring Boot 的智能工单系统，集成 AI 助手提供智能问答能力，支持 CLI 命令行工具操作。

## 项目架构

### 系统模块

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         用户界面 / AI Agent                              │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTP/SSE / 进程调用
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    AiAssistant (ReAct Agent) 端口: 8081                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │ Agent Loop       │  │ 工具调用          │  │ RAG检索              │  │
│  │ (主循环)         │  │ CLI/文件/认证    │  │ 知识库问答           │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ 进程调用 (ProcessBuilder)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                 workorder-cli (Go命令行工具)                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │ 认证模块         │  │ 命令解析器       │  │ Schema管理           │  │
│  │ (Token管理)      │  │ (Cobra框架)      │  │ (动态获取)           │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTP 请求
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               CLI Service (端口: 5000)  +  Backend (端口: 8080)         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │ 鉴权模块         │  │ 数据查询代理      │  │ 工单服务/流程服务    │  │
│  │ (RPC验证)        │  │ (OpenFeign调用)   │  │ 用户服务/数据看板    │  │
│  └──────────────────┘  └──────────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 模块说明

| 模块 | 技术栈 | 端口 | 职责 |
|------|--------|------|------|
| **workorder-api** | 纯Java模块 | - | **共享API模块**，定义Service接口和DTO，作为backend和cli-service的桥梁 |
| **backend** | Spring Boot 3.3 + MySQL + MyBatis-Plus | 8080 | 工单系统核心业务服务，实现workorder-api接口 |
| **cli-service** | Spring Boot 3.3 + Spring Cloud OpenFeign | 5000 | API代理服务，通过OpenFeign调用backend，提供统一数据查询入口 |
| **AiAssistant** | Spring Boot 3.2 + LangChain4j + 智谱GLM | 8081 | 基于ReAct模式的智能客服Agent |
| **workorder-cli** | Go + Cobra | - | 命令行工具，桥接Agent与后端服务 |

### 目录结构

```
-WorkOrder/
├── backend/                      # 后端服务
│   ├── workorder-api/            # CLI查询API模块（接口定义+DTO）
│   └── workorder-server/         # 后端主服务
├── cli-service/                  # CLI代理服务
├── AiAssistant/                  # AI助手服务
└── workorder-cli/                # Go命令行工具
```

## 调用链路

### AI助手查询链路

```
用户问题 → AiAssistant Agent Loop → 思考(Reasoning) → 调用工具(Acting)
    ↓
workorder-cli 命令执行 → CLI Service 代理 → Backend API
    ↓
结果返回 → Agent总结 → 最终回答用户
```

## 核心功能

### 后端服务功能

| 模块 | 功能 |
|------|------|
| 工单管理 | 工单创建、分页查询、详情查询、审核流程、状态流转 |
| 用户管理 | 用户登录、个人信息管理、权限控制 |
| 流程管理 | 审核流程配置、流程查询 |
| 数据看板 | 统计概览、本周处理数量、消息中心 |

### AI助手功能

| 功能 | 说明 |
|------|------|
| ReAct Agent | 基于LangChain4j的显式思考-行动循环 |
| 工具调用 | CLI执行、文件操作、认证管理、Todo管理 |
| RAG检索 | 基于知识库的智能问答 |
| 记忆管理 | 短期记忆(MongoDB) + 长期记忆(文件存储) |
| 错误恢复 | 错误分类、指数退避重试、兜底策略 |
| Hook机制 | 日志记录、Token统计 |

### CLI工具功能

| 命令模块 | 命令 |
|----------|------|
| work_order | page/detail/search/+list/+search |
| dashboard | data/handle_quantity/messages/+overview |
| flow | getById/page |
| auth | login/logout/status |
| api | call |
| list | 获取可用命令列表 |
| schema | 获取命令Schema |

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 语言 | Go | 1.23+ |
| Web框架 | Spring Boot | 3.2/3.3 |
| RPC框架 | Spring Cloud OpenFeign | 4.1.0 |
| ORM框架 | MyBatis-Plus | 3.5.5 |
| LLM集成 | LangChain4j | 1.17.2 |
| 大模型 | 智谱 GLM-4.5-Air | API |
| 向量化 | all-MiniLM-L6-v2 | 本地模型 |
| 向量存储 | Elasticsearch | 8.x |
| 数据库 | MySQL | 8.x |
| 会话存储 | MongoDB | 6.x |
| 缓存 | Redis | 7.x |
| Excel操作 | Apache POI | 5.2.5 |
| CLI框架 | Cobra | Go |

## 测试文件

### Java测试

| 模块 | 测试文件 |
|------|----------|
| AiAssistant | AiAssistantApplicationTests.java |
| AiAssistant | LongTermMemoryServiceTest.java |
| AiAssistant | StageOneAcceptanceTest.java |
| backend | SpringVueDemoApplicationTests.java |
| backend | AuthControllerTest.java |
| backend | TraceIdInterceptorTest.java |

### Python测试脚本

| 脚本 | 用途 |
|------|------|
| test_cli_tools.py | CLI工具功能测试 |
| test_error_handling.py | 错误恢复机制测试 |
| test_file_tools.py | 文件操作工具测试 |
| test_knowledge_base.py | 知识库RAG测试 |

### 验证用例

| 文档 | 说明 |
|------|------|
| VERIFICATION_CASES.md | CLI工具阶段3/4/5验证用例 |

## 部署说明

### 前置依赖

| 服务 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.x | 后端数据库（库名: wos） |
| Elasticsearch | 8.x | 向量存储、全文搜索 |
| MongoDB | 6.x | 会话记忆存储 |
| Redis | 7.x | 缓存 |
| Canal | - | MySQL数据同步 |

### 启动顺序

1. **启动基础服务**
   ```bash
   ./elasticsearch.bat
   ./canal.bat
   ```

2. **启动后端服务**
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run -pl workorder-server
   # 端口: 8080
   ```

3. **启动CLI服务**
   ```bash
   cd cli-service
   mvn clean install
   mvn spring-boot:run
   # 端口: 5000
   ```

4. **编译CLI工具**
   ```bash
   cd workorder-cli
   go build -o workorder-cli.exe .
   ```

5. **启动AI助手**
   ```bash
   cd AiAssistant
   mvn clean install
   mvn spring-boot:run
   # 端口: 8081
   ```

### 数据库初始化

1. 创建MySQL数据库：`wos`
2. 执行初始化脚本：`backend/workorder-server/src/main/resources/workorder.sql`
3. 执行组织架构数据：`backend/workorder-server/src/main/resources/organization.sql`

### 接口测试

```bash
# 后端登录
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"phone": "13812345678", "password": "newPassword123!"}'

# AI助手聊天
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查一下处理中的工单", "userId": "1"}'
```

## 架构文档

| 文档 | 说明 |
|------|------|
| AiAssistant_technical_design.md | AI助手二期技术方案（ReAct Agent） |
| CLI_architecture.md | CLI服务技术架构文档 |
| workorder-cli_technical_design.md | CLI工具三期技术架构文档 |
| SKILL.md | CLI查询技能文档 |
| 后端部署.md | 后端部署说明 |

## 许可证

MIT License