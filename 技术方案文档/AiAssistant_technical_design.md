# AiAssistant 二期技术方案（ReAct Agent）

## 一、概述

本方案旨在将 AiAssistant 改造为基于 ReAct（Reasoning + Acting）模式的工单客服 Agent，通过调用 CLI 服务和文件操作工具提供智能工单查询和管理能力，通过知识库和 RAG 技术提供工单系统知识智能回复。

### 功能点清单

| 序号 | 功能点 | 描述 | 实现方式 |
|------|--------|------|----------|
| 1 | LLM接口改造 | 切换为智谱 GLM-4.5-Air | 通过 LangChain4j 集成智谱 API |
| 2 | Agent Loop | 显式循环实现，支持工具调用、错误处理、Todo管理 | 参考 learn-claude-code s01 的 while(true) 循环模式 |
| 3 | Tool Use | 动态注册机制，工具按需加载 | 参考 learn-claude-code s02 的工具分发映射表模式 |
| 4 | Skill Loading | 技能懒加载，启动时注入元数据，运行时加载完整内容 | 参考 learn-claude-code s07 的分层加载模式 |
| 5 | TodoWrite | 多步任务先列TODO清单再执行，状态管理 | 参考 learn-claude-code s05 的任务状态机模式 |
| 6 | Hook机制 | LLM执行后/工具执行后打日志、Token统计 | 参考 learn-claude-code s04 的生命周期钩子模式 |
| 7 | 错误恢复 | 错误分类、指数退避重试、Fallback Chain、错误作为信息 | 参考 learn-claude-code s11 的错误恢复体系 |
| 8 | 上下文压缩 | 对话上下文自动压缩，节省token | 参考 learn-claude-code s08 的上下文压缩策略 |
| 9 | Prompt工程 | 动态系统提示词生成，上下文管理 | 模板文件 + 动态变量，运行时构建 |
| 10 | 知识库与RAG | Markdown知识库 + Elasticsearch向量检索 | 本地向量化模型 + 向量数据库检索 |
| 11 | 记忆模块 | 会话级短期记忆（MongoDB） | 长期记忆已移除；压缩按独立短期记忆方案重构 |
| 12 | SSO认证 | 调用backend登录接口获取token，本地文件存储 | 自研登录工具 + Token持久化 |

### ReAct 模式说明

ReAct 是一种让 LLM 在推理过程中通过调用工具获取外部信息的范式：

```
用户问题 → 思考（Reasoning）→ 调用工具（Acting）→ 获取结果 → 思考 → ... → 最终回答
```

---

## 二、技术架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         用户界面                                        │
└─────────────────────────────────────┬───────────────────────────────────┘
                                      │ HTTP/SSE
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    AiAssistant (ReAct Agent)                            │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    Agent Loop (主循环)                            │  │
│  │                                                                   │  │
│  │  ┌──────────────┐    ┌──────────────┐    ┌───────────────────┐   │  │
│  │  │ 调用 LLM     │◀───│ 消息管理      │◀───│ ChatMemoryStore   │   │  │
│  │  │ 生成响应     │    │ (含压缩逻辑)  │    │  (MongoDB持久化)   │   │  │
│  │  └──────┬───────┘    └──────┬───────┘    └───────────────────┘   │  │
│  │         │                   │                                   │  │
│  │         │                   ▼                                   │  │
│  │         │         ┌───────────────────┐                         │  │
│  │         │         │ SessionMemory     │                         │  │
│  │         │         │ （待阶段二/三实现） │                         │  │
│  │         │         └───────────────────┘                         │  │
│  │         ▼                                                       │  │
│  │  ┌──────────────┐                                                │  │
│  │  │ 提取工具调用  │                                                │  │
│  │  └──────┬───────┘                                                │  │
│  │         │                                                        │  │
│  │    ┌────┴────┐                                                   │  │
│  │    │         │                                                   │  │
│  │   无调用   有调用                                                 │  │
│  │    │         │                                                   │  │
│  │    │         ▼                                                   │  │
│  │    │  ┌──────────────┐    ┌─────────────────────────────────┐   │  │
│  │    │  │ ToolDispatcher│───▶│ 工具处理器                       │   │  │
│  │    │  │  (工具分发)   │    │ CliExecutorTools / FileTools   │   │  │
│  │    │  │               │    │ TodoWriteTools / SsoCliTools   │   │  │
│  │    │  └──────────────┘    └─────────────────────────────────┘   │  │
│  │    │         │                                                   │  │
│  │    │         ▼                                                   │  │
│  │    │  ┌─────────────────────────────────────────────────────┐   │  │
│  │    │  │ 外部系统交互                                         │   │  │
│  │    │  │ workorder-cli (二进制) / 文件系统 / backend API      │   │  │
│  │    │  └─────────────────────────────────────────────────────┘   │  │
│  │    │                                                             │  │
│  │    └───────→ 返回最终回答                                         │  │
│  │                                                                   │  │
│  │  ┌───────────────────────────────────────────────────────────┐   │  │
│  │  │ RAG 检索（每轮调用前）                                     │   │  │
│  │  │ ContentRetriever → EmbeddingStore → 知识库文档            │   │  │
│  │  └───────────────────────────────────────────────────────────┘   │  │
│  │                                                                   │  │
│  │  ┌───────────────────────────────────────────────────────────┐   │  │
│  │  │ 会话级短期记忆（MongoDB）；不建设跨会话长期记忆             │   │  │
│  │  └───────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Hook 机制（生命周期扩展）                                         │  │
│  │  POST_LLM_RESPONSE / PRE_TOOL_USE / POST_TOOL_USE / SESSION_END  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  错误恢复机制                                                       │  │
│  │  ErrorClassifier → RetryService → FallbackService → 错误作为信息   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ HTTP 请求
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                CLI Service (端口 5000)                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐        │
│  │ 鉴权模块     │  │ 数据查询代理  │  │  Schema管理            │        │
│  │ (RPC验证)    │  │ (OpenFeign)  │  │                       │        │
│  └──────────────┘  └──────┬───────┘  └───────────────────────┘        │
│                           │                                           │
│                           ▼                                           │
│  ┌───────────────────────────────────────────────────────────────────┐│
│  │                   Backend (端口 8080)                             ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  ││
│  │  │ 工单服务     │  │ 流程服务     │  │  用户服务              │  ││
│  │  └──────────────┘  └──────────────┘  └───────────────────────┘  ││
│  └───────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

**架构说明**：
- Agent 通过 `chatLanguageModel.generate()` 调用 LLM，每次调用前通过 `ContentRetriever` 执行 RAG 检索
- 当前消息通过 `ChatMemoryStore` 读取和保存；结构化短期记忆与压缩按 `short-term-memory_technical_design.md` 分阶段实现
- 不加载、提取或保存跨会话长期记忆
- 工具通过 `ToolDispatcher` 动态分发，支持多种工具处理器
- CLI二进制文件（workorder-cli）作为Agent与后端服务之间的桥梁，参考飞书CLI设计
- 三期将发布 `@workOrder/cli` npm包，通过 `npx @workOrder/cli@latest install` 安装

### 2.2 ReAct 工作流程

```
用户问题: "帮我查一下所有处理中的工单，然后导出成Excel"

思考1: 用户需要查询处理中的工单并导出Excel。首先需要查看可用的CLI命令列表。
行动1: 调用 listCliCommands()
结果1: 返回可用命令列表，包含 work_order page 命令

思考2: 获取到命令列表，需要查看 work_order page 命令的参数说明。
行动2: 调用 getCliCommandSchema("work_order page")
结果2: 返回参数说明，包含 page-num, page-size, status 等参数

思考3: 现在可以调用工单查询命令，查询处理中的工单（status=400）。
行动3: 调用 executeCliCommand("work_order page --page-num 1 --page-size 100 --status 400")
结果3: 返回处理中的工单列表（5条）

思考4: 获取到了工单数据，现在需要将数据写入Excel文件。
行动4: 调用 createExcelFile("处理中工单.xlsx", 工单数据)
结果4: 文件创建成功，路径为 ./output/处理中工单.xlsx

思考5: 任务完成，用户需要的信息已获取并导出。
最终回答: 已查询到5条处理中的工单，已导出到 ./output/处理中工单.xlsx
```

### 2.3 核心组件说明

| 组件 | 职责 | 技术实现 |
|------|------|----------|
| ReAct 引擎 | 管理思考-行动循环 | LangChain4j ReActAgent 显式循环 |
| Agent 控制器 | 接收用户请求，管理对话流程 | Spring Controller + SSE |
| Prompt 工程 | 构建系统提示词，优化上下文 | 模板文件 + 动态变量 |
| CLI 执行工具 | 执行CLI命令，获取JSON结果 | subprocess + ProcessBuilder |
| 文件操作工具 | 创建和读取文件 | Apache POI + Java IO |
| 记忆管理 | 管理会话级短期上下文 | MongoDB |
| LLM 集成 | 调用智谱 GLM-4.5-Air | LangChain4j |
| workorder-cli | CLI二进制文件，桥接Agent与后端 | Go开发的CLI工具（三期） |

---

## 三、各功能点详细设计

### 3.1 LLM接口改造

**功能描述**：将当前使用的LLM服务切换为智谱 GLM-4.5-Air，支持流式响应和向量化模型。

**实现方式**：
- 通过 LangChain4j 的 ZhipuStreamingChatModel 构建流式聊天模型
- 配置模型参数：temperature=0.7，maxTokens=4096
- 使用本地 ONNX 模型（all-MiniLM-L6-v2，384维）进行向量化
- 模型文件提前下载到本地，无需API调用费用

**交付物**：
- 智谱模型配置类
- 本地向量化模型配置类
- application.yaml 中的模型配置项

**测试方法**：
- 验证流式响应是否正常输出
- 验证向量化模型能否正确生成嵌入向量
- 验证API Key缺失时的错误处理

**与其他模块的交互**：
- AgentLoop 调用 chatLanguageModel.generate() 执行推理
- ContentRetriever 使用 embeddingModel 进行查询向量化

### 3.2 Agent Loop

**功能描述**：实现显式的 ReAct 循环，管理思考-行动流程，支持工具调用、错误处理、记忆管理。

**实现方式**：
- 使用 while(true) 循环，每轮执行：调用LLM → 提取工具调用 → 执行工具 → 结果写回 → 判断是否继续
- **RAG检索集成**：每次调用LLM之前，使用当前用户消息作为查询调用 ContentRetriever 获取相关知识库文档，将检索结果作为上下文消息注入到系统提示词或消息列表中
- **短期记忆集成**：当前通过 ChatMemoryStore 读取和保存会话消息；后续接入统一 SessionMemoryService
- 不建设或注入跨会话长期记忆
- 最大思考次数限制为20次，防止无限循环

**交付物**：
- AgentLoop 核心类
- 工具调用提取逻辑
- 消息管理和循环控制

**测试方法**：
- 测试单轮对话（无工具调用）
- 测试多轮工具调用链
- 测试最大思考次数限制
- 测试RAG检索是否正确注入
- 测试删除长期记忆后应用启动、普通对话和工具调用正常

**与其他模块的交互**：
- 调用 chatLanguageModel 进行推理
- 调用 ContentRetriever 进行RAG检索
- 调用 ChatMemoryStore 管理当前会话消息
- 调用 ToolDispatcher 分发工具调用
- 通过 HookRegistry 触发生命周期钩子

### 3.3 Tool Use（工具调用）

**功能描述**：实现工具动态注册和分发机制，支持CLI执行、文件操作、认证、Todo管理等工具。

**实现方式**：
- 定义 ToolHandler 接口，工具处理器实现该接口
- ToolDispatcher 维护工具名称到处理器的映射表
- 新增工具只需实现 ToolHandler 接口并添加 @Component 注解
- Spring 容器自动扫描并注册所有工具处理器

**交付物**：
- ToolDispatcher 工具分发器
- ToolHandler 接口定义
- 各工具处理器实现类

**测试方法**：
- 验证工具注册是否正确
- 验证工具调用是否能正确分发
- 验证未知工具调用的错误处理
- 验证并发工具调用的线程安全性

**与其他模块的交互**：
- AgentLoop 通过 toolDispatcher.dispatch() 调用工具
- 各工具处理器调用外部系统（CLI、文件系统、backend API）

### 3.4 Skill Loading（技能加载）

**功能描述**：实现技能懒加载机制，启动时只注入技能元数据，运行时按需加载完整内容。

**实现方式**：
- 技能文档以 SKILL.md 文件形式存储在 skills/ 目录下
- 每个 SKILL.md 包含 YAML frontmatter（name、description、tags）
- SkillLoader 在应用启动时扫描技能目录，解析元数据
- 系统提示词只包含技能名称和描述（约100 tokens/技能）
- Agent 通过 loadSkill(skillName) 工具按需加载完整内容（约2000+ tokens/技能）

**交付物**：
- SkillLoader 技能加载器
- SKILL.md 文件格式规范
- skills/ 目录结构

**测试方法**：
- 验证技能元数据是否正确解析
- 验证 loadSkill 工具是否能加载完整内容
- 验证技能目录热更新（修改SKILL.md后无需重启）
- 验证未知技能名称的错误处理

**与其他模块的交互**：
- DynamicPromptGenerator 从 SkillLoader 获取技能元数据
- CliExecutorTools 提供 loadSkill 工具供Agent调用

### 3.5 TodoWrite（任务规划）

**功能描述**：实现多步任务的规划和状态管理，确保任务有序执行、进度可见。

**实现方式**：
- TodoManager 维护每个会话的任务列表
- 任务状态机：pending → in_progress → completed，一次只能有一个 in_progress
- 最大任务数限制为20个
- Nag reminder机制：如果LLM连续3轮未更新todo，自动提醒
- Agent通过 todoWrite/todoList/todoClear 工具操作任务列表
- **sessionId自动注入**：todoWrite工具的sessionId参数由系统在工具调用时自动注入，无需LLM提供，确保会话隔离正确性

**交付物**：
- TodoManager 任务状态管理器
- TodoWriteTools 工具处理器
- 系统提示词中的 TodoWrite 使用指导

**测试方法**：
- 验证任务创建和状态更新
- 验证一次只能有一个 in_progress 任务
- 验证 Nag reminder 机制
- 验证任务进度可视化
- 验证会话隔离

**与其他模块的交互**：
- AgentLoop 集成 Nag reminder 逻辑
- DynamicPromptGenerator 在系统提示词中添加 TodoWrite 指导

### 3.6 Hook机制

**功能描述**：实现生命周期钩子机制，在Agent执行的关键节点插入可插拔逻辑。

**实现方式**：
- 定义 AgentHook 接口和 HookType 枚举
- HookRegistry 维护各类型钩子的注册列表
- 支持的钩子类型：SESSION_START、SESSION_END、POST_LLM_RESPONSE、PRE_TOOL_USE、POST_TOOL_USE
- 内置日志记录Hook和Token统计Hook

**交付物**：
- AgentHook 接口
- HookType 枚举
- HookRegistry 注册器
- 内置日志记录和Token统计Hook

**测试方法**：
- 验证各类型钩子是否在正确时机触发
- 验证钩子执行失败不影响主流程
- 验证动态注册钩子功能
- 验证Token统计准确性

**与其他模块的交互**：
- AgentLoop 在关键节点调用 HookRegistry.executeHooks()
- TokenCountingService 通过 Hook 收集数据

### 3.7 错误恢复

**功能描述**：实现完整的错误恢复体系，将错误分为三类处理，支持重试和兜底策略。

**实现方式**：
- ErrorClassifier 将错误分为：TRANSIENT（可重试）、PERMANENT（需换策略）、USER-ACTIONABLE（需用户操作）
- RetryService 实现指数退避重试 + 抖动，防止重试风暴
- FallbackService 实现兜底链，按顺序尝试替代方案
- 错误作为信息返回给AI，让AI自适应调整策略

**交付物**：
- ErrorClassifier 错误分类器
- RetryService 重试服务
- FallbackService 兜底服务
- AgentLoop 集成错误恢复循环

**测试方法**：
- 验证各类错误的分类准确性
- 验证TRANSIENT错误的重试机制
- 验证PERMANENT错误的兜底策略
- 验证USER-ACTIONABLE错误的用户提示
- 验证错误作为信息返回后AI的自适应能力

**与其他模块的交互**：
- AgentLoop 在工具执行失败时调用错误恢复机制
- CliExecutorTools 和 FileTools 作为兜底策略的调用目标

### 3.8 上下文压缩

**功能描述**：实现对话上下文自动压缩机制，当上下文超过阈值时自动总结历史消息，节省Token。

**实现方式**：
- 旧的固定消息数、字符数估算和内存包装器已删除
- 后续按动态 token 预算、完整工具原子单元和结构化滚动摘要实现

**交付物**：
- SessionMemoryService 会话记忆服务
- TokenEstimator 和消息原子分段组件
- 结构化滚动摘要及 MongoDB 持久化

**测试方法**：
- 验证消息数超过阈值时自动触发压缩
- 验证压缩后消息数量减少
- 验证压缩后关键信息保留
- 验证压缩不影响对话连贯性

**与其他模块的交互**：
- AgentLoop 通过 SessionMemoryService 准备上下文并保存本轮增量

### 3.9 Prompt工程

**功能描述**：实现动态系统提示词生成，根据当前可用工具和技能动态构建提示词。

**实现方式**：
- DynamicPromptGenerator 在运行时动态生成系统提示词
- 提示词包含：角色定位、可用技能列表、可用工具列表、知识来源、交互原则、注意事项、TodoWrite使用规范
- 技能元数据从 SkillLoader 获取
- 工具列表从 Spring 容器动态发现（扫描 @Tool 注解）

**交付物**：
- DynamicPromptGenerator 动态提示词生成器
- 系统提示词模板结构

**测试方法**：
- 验证提示词包含所有必要组件
- 验证技能列表动态更新
- 验证工具列表动态更新
- 验证提示词长度在合理范围内

**与其他模块的交互**：
- 从 SkillLoader 获取技能元数据
- 从 ApplicationContext 获取工具列表
- AgentLoop 使用生成的提示词进行推理

### 3.10 知识库与RAG

**功能描述**：实现基于RAG的知识库检索，为Agent提供工单系统领域知识。

**实现方式**：
- 知识库采用多文件Markdown组织，按主题拆分为独立文件
- 应用启动时自动扫描知识库目录，通过 EmbeddingStoreIngestor 导入向量数据库
- **classpath资源加载**：知识库文件位于classpath下，必须通过 Spring 的 ResourcePatternResolver 或 ResourceLoader 加载，不能使用 Paths.get() 直接解析
- 使用本地向量化模型（all-MiniLM-L6-v2）进行向量化
- ContentRetriever 在每次LLM调用前执行相似性检索，返回Top-K相关文档
- 检索结果注入LLM上下文，辅助回答

**交付物**：
- KnowledgeBaseIngestor 文档导入组件
- ContentRetriever 检索器配置
- 知识库目录结构和文件规范

**测试方法**：
- 验证文档导入是否成功
- 验证相似性检索结果的相关性
- 验证检索结果正确注入上下文
- 验证知识库更新后检索结果更新

**与其他模块的交互**：
- AgentLoop 在调用LLM前调用 ContentRetriever
- LocalEmbeddingModel 提供向量化能力
- Elasticsearch 作为向量存储

### 3.11 记忆模块

**功能描述**：实现会话级短期记忆管理；不提供跨会话用户长期记忆。

**实现方式**：
- 短期记忆：使用 MongoDB 存储会话消息；结构化摘要、最近完整消息、归属、版本和 TTL 按短期记忆方案改造
- 动态系统提示词和 RAG 不作为普通会话历史重复持久化
- 记忆文件数达到10个时触发合并去重

**交付物**：
- 短期记忆配置和 MongoDB 会话仓储
- SessionMemoryService 及上下文压缩组件

**测试方法**：
- 验证短期记忆持久化、读取、压缩、过期和用户隔离
- 验证删除长期记忆后应用启动和 Agent 主链路正常

**与其他模块的交互**：
- AgentLoop 通过统一短期记忆服务准备和保存上下文

### 3.12 SSO认证

**功能描述**：实现登录认证和Token管理，支持调用backend登录接口获取和管理Token。

**实现方式**：
- SsoCliTools 提供 login/getToken/logout/checkLoginStatus 工具
- TokenFileManager 将Token存储到本地文件（~/.workorder/token）
- Token存储格式为JSON，包含token、过期时间、手机号等信息
- Token有效期为7天
- CLI工具执行时从本地文件读取Token并设置到环境变量

**交付物**：
- SsoCliTools 认证工具
- TokenFileManager Token文件管理器
- Token存储格式规范

**测试方法**：
- 验证登录成功后Token正确保存
- 验证Token过期后需要重新登录
- 验证退出登录后Token正确清除
- 验证登录失败的错误处理

**与其他模块的交互**：
- CliExecutorTools 在执行CLI命令时读取Token
- AgentLoop 在遇到401错误时提示用户重新登录

---

## 四、测试场景设计

### 4.1 正常流程测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| 工单列表查询 | 用户询问工单列表 | Agent调用CLI工具查询并返回结果 |
| 工单查询+导出Excel | 查询处理中工单并导出 | 多工具调用链：listCliCommands → getCliCommandSchema → executeCliCommand → createExcelFile |
| 工单查询+生成报告 | 查询本周完成工单并生成Markdown报告 | 多工具调用链：查询 → createMarkdownFile |
| 读取Excel并分析 | 读取已导出的Excel文件并统计 | readExcelFile → 分析数据 → 回答 |
| 多轮对话+文件操作 | 查询消息并保存到TXT | 多轮交互：查询消息 → createTxtFile |

### 4.2 RAG检索测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| 状态码解释 | 用户询问工单状态200的含义 | RAG检索知识库，返回状态解释 |
| 流程说明 | 用户询问工单处理流程 | RAG检索知识库，返回流程说明 |
| 类型说明 | 用户询问工单类型和优先级 | RAG检索知识库，返回类型说明 |

### 4.3 错误恢复测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| TRANSIENT错误-网络超时 | CLI服务网络超时 | 指数退避重试3次，成功则继续，失败则返回错误 |
| TRANSIENT错误-限流 | CLI服务返回429 | 指数退避重试3次，间隔递增 |
| PERMANENT错误-文件不存在 | 读取不存在的Excel文件 | 尝试兜底策略（读取txt/md），失败则返回错误作为信息 |
| PERMANENT错误-参数错误 | CLI命令参数错误 | 不重试，返回错误作为信息，AI修正参数后重试 |
| USER-ACTIONABLE错误-未授权 | CLI调用返回401 | 提示用户重新登录 |
| USER-ACTIONABLE错误-权限不足 | CLI调用返回403 | 提示权限不足，需要用户操作 |

### 4.4 记忆与压缩测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| 上下文压缩触发 | 对话超过20轮 | 自动压缩历史消息，保留最近10条+摘要 |
| 短期记忆持久化 | 跨会话查询历史对话 | MongoDB中正确存储和读取 |
| 长期记忆移除回归 | 应用启动、普通对话、工具调用 | 不依赖本地记忆文件且主链路正常 |

### 4.5 TodoWrite测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| 多步任务规划 | 复杂任务需要多个步骤 | Agent先调用todoWrite创建任务列表 |
| 任务状态更新 | 执行任务后更新状态 | todoWrite更新任务状态为completed |
| Nag reminder触发 | LLM连续3轮未更新todo | 系统自动提醒更新任务列表 |
| 会话隔离 | 多个会话并行 | 每个会话有独立的todo列表 |

### 4.6 认证测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| 首次登录 | 用户调用login工具 | 登录成功，Token保存到本地 |
| Token过期 | 7天后再次使用 | 提示Token过期，需要重新登录 |
| 退出登录 | 用户调用logout工具 | Token清除，需要重新登录 |
| 登录失败 | 用户名密码错误 | 返回错误信息 |

### 4.7 服务降级测试

| 场景 | 描述 | 预期结果 |
|------|------|----------|
| CLI服务不可用 | backend服务宕机 | 返回友好提示，提供替代方案 |
| LLM服务不可用 | 智谱API不可用 | 返回预设错误提示，记录错误追踪ID |
| 向量数据库不可用 | Elasticsearch不可用 | RAG检索降级，使用知识库文件直接匹配 |
| 并发会话 | 多个用户同时使用 | 各会话独立，数据隔离 |

---

## 五、实施计划

### 5.1 功能点实施清单

| 功能点 | 交付边界 | 测试方法 | 依赖模块 | 优先级 |
|--------|----------|----------|----------|--------|
| **LLM接口改造** | 智谱模型配置完成，本地向量化模型可用 | 流式响应测试、向量化测试 | 无 | P0 |
| **Agent Loop** | 显式循环实现，支持工具调用和记忆管理 | 单轮/多轮对话测试、RAG集成测试 | LLM接口改造 | P0 |
| **Tool Use** | 工具分发机制完成，CLI执行工具可用 | 工具注册测试、工具调用测试 | Agent Loop | P0 |
| **CLI执行工具** | executeCliCommand/listCliCommands/getCliCommandSchema/loadSkill工具实现 | CLI命令执行测试、技能加载测试 | Tool Use | P0 |
| **文件操作工具** | createExcelFile/readExcelFile/createMarkdownFile/readMarkdownFile/createTxtFile/readTxtFile实现 | 文件读写测试、Excel操作测试 | Tool Use | P0 |
| **知识库与RAG** | 文档导入完成，ContentRetriever可用 | 文档导入测试、检索相关性测试 | LLM接口改造 | P0 |
| **Prompt工程** | 动态提示词生成器完成 | 提示词内容验证、动态更新测试 | Skill Loading | P1 |
| **Skill Loading** | 技能扫描和懒加载机制完成 | 技能元数据解析测试、loadSkill测试 | 无 | P1 |
| **上下文压缩** | 动态预算、原子分段和滚动摘要完成 | 压缩触发、协议完整和摘要回滚测试 | Agent Loop | P1 |
| **短期记忆** | SessionMemoryService 与 MongoDB 结构化存储完成 | 持久化、归属、并发和过期测试 | 上下文压缩 | P1 |
| **SSO认证** | SsoCliTools和TokenFileManager完成 | 登录测试、Token管理测试 | 无 | P1 |
| **Hook机制** | HookRegistry和内置Hook完成 | 钩子触发测试、日志记录测试 | Agent Loop | P2 |
| **错误恢复** | ErrorClassifier/RetryService/FallbackService完成 | 错误分类测试、重试测试、兜底测试 | Agent Loop | P2 |
| **TodoWrite** | TodoManager和TodoWriteTools完成 | 任务管理测试、Nag reminder测试 | Agent Loop | P2 |
| **Agent控制器** | AIChatController完成，支持SSE流式响应；agentLoop.run()需在专用线程池执行（如Schedulers.boundedElastic），避免阻塞响应式事件循环 | 接口测试、并发测试、线程阻塞测试 | Agent Loop | P0 |

### 5.2 实施阶段划分

**阶段一：基础能力搭建（P0）**

| 步骤 | 功能点 | 交付物 | 依赖 |
|------|--------|--------|------|
| 1.1 | LLM接口改造 | 智谱模型配置、本地向量化模型配置 | 无 |
| 1.2 | Tool Use | ToolDispatcher、ToolHandler接口 | 无 |
| 1.3 | CLI执行工具 | CliExecutorTools（含loadSkill） | Tool Use |
| 1.4 | 文件操作工具 | FileTools | Tool Use |
| 1.5 | 知识库与RAG | KnowledgeBaseIngestor、ContentRetriever | LLM接口改造 |
| 1.6 | Agent Loop | 核心循环实现，集成RAG和工具调用 | 1.1-1.5 |
| 1.7 | Agent控制器 | AIChatController，SSE流式响应 | Agent Loop |

**阶段二：记忆与Prompt（P1）**

| 步骤 | 功能点 | 交付物 | 依赖 |
|------|--------|--------|------|
| 2.1 | Skill Loading | SkillLoader、SKILL.md规范 | 无 |
| 2.2 | Prompt工程 | DynamicPromptGenerator | Skill Loading |
| 2.3 | 上下文压缩 | TokenEstimator、原子分段和滚动摘要 | 无 |
| 2.4 | 短期记忆 | SessionMemoryService、MongoDB结构化存储 | 上下文压缩 |
| 2.6 | SSO认证 | SsoCliTools、TokenFileManager | 无 |
| 2.7 | 更新Agent Loop | 接入统一短期记忆服务 | 2.4 |

**阶段三：增强能力（P2）**

| 步骤 | 功能点 | 交付物 | 依赖 |
|------|--------|--------|------|
| 3.1 | Hook机制 | HookRegistry、内置日志和Token统计Hook | Agent Loop |
| 3.2 | 错误恢复 | ErrorClassifier、RetryService、FallbackService | Agent Loop |
| 3.3 | TodoWrite | TodoManager、TodoWriteTools | Agent Loop |
| 3.4 | 更新动态提示词 | 添加TodoWrite使用指导 | Prompt工程 |
| 3.5 | 更新Agent Loop | 集成错误恢复和TodoWrite | 3.2、3.3 |

### 5.3 验证标准

每个功能点完成后需满足以下验证标准：

| 验证维度 | 标准 |
|----------|------|
| 功能完整性 | 所有设计的功能点均已实现 |
| 接口正确性 | REST API返回正确的状态码和响应格式 |
| 工具可用性 | 所有工具均可被Agent正确调用 |
| RAG准确性 | 检索结果与查询相关，能辅助回答 |
| 错误处理 | 错误分类正确，重试和兜底策略有效 |
| 记忆持久性 | 会话级短期记忆正确存储、读取、隔离和过期 |
| 性能指标 | 响应时间 < 3秒（不含LLM推理时间），并发 > 10用户 |
| 安全性 | Token管理安全，权限控制有效 |

---

## 六、技术栈总结

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | 框架基础 |
| Spring Cloud OpenFeign | 4.1.x | HTTP 客户端 |
| LangChain4j | 0.33.x | LLM 集成 + ReAct Agent + RAG |
| LangChain4j ONNX | 0.33.x | 本地向量化模型支持 |
| 智谱 GLM-4.5-Air | API | 大语言模型 |
| all-MiniLM-L6-v2 | 本地模型 | 向量化模型（384维） |
| Apache POI | 5.2.x | Excel 文件操作 |
| MongoDB | 6.x | 会话记忆存储 |
| Elasticsearch | 8.x | 向量数据库 |
| Spring Retry | 2.0.x | 重试机制 |

---

## 七、三期CLI工具规划

### 7.1 设计目标

参考飞书CLI的设计模式，三期将设计并实现 `@workOrder/cli` npm包，提供面向AI Agent的工单系统命令行工具。

| 设计原则 | 说明 |
|----------|------|
| AI友好 | 所有操作通过参数一次性传入，不弹交互式菜单，输出JSON格式 |
| 自描述 | 支持命令发现和Schema内省，Agent可动态获取可用命令和参数说明 |
| 元数据驱动 | 通过元数据自动生成命令，新增API无需手写代码 |
| 三层架构 | Shortcuts（快捷命令）、API Commands（API映射）、Raw API（原始调用） |
| 安全可控 | 支持权限校验、命令预览（--dry-run）、输入验证 |

### 7.2 三层命令架构

#### 第一层：Shortcuts（快捷命令）

带 `+` 前缀的高频操作，封装常见业务场景：

```bash
workorder-cli work_order +list
workorder-cli work_order +list --status 400
workorder-cli dashboard +overview
workorder-cli work_order +search --keyword "服务器"
```

#### 第二层：API Commands（API命令）

与后端API一一对应，参数更明确：

```bash
workorder-cli work_order page --page-num 1 --page-size 10
workorder-cli work_order detail --id 1
workorder-cli dashboard messages --page-num 1 --page-size 20
```

#### 第三层：Raw API（原始调用）

直接调用任意后端接口，灵活性最高：

```bash
workorder-cli api call --method POST --endpoint /workOrder/page --body '{"pageNum": 1, "pageSize": 10}'
```

### 7.3 核心特性

- **Schema内省**：Agent可动态发现可用命令和参数说明
- **JSON输出契约**：所有命令支持 `--output=json` 参数
- **命令预览**：支持 `--dry-run` 参数预览执行结果
- **认证管理**：支持环境变量、配置文件、命令行参数、系统密钥链等多种认证方式

### 7.4 实施计划

| 阶段 | 内容 | 时间 |
|------|------|------|
| Phase 1 | 基础框架搭建，命令行入口设计 | 2周 |
| Phase 2 | 认证模块、HTTP客户端、输出格式化 | 2周 |
| Phase 3 | 元数据驱动命令生成、Schema内省 | 3周 |
| Phase 4 | Shortcuts快捷命令、SKILL.md编写 | 2周 |
| Phase 5 | 安全验证、测试、发布npm包 | 2周 |
