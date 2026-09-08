# 工单 Web 前端技术方案

> 版本：v0.2（范围确认稿）  
> 调研日期：2026-08-31  
> 目标：为核心工单后端与 AI Agent 提供统一的 Vue Web 管理端

## 1. 结论摘要

建议在仓库根目录新增独立的 `frontend/` 工程，采用 Vue 3 + TypeScript + Vite 构建单页应用，以 Element Plus 承担企业后台基础组件，以 Pinia 管理登录态和少量跨页面状态，以 Vue Router 管理路由与页面权限，以 Axios 访问常规 REST 接口，并使用原生 `fetch + ReadableStream + AbortController` 接入 Agent 对话。

本期仅做本地联调，通过 Vite 开发代理统一访问 Backend 和 AiAssistant，浏览器不直接跨域访问 8080/8081。未来有域名、HTTPS 和 Nginx 后，再按同样的路径规则平移到生产反向代理：

```text
浏览器
  │
  ▼
https://workorder.example.com
  ├── /                         → Vue 静态资源
  ├── /api/backend/**           → Backend :8080（去掉 /api/backend 前缀）
  └── /api/assistant/**         → AiAssistant :8081（去掉 /api/assistant 前缀）
                                      │
                                      └── 继续复用 CLI / Backend / MongoDB / RAG
```

本地阶段由 Vite 解决同源访问，不要求现在建设 Nginx、域名或 HTTPS。未来部署时再由网关统一承担认证安全头、限流和 SSE 超时等能力。前端不应直接调用 `cli-service` 或 Go CLI；Agent 内部仍按现有链路使用 CLI。

当前接口足以先完成登录、看板、工单主流程、流程管理、组织/人员管理、个人信息和基础 AI 对话。Agent 真流式输出、待办/消息跳转、前端可用的操作权限描述仍需后端补充；附件上传已明确排除在一期之外，详见第 9 节。

## 2. 项目调研结果

### 2.1 现有服务

| 模块 | 端口 | 与前端的关系 |
| --- | ---: | --- |
| `backend/workorder-server` | 8080 | 登录、认证、工单、流程、组织、人员、看板、搜索、导出和打印 |
| `AiAssistant` | 8081 | Web Agent 对话、会话记忆清理 |
| `cli-service` | 5000 | Agent/CLI 的内部网关，Web 前端无需直连 |
| `workorder-cli` | - | Agent 工具执行依赖，Web 前端无需感知 |

Backend 采用 Spring Boot 3.3、Spring Security、JWT Access Token + 可轮换 Refresh Token；AiAssistant 采用 Spring Boot 3.2，同时使用 MVC 与 Reactor 类型返回对话结果。

### 2.2 已有 Web 能力

- Backend 已提供 Knife4j/OpenAPI 文档：`/doc.html`、`/v3/api-docs`。
- AiAssistant 仅有一个占位 `static/index.html`，不是可继续演进的前端工程。
- 两套服务当前都没有有效的浏览器跨域配置，开发和生产都应通过代理以同源方式访问，避免开放宽泛 CORS。
- Backend 普通接口大多经 `ResponseAdvice` 包装为 `{ code, msg, data }`；`/api/auth/**` 被排除，认证接口存在“包装与不包装并存”的情况。
- 登录接口返回 `{ code, msg, data: AuthTokenResponse }`，其中包含 access token、refresh token 及各自过期时间；受保护接口使用 `Authorization: Bearer <accessToken>`。
- 管理员接口以 `ROLE_ADMIN` 控制公司、部门和员工管理。数据库初始化数据包含 `role=admin` 的管理员账号，新建员工默认由数据库赋予 `user` 角色，现有接口没有把普通员工提升为管理员的能力。
- 流程创建、编辑、删除在 Controller 和 Service 两层都没有管理员校验，因此当前行为就是所有已登录用户均可管理流程；本期前端按后端现状开放，不额外伪造管理员限制。

### 2.3 核心业务能力

工单状态包括未审核、审核中、审核失败、未派单、处理中、已超时、已处理、已验收、验收失败、已取消。已有操作包括：创建、审批、派单、请求协助、催单、完成、验收通过/失败、取消、删除、导出、打印与 Elasticsearch 全文搜索。

主要可用页面数据如下：

- 看板：本月已处理、待处理、未审核、超时数量；状态/类型分布；本周处理量；待办；消息列表。
- 工单：条件分页、详情、处理轨迹、创建、状态操作、导出、PDF 打印、全文搜索。
- 流程：分页、详情、创建、编辑、删除；流程由多级审核节点、一个派单节点和一个验收节点组成。
- 用户与组织：个人资料、组织架构、员工查询；管理员可维护公司、部门与员工。
- Agent：`POST /assistant/chat`，请求体为 `{ memoryId, message }`，要求 Bearer Token；`DELETE /assistant/memory/{sessionId}` 可清理会话。

### 2.4 已发现的契约问题

1. Agent 接口声明 `text/event-stream`，但 `AgentLoop` 当前在完整 ReAct 执行结束后只 `next` 一次，`DefaultAgentGateway` 还会先 `collectList().block()`，所以用户体验是“等待后整段出现”，不是逐段流式。
2. Agent 没有稳定的事件协议，成功内容是文本，部分异常则手工拼成 `data: {...}\n\n`；前端难以可靠区分正文、错误、结束、工具状态与写操作确认。
3. `WorkOrderTodoVO` 没有工单 `id/code`，待办卡片无法可靠跳转工单详情。
4. `MessageVO` 没有消息 ID、关联工单 ID/编号和已读状态，也没有标记已读接口，消息中心只能展示文本。
5. 创建工单只有 `accessoryUrl/accessoryName` 字段，仓库中没有文件上传/删除/下载签名接口。
6. 后端没有直接返回 `allowedActions`。若前端自行复制复杂的“状态 + 当前处理人 + 提交人”规则，后续很容易与服务端漂移。
7. 接口参数校验覆盖不完整，不少控制器也没有使用 `@Valid`；前端校验不能代替服务端校验。
8. REST 返回结构、HTTP 错误体、二进制导出/打印三类响应需要分别处理，不能由一个简单响应拦截器一刀切。

## 3. 建设范围

### 3.1 建议一期（MVP）

- 登录、自动续期、退出、个人信息。
- 工作台：指标卡、趋势图、分布图、待办和消息摘要。
- 工单列表、组合筛选、全文搜索、详情、创建和完整流转操作。
- 工单导出、打印 PDF。
- 流程列表、详情和可视化编辑。
- 组织架构和员工选择器。
- Agent 独立页 + 全局侧边抽屉，共用当前登录身份。
- 管理员公司、部门、员工维护作为次优先级功能实现，但不纳入本期正式验收范围。
- 403、404、服务不可用等通用页面。

### 3.2 暂不建议一期扩张

- 不在浏览器重做 CLI Schema、dry-run 或 Agent 工具调试台。
- 不建设复杂低代码流程设计器；一期采用受约束的节点编排表单。
- 一期不提供附件上传；创建和详情页面保留附件字段兼容能力，但默认隐藏上传入口。
- 不做微前端、SSR、PWA 或离线能力。
- 不让前端直接连接 MySQL、Elasticsearch、MongoDB、Redis 或 CLI Service。

## 4. 技术选型

| 层次 | 选型 | 理由 |
| --- | --- | --- |
| 框架 | Vue 3、Composition API、`<script setup>` | 与用户指定栈一致，适合中型后台系统和模块化组合 |
| 语言 | TypeScript strict | 工单状态、分页、流程节点和多种响应形态都需要静态约束 |
| 构建 | Vite | Vue 官方脚手架的默认构建方案，开发反馈快 |
| 运行时 | Node.js 24 LTS，使用 `.nvmrc`/Volta 固定 | 当前处于 LTS；不选 Current 或已 EOL 版本 |
| 包管理 | pnpm，提交锁文件 | 安装快、依赖边界清晰；团队如已统一 npm 可替换 |
| 路由 | Vue Router（使用与 Vue 3 兼容的当前稳定大版本） | 官方路由；路由元信息承载登录和角色门禁 |
| 状态 | Pinia | Vue 官方推荐状态方案；只存认证、用户、少量 UI/对话元数据 |
| UI | Element Plus + 自定义 Design Tokens | 与表格、表单、分页、弹窗密集的企业后台匹配 |
| 请求 | Axios（REST/Blob）+ fetch（Agent 流） | REST 拦截器和文件下载方便；fetch 更适合 POST、Bearer Header 与流读取 |
| 图表 | Apache ECharts，按需引入 | 与现有看板的柱状、折线、饼/环图需求匹配 |
| 内容渲染 | markdown-it + DOMPurify | Agent Markdown 展示，同时阻断不可信 HTML/XSS |
| 日期 | dayjs | 统一处理后端的时间戳、LocalDateTime 和字符串时间 |
| 单测 | Vitest + Vue Test Utils + MSW | 覆盖状态机、请求适配器、组件交互与异常响应 |
| 端到端 | Playwright | 覆盖 Chromium，并对登录和主流程进行真实浏览器回归 |
| 代码质量 | ESLint + Prettier + vue-tsc | 提交前同时执行 lint、类型检查、单测和构建 |

版本策略采用“初始化时锁定精确版本 + Renovate/Dependabot 定期小步升级”，技术方案不硬编码未来会过期的补丁号。Vue 官方当前建议通过 `create-vue` 创建 Vite + TypeScript 工程；Pinia 是默认状态管理建议；Node 官方建议生产使用 LTS。参考资料见第 12 节。

## 5. 前端信息架构

```text
/login                         登录
/
├── /dashboard                工作台
├── /work-orders              工单列表/筛选/搜索
│   ├── /new                  新建工单
│   └── /:id                  工单详情、处理轨迹、可用操作
├── /flows                    流程列表
│   ├── /new                  新建流程
│   └── /:id/edit             编辑流程
├── /messages                 消息中心
├── /organization             组织架构
├── /assistant                AI 助手
├── /profile                  个人中心
└── /admin                    管理员可见
    ├── /companies            公司管理
    ├── /departments          部门管理
    └── /staff                员工管理
```

桌面端采用浅色主题、左侧主导航 + 顶部用户区；Agent 除独立页面外，提供全局右侧抽屉，用户浏览工单时可随时提问。宽度较小时导航折叠，表格保留横向滚动；一期按 PC 管理后台优化，不承诺完整移动端操作体验，也暂不建设暗色主题。

### 5.1 工单列表

- 默认显示编号、标题、类型、优先级、状态、提交人、当前处理人、创建和截止时间。
- 快速筛选：我的待办、我提交的、处理中、已超时、已完成。
- 高级筛选：类型、状态多选、优先级、提交/审批/派单/处理/验收人、公司、部门、创建/截止时间。
- 普通条件分页调用 `/workOrder/page`；关键词全文检索调用 `/workOrder/search`，两种模式在 UI 上明确切换，避免分页模型混用。
- 导出必须使用当前筛选条件，下载响应按 Blob 处理；打印在新窗口展示服务端 PDF Blob URL。

### 5.2 工单详情与操作

- 顶部展示编号、状态、优先级、截止时间和超时提示。
- 主体展示内容；若历史工单已有附件 URL，则展示只读附件链接。右侧或下方以时间线展示提交、审核、派单、处理、验收及备注。
- 操作按钮由服务端 `allowedActions` 决定；在接口补齐前，可临时依据状态和处理人推断展示，但最终提交仍以服务端校验为准。
- 派单/协助弹窗复用员工选择器；审批、完成和验收操作要求备注或二次确认。
- 取消、删除等不可逆或高影响动作使用明确的对象名称和状态二次确认。

### 5.3 流程编辑

一期使用“审核节点列表 + 派单人 + 验收人”的结构化编辑器，而不是自由拖拽画布：

```text
审核人 A → 审核人 B → 派单人 → 处理人（运行期指派）→ 验收人
```

审核节点支持增删和排序，派单与验收各限一个，人员只能从员工接口选择。提交前在前端校验重复审核人、缺少必要节点等规则，服务端继续作为最终裁决者。

### 5.4 Agent 交互

- `memoryId` 在创建会话时由前端生成安全的 Long 范围 ID并持久化会话列表；会话与当前用户绑定，切换账号后清空本地索引。
- 通过 `fetch` 发起 POST 并附带 Bearer Token；原生 `EventSource` 不适合当前 POST + 自定义 Authorization Header 场景。
- 支持停止生成、清空当前会话、复制回答、重新发送、自动滚动和 Markdown/代码块展示。
- 写操作必须展示 Agent 的 dry-run 预演和确认步骤，不在前端自动替用户确认。
- 服务端完成事件协议改造后，前端按 `message.delta`、`tool.status`、`confirmation.required`、`message.done`、`error` 渲染；改造前以“处理中 → 一次性完整回答”降级展示。

## 6. 工程结构

```text
frontend/
├── src/
│   ├── api/
│   │   ├── http.ts                 # REST 实例、刷新队列、错误归一化
│   │   ├── contracts/              # OpenAPI 生成类型
│   │   ├── modules/                # auth/work-order/flow/dashboard/admin
│   │   └── assistant-stream.ts     # SSE 流解析与取消
│   ├── assets/
│   ├── components/
│   │   ├── business/               # StatusTag、StaffPicker、OrderTimeline
│   │   └── common/
│   ├── composables/
│   ├── constants/                  # 状态、类型、优先级、操作枚举
│   ├── layouts/
│   ├── router/
│   ├── stores/                     # auth、user、assistant
│   ├── styles/                     # tokens、reset、Element Plus 覆盖
│   ├── types/
│   ├── utils/
│   └── views/
├── e2e/
├── public/
├── .env.development
├── .env.production
├── vite.config.ts
└── package.json
```

页面数据原则上留在页面/composable 内，不把所有 API 数据塞进 Pinia。Pinia 仅用于：access token（内存）、当前用户、角色、应用级偏好，以及 Agent 当前会话索引。

## 7. API 与认证设计

### 7.1 REST 适配

建立统一的 `request<T>()` 层，但显式区分四类响应：

1. 普通业务接口：解包 `{ code, msg, data }`，`code !== 1` 转为业务异常。
2. `/api/auth/validate`：直接返回 `ValidateTokenResult`，不走 Result 解包。
3. `/api/auth/refresh`：继续服务 CLI 并保持当前 Result 契约；Web 改用新增的 `/api/auth/web/refresh` Cookie 接口。
4. `/workOrder/export`、`/workOrder/print`：按 Blob 处理，不执行 JSON 解包。

所有错误归一为 `{ kind, httpStatus, businessCode, message, traceId, fieldErrors }`。401 触发一次共享 refresh Promise，刷新成功后只重放幂等查询；写请求若无法确认服务端是否执行成功，不自动盲目重放，提示用户刷新数据核对。403 展示无权限，5xx/网络错误展示可重试提示。

OpenAPI 用于生成 TypeScript 类型，不建议第一阶段全自动生成请求实现，因为当前包装差异、Blob、SSE 和部分 Schema 精度仍需人工适配。CI 可下载 `/v3/api-docs` 做类型漂移检查。

### 7.2 Token 策略与链路兼容

本期允许调整 Web Cookie，但不能改变 CLI、Agent 和飞书的现有认证语义。建议采用“新增 Web 专用接口、保留原接口”的兼容方案：

| 调用方 | 登录/换证方式 | 本期处理 |
| --- | --- | --- |
| Vue Web | 新增 `/api/auth/web/login`、`/api/auth/web/refresh`、`/api/auth/web/logout` | Refresh Token 仅存在 HttpOnly Cookie，响应只返回 Access Token |
| Go CLI | 现有 `/user/login`、`/api/auth/refresh`、`/api/auth/logout` | 请求和响应保持不变，继续由 CLI 保存 Refresh Token |
| Agent Web | Vue 将 Access Token 作为 Bearer 传给 `/assistant/chat` | 保持现状；AiAssistant 继续调用 Backend `/api/auth/validate` |
| 飞书 Agent | AiAssistant 通过内部身份交换获得短期 `channel_proxy` Bearer Token | 保持现状；不使用 Web Cookie，也不依赖普通 Refresh Session |

Web 专用接口行为：

- `POST /api/auth/web/login` 校验账号密码、创建现有 Refresh Session，将 Refresh Token 写入 Cookie，响应返回 `tokenType/accessToken/accessTokenExpiresAt`，不返回 Refresh Token。
- `POST /api/auth/web/refresh` 从 Cookie 读取并轮换 Refresh Token，更新 Cookie，只返回新的 Access Token。
- `POST /api/auth/web/logout` 撤销对应 token family、清理 Cookie；可同时携带 Access Token 便于服务端核对当前用户。
- Access Token 仅保存在 Pinia/JS 内存；页面刷新后通过 Web refresh 接口恢复会话。
- Cookie 使用 `HttpOnly + SameSite=Lax`。当前本地 HTTP 配置 `Secure=false`；将来启用 HTTPS 后必须切换为 `Secure=true`。
- 本地 Vite 代理负责转发 `Set-Cookie`。本地可暂用 `Path=/`；未来反向代理上线后收紧到 Web 认证路径，并根据外部前缀重写 Cookie Path。
- 现有 `AuthTokenService.issue/refresh/logout` 和 Refresh Session 表继续复用，仅在 Controller 层增加 Cookie 适配，避免维护两套认证核心逻辑。

该方案不会影响 Agent 链路：Web Agent 仍拿到普通 Access Token；飞书仍使用独立的 `channel_proxy` Token；CLI 的 JSON Refresh Token 契约不删除、不改名。认证改造必须为四条链路分别增加回归测试。

### 7.3 权限

- 路由级：`meta.requiresAuth`、`meta.roles = ['ADMIN']`。
- 菜单级：根据 `/user/me` 返回角色隐藏管理员入口。
- 操作级：以后端返回 `allowedActions` 为准。
- 接口级：Spring Security 和业务服务必须继续校验；前端隐藏按钮不是安全边界。

## 8. Agent 事件协议建议

建议 AiAssistant 统一由服务端编码 SSE，每个事件均为 JSON，前端不解析混杂的裸文本：

```text
event: message.delta
data: {"requestId":"...","content":"正在查询"}

event: tool.status
data: {"requestId":"...","tool":"work_order_page","status":"running"}

event: confirmation.required
data: {"requestId":"...","summary":"将创建一条故障工单","confirmationId":"..."}

event: message.done
data: {"requestId":"...","content":"完整回答","usage":{}}

event: error
data: {"requestId":"...","code":"AGENT_TIMEOUT","message":"...","retryable":true}
```

同时增加 `X-Accel-Buffering: no`、合理的心跳事件和代理读取超时。若 LLM 客户端尚不支持 token 流，可先流式发送阶段事件，最终以一次 `message.done` 返回完整答案，也比长时间无反馈更可控。

## 9. 后端配套改造清单

### P0：一期联调前确认

| 项目 | 建议 |
| --- | --- |
| 同源接入 | 开发用 Vite proxy，生产用 Nginx/网关；无需给浏览器开放 `*` CORS |
| 认证存储 | 支持 HttpOnly Refresh Cookie；固定 401/403 响应结构 |
| 工单操作 | 详情增加 `allowedActions`，操作项包含动作类型、是否需人员、是否需备注 |
| 待办跳转 | `WorkOrderTodoVO` 增加工单 `id`、`code` |
| Agent | 固定 SSE 事件协议；至少提供阶段进度与 done/error 事件 |
| 流程权限 | 已确认按后端现状执行：所有登录用户可管理流程；前端菜单不限制为管理员 |

### P1：完整体验所需

| 项目 | 建议 |
| --- | --- |
| 附件 | 本期不建设上传；后续再增加上传、下载/签名、删除接口及安全扫描 |
| 消息中心 | 增加消息 ID、关联工单、已读状态、单条/全部已读接口 |
| 参数校验 | 控制器统一 `@Valid`，补充 `@NotBlank/@Size/@Min` 等约束 |
| API 契约 | 统一 Result、错误码、traceId；确保 OpenAPI 精确描述分页和二进制响应 |
| 搜索 | 明确 ES 不可用时的前端提示或数据库降级策略 |
| 会话 | 最好由 Agent 服务创建/列出会话 ID，而不是长期由浏览器自生成 |

### 安全问题

调研中发现仓库的 `AiAssistant/application.yaml` 包含看起来可用的 LLM API Key。它与前端建设无关，但应立即作废并轮换，改为环境变量或密钥管理注入，同时检查 Git 历史是否已包含该值。

## 10. 测试与验收

### 10.1 自动化测试

- 单元测试：响应解包、刷新并发队列、状态/操作映射、时间转换、SSE 分帧器。
- 组件测试：筛选表单、流程节点编辑、操作确认框、Agent 消息渲染。
- MSW 契约场景：成功、业务失败、401 刷新成功/失败、403、500、超时、异常 SSE、Blob 下载。
- Playwright 主链路：登录 → 创建工单 → 审批 → 派单 → 处理 → 验收；Agent 查询和写操作确认。管理员组织维护只做开发冒烟测试，不作为本期验收门槛。
- 可访问性：键盘操作、表单标签、焦点回收、状态不仅依赖颜色表达。

### 10.2 一期验收标准

- 普通用户的菜单、路由和工单操作符合权限；管理员页面不阻塞本期验收。
- Access Token 过期时单次刷新，多并发请求不会触发 refresh 风暴。
- 工单关键状态操作成功后，列表、详情、待办和看板数据一致刷新。
- 导出文件可正确命名下载，打印 PDF 可预览。
- Agent 长请求可停止；错误不会被渲染成普通回答；Markdown 不允许注入脚本。
- 主流桌面 Chrome/Edge 可用，1280px 及以上无关键内容遮挡。
- CI 必须通过 lint、vue-tsc、单测、构建和关键 E2E。

## 11. 实施阶段与工作量预估

以下为一名前端工程师、接口配套由后端并行完成时的粗估：

| 阶段 | 交付 | 估算 |
| --- | --- | ---: |
| 0. 契约确认 | OpenAPI、Cookie 兼容方案、DTO、SSE | 2～3 人日 |
| 1. 工程底座 | Vue 工程、布局、主题、路由、认证、请求层、Mock | 3～4 人日 |
| 2. 核心工单 | 看板、列表、详情、创建、流转、导出/打印 | 6～8 人日 |
| 3. 流程与组织 | 流程编辑、组织/员工选择、个人中心；管理员页面次优先实现 | 4～6 人日 |
| 4. Agent | 对话页/抽屉、流读取、确认、异常和会话清理 | 3～5 人日 |
| 5. 质量与交付 | E2E、兼容性、性能、本地启动与联调文档 | 2～3 人日 |

合计约 18～26 人日。附件上传已排除；管理员页面可以实现但不作为本期验收阻塞项。Nginx、域名、HTTPS 和正式容器部署不计入本期。若 Agent 仅做阶段反馈而不实现 token 级流式，可进一步压缩约 2～3 人日。

## 12. 官方选型依据

- [Vue Quick Start](https://vuejs.org/guide/quick-start.html)：官方脚手架使用 Vite，并可直接启用 TypeScript、Router、Pinia、Vitest 与 E2E。
- [Vue + TypeScript](https://vuejs.org/guide/typescript/overview)：Vue 对 TypeScript 提供一等支持，构建外需要使用 `vue-tsc` 做类型检查。
- [Vue Router](https://router.vuejs.org/guide/)：Vue 官方客户端路由方案。
- [Pinia](https://pinia.vuejs.org/introduction.html)：Vue 生态默认推荐状态管理方案。
- [Element Plus](https://element-plus.org/en-US/guide/installation.html)：面向 Vue 3 的组件库及浏览器兼容说明。
- [Node.js Releases](https://nodejs.org/en/about/previous-releases)：生产应使用 LTS；调研时 v24 为 LTS。
- [Vitest](https://vitest.dev/guide/features)：复用 Vite 配置、支持 TypeScript 和 Vue 组件测试。
- [Playwright](https://playwright.dev/docs/intro)：支持 Chromium、Firefox、WebKit 的端到端测试。
- [Apache ECharts 按需引入](https://echarts.apache.org/handbook/en/basics/import/)：可通过按需导入控制看板包体积。

## 13. 已确认的实施约束

1. 允许新增或扩展 DTO，但以向后兼容为原则：优先新增字段，不删除、不改名现有字段；同步检查 CLI Schema、Agent 工具输出和相关测试。
2. 流程管理沿用当前后端语义，所有已登录用户均可管理，不新增管理员限制。
3. 一期不上传附件，历史附件只读展示。
4. 允许调整 Web Cookie；采用新增 Web 专用认证接口的方式，现有 CLI、Agent、飞书认证链路保持不变。
5. PC 管理后台优先；管理员功能可以开发，但不纳入本期验收。
6. 视觉采用浅色主题，暂不做暗色主题。
7. 当前只做本地测试，没有 HTTPS、域名或 Nginx；开发环境使用 Vite proxy，生产部署配置延后。

### DTO 兼容规则

- `WorkOrderDetailVO` 增加 `allowedActions`、待办增加工单 `id/code` 等均采用可选新增字段，旧 CLI 和 Agent JSON 解析不受影响。
- 若 Agent 的命令 Schema 来自 CLI/CLI Service 元数据，新增字段后应更新 schema snapshot、golden set 和 token 预算测试，避免工具结果变大造成记忆窗口回归。
- 不在一期修改工单状态、类型、优先级和 HandleType 的既有数值；这些数值已经被 Backend、CLI 和 Agent Prompt 共同依赖。
- Web 专用认证响应使用新的 `WebAccessTokenResponse`，不修改现有 `AuthTokenResponse`，从类型层面防止 CLI 丢失 Refresh Token。

至此不存在阻塞工程搭建的产品问题，可以直接进入前端骨架、浅色主题、Web 专用认证接口和核心页面开发。
