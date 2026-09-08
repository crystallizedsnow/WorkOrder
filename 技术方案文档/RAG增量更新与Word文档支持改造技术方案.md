# 工单 Agent RAG 增量更新与 Word 文档支持改造技术方案

## 1. 文档说明

本文面向 `AiAssistant` 现有 RAG 实现，给出以下四项改造的设计方案，不包含代码实现：

1. 由意图识别结果决定是否调用 RAG；
2. 通过接口手动触发文档检查，实现单文档增量更新、冲突确认、历史版本保留和指定版本回退；
3. 支持上传并解析 Word 文档；
4. 实现文档更新后的知识库评测、测试接口与发布门禁。

“自动更新”在本方案中的含义是：用户调用检查接口后，系统自动完成文件识别、内容比对、版本生成、冲突检测和更新计划生成；不采用定时扫描，也不在每次文件变化后自动重建全部索引。

## 2. 当前实现与主要问题

### 2.1 当前知识入库流程

当前流程为：

```text
classpath Markdown
  -> manifest.properties 可信来源校验
  -> DocumentSplitter 标题感知切分
  -> BGE 本地向量化
  -> 创建全新 Elasticsearch 物理索引
  -> Bulk 写入全部片段
  -> Alias 原子切换
```

现有方案具备全量发布、失败不影响旧索引和整库回滚能力，但存在以下限制：

- 只扫描 `**/*.md`，不支持上传 Word；
- 修改一份文件也会重新解析、向量化并写入全部知识文档；
- 文档版本由 `manifest.properties` 人工维护；
- 旧索引只能表达整库历史，不能管理单份文档的完整版本链；
- 只能回退整个索引，不能指定某份文档回退到某个版本；
- 文件仍是 classpath 资源，不适合作为运行时上传和更新的持久化载体。

### 2.2 当前意图识别如何决定

当前意图识别依次执行以下步骤：

1. 检查请求是否为确认执行、取消执行等控制协议；
2. 使用本地 embedding，将当前用户请求与 Skill Card 的描述、能力、正例和负例计算相似度；
3. 召回 Top 3 候选 Skill；embedding 不可用时退化为取注册表中的前几个 Skill；
4. 将当前请求、领域范围卡和候选 Skill Card交给意图模型；
5. 意图模型必须通过结构化 function call 返回以下类型之一：
   - `CONTROL_CONTINUATION`
   - `SYSTEM_HELP`
   - `KNOWLEDGE_QA`
   - `SKILL_EXECUTION`
   - `CLARIFY`
   - `OUT_OF_SCOPE`
6. 服务端策略层校验模型选择的 Skill 是否真实存在、是否来自候选集，以及多个 Skill 是否冲突；不合法时改判为 `CLARIFY`。

需要特别说明：当前配置为 `SHADOW`。`DefaultAgentGateway` 只异步记录路由结果，不使用该结果拦截请求或缩小工具集合。随后 `AgentLoop` 对每一个进入主循环的用户请求都执行一次 RAG 检索，检索发生在 ReAct 第一轮之前，而不是每一轮模型调用前执行。

因此，当前实际行为是“意图识别给出建议但不生效，所有正常请求都尝试检索 RAG”。

## 3. 总体设计

### 3.1 目标架构

```text
用户请求
  -> 意图识别与服务端策略
       -> 不需要 RAG：直接帮助回答 / Skill / 拒答 / 澄清
       -> 需要 RAG：查询上下文化 -> 按当前版本集合混合检索

管理端上传 Markdown/DOCX
  -> 临时文件与安全校验
  -> 格式解析
  -> 同名文件及内容哈希检查
  -> 生成更新计划
       -> 无冲突：确认发布或按策略直接发布
       -> 有冲突：等待用户显式确认
  -> 只切分和向量化变化文档
  -> 写入不可变历史版本
  -> 原子更新当前版本注册表
  -> 触发评测
```

### 3.2 核心原则

- 文档版本不可变：已发布版本不原地覆盖、不删除；
- 激活状态与文档内容分离：历史片段永久保存，由当前版本注册表决定哪些版本参与检索；
- 单文档增量：只处理新增或变化文档，不重新计算无关文档向量；
- 先检查、后应用：冲突检查和实际发布使用两个接口及一次性确认令牌；
- 同名识别但不只依赖文件名：稳定文档 ID 由知识空间和规范化文件名定位，内容哈希用于判断变化；
- 评测失败不自动污染生产：新版本先进入候选态，通过门禁后才激活；
- 管理操作必须鉴权、审计并支持幂等。

## 4. 功能一：由意图识别决定是否调用 RAG

### 4.1 路由决策规则

在 `RoutingDecision` 中增加显式字段：

```json
{
  "routeType": "KNOWLEDGE_QA",
  "ragMode": "REQUIRED",
  "ragQuery": "工单状态 200 的含义",
  "reasonCode": "DOMAIN_KNOWLEDGE_REQUIRED"
}
```

`ragMode` 只允许：

| 值 | 含义 |
|---|---|
| `NEVER` | 禁止调用 RAG |
| `OPTIONAL` | Skill 执行需要领域解释时允许调用 |
| `REQUIRED` | 回答必须以知识库证据为依据 |

服务端最终决策不能完全相信模型输出，必须根据 `routeType` 和 Skill 配置重新计算：

| 意图类型 | RAG 策略 | 处理方式 |
|---|---|---|
| `KNOWLEDGE_QA` | `REQUIRED` | 检索成功后回答；无证据时明确告知未找到可信依据 |
| `SKILL_EXECUTION` | 默认 `NEVER` | 实时数据通过 CLI/API 获取；仅当 Skill Card 声明 `ragPolicy=OPTIONAL/REQUIRED` 时检索 |
| `SYSTEM_HELP` | `NEVER` | 使用系统能力说明，不查询业务知识库 |
| `CONTROL_CONTINUATION` | `NEVER` | 延续确认或取消协议，不检索 |
| `CLARIFY` | `NEVER` | 直接询问缺失信息，避免用检索内容猜测意图 |
| `OUT_OF_SCOPE` | `NEVER` | 返回范围提示，不进入 RAG 和主 Agent |

多意图请求中，只要存在经过策略验证的 `KNOWLEDGE_QA`，或者某个已允许 Skill 明确要求知识证据，本次请求才调用 RAG。RAG 命中结果不能反向改变意图，也不能把越权请求变成允许执行的请求。

### 4.2 调用链改造

`DefaultAgentGateway` 应从“影子观察后无条件进入 AgentLoop”改为：

```text
确认协议处理
  -> IntentRouter.route
  -> IntentRoutingPolicy 强校验
  -> 按 routeType 分流
       KNOWLEDGE_QA / 需要知识的 Skill -> 构造 RagRequest
       其他类型                    -> 不创建 RagRequest
  -> AgentLoop
```

`AgentLoop` 不再自行决定是否检索，只消费入口传入的、已经过服务端校验的 `RagDirective`。这样可以避免模型通过提示注入自行开启 RAG。

建议的结构：

```java
record RagDirective(
    RagMode mode,
    String query,
    Set<String> allowedKnowledgeSpaces,
    Set<TrustLevel> allowedTrustLevels
) {}
```

### 4.3 检索频率

首期维持“每个用户请求最多主动检索一次”，而不是每个 ReAct 轮次检索：

- 使用当前问题和最近两条用户消息生成检索问题；
- 检索结果在本次 Agent 执行期间只读复用；
- 若工具结果产生了新的知识问题，模型不能直接任意检索，应由受限的 `searchKnowledge` 工具发起补充检索；该工具仍需检查当前路由是否允许 RAG，并限制调用次数和知识空间。

### 4.4 异常策略

- 意图路由失败：默认不调用 RAG，返回可重试或澄清提示；不能无条件放行；
- `REQUIRED` 且 RAG 不可用：明确返回知识库暂不可用；
- `REQUIRED` 但无可信命中：明确返回未找到依据；
- `OPTIONAL` 检索失败：Skill 可继续执行，但不得把模型常识描述成项目规则；
- `NEVER`：即使模型提出知识检索工具调用，也由工具分发层拒绝。

### 4.5 上线方式

1. 保持现有 `SHADOW`，补齐“应检索/不应检索”评测集；
2. 增加 `COMPARE` 指标，对比旧的全量检索与新决策；
3. 对小流量开启 `ENFORCE`；
4. 达到门禁后全量启用；
5. 保留紧急开关，可临时退回 `SHADOW`，但不建议退回“所有请求均检索”。

## 5. 功能二：接口触发的单文档增量更新

### 5.1 存储模型调整

classpath 仅保留初始种子知识。运行时上传的原始文件保存在可配置的持久化目录中，元数据及评测报告通过临时文件加原子替换的方式写入 `catalog.json`，Elasticsearch 保存可检索片段。这是本期面向单实例部署的实现；后续扩展到多实例时，可在保持下述逻辑模型和接口不变的前提下，将目录迁移到 MongoDB、将原文件迁移到对象存储。

#### 5.1.1 文档主表 `knowledge_document`

| 字段 | 说明 |
|---|---|
| `documentId` | 稳定逻辑文档 ID |
| `knowledgeSpace` | 知识空间，如 `workorder` |
| `normalizedFileName` | Unicode 归一化、去首尾空格并统一大小写后的文件名 |
| `displayName` | 展示名称 |
| `activeRevisionId` | 当前生产生效版本 |
| `latestRevisionId` | 最新上传或生成版本，不一定已生效 |
| `owner` | 责任人 |
| `trustLevel` | 可信等级 |
| `lockVersion` | 乐观锁版本 |
| `createdAt/updatedAt` | 记录时间 |

唯一约束：`knowledgeSpace + normalizedFileName`。同一知识空间内，同名文件被识别为同一逻辑文档。

#### 5.1.2 文档版本表 `knowledge_revision`

| 字段 | 说明 |
|---|---|
| `revisionId` | 不可变版本 ID |
| `documentId` | 所属逻辑文档 |
| `versionNo` | 服务端单调递增版本号，如 `7` |
| `versionLabel` | 展示版本，如 `2026.09.04.1`，可由用户填写或系统生成 |
| `contentHash` | 规范化内容 SHA-256 |
| `binaryHash` | 原文件 SHA-256 |
| `format` | `MARKDOWN` 或 `DOCX` |
| `storageKey` | 原文件持久化位置 |
| `parserVersion` | 解析器版本 |
| `chunkingVersion` | 分片算法版本 |
| `embeddingModel` | 向量模型及修订版本 |
| `chunkCount` | 片段数 |
| `status` | `STAGED/EVALUATING/ACTIVE/INACTIVE/REJECTED/FAILED` |
| `createdBy/createdAt` | 上传审计信息 |
| `activatedBy/activatedAt` | 生效审计信息 |

历史版本不覆盖。`versionLabel` 在同一 `documentId` 下唯一；同一标签对应不同内容哈希属于版本冲突，禁止直接发布。

#### 5.1.3 ES 片段字段

在现有字段上增加：

```text
documentId
revisionId
versionNo
versionLabel
contentHash
format
chunkOrdinal
parserVersion
chunkingVersion
embeddingModel
createdAt
```

片段 `_id` 使用 `revisionId + chunkOrdinal + chunkContentHash` 生成。相同历史版本重复写入必须幂等。

### 5.2 当前版本注册表

维护一个 `ActiveRevisionRegistry`：

```text
documentId -> activeRevisionId
```

发布或回退时，只原子更新某个文档的当前版本指针。检索器读取注册表快照，把所有当前 `revisionId` 作为 Elasticsearch 过滤条件，同时用于 BM25 和 KNN 查询。本期由持久化 `catalog.json` 恢复并在进程内维护注册表；多实例部署时再迁移到 MongoDB，并在变更后主动失效各实例缓存。

这样新增或更新一份文档时：

- 不修改其他文档；
- 不重新生成其他文档向量；
- 旧版本仍在 ES 中，但不会被生产检索命中；
- 指定版本回退只需切换该文档的 `activeRevisionId`；
- 每次回答可记录本次使用的注册表版本，便于复现。

知识库规模非常大、当前版本 ID 数量达到 ES terms 查询限制时，再将注册表升级为 ES terms lookup 或按知识空间拆分注册表；首期无需增加这一复杂度。

### 5.3 检查与更新接口

管理接口不继续放在公开 Actuator 写端点，建议放到受管理权限保护的 `/api/admin/knowledge-bases`。

#### 5.3.1 上传并检查

```http
POST /api/admin/knowledge-bases/{space}/documents:check
Content-Type: multipart/form-data

file=<binary>
versionLabel=<optional>
owner=<required>
trustLevel=<required>
```

接口执行：

1. 校验文件类型、扩展名、MIME、大小和安全限制；
2. 计算原文件哈希；
3. 解析并规范化正文，计算内容哈希；
4. 按 `space + normalizedFileName` 查找逻辑文档；
5. 比较活动版本、最新版本和待上传内容；
6. 生成更新计划和有时效的一次性 `confirmationToken`；
7. 暂存原文件和解析结果，不写入生产向量索引。

返回示例：

```json
{
  "checkId": "chk_123",
  "documentId": "doc_456",
  "normalizedFileName": "work-order-process.docx",
  "result": "CONTENT_CHANGED",
  "requiresConfirmation": true,
  "currentVersion": "2026.08.14",
  "proposedVersion": "2026.09.04.1",
  "currentHash": "...",
  "proposedHash": "...",
  "summary": {
    "addedSections": 2,
    "changedSections": 3,
    "deletedSections": 1
  },
  "confirmationToken": "...",
  "expiresAt": "2026-09-04T12:10:00Z"
}
```

#### 5.3.2 确认并创建候选版本

```http
POST /api/admin/knowledge-bases/{space}/documents/{documentId}:apply
Idempotency-Key: <uuid>

{
  "checkId": "chk_123",
  "confirmationToken": "...",
  "confirmed": true,
  "expectedActiveRevisionId": "rev_old"
}
```

`apply` 必须重新检查：令牌未过期、调用人一致、暂存文件哈希一致、活动版本没有被其他人修改。成功后只对该候选文档进行分片和向量化，写入不可变历史片段，状态进入 `EVALUATING`。

如果用户拒绝确认，调用同一接口传 `confirmed=false`，候选记录变成 `REJECTED` 并清理暂存文件。

#### 5.3.3 发布候选版本

```http
POST /api/admin/knowledge-bases/{space}/documents/{documentId}/revisions/{revisionId}:activate
```

评测达到门禁后才能激活。激活操作通过乐观锁切换 `activeRevisionId`，旧活动版本变为 `INACTIVE`，但文件、解析内容和向量全部保留。

可提供配置：

```yaml
knowledge-base:
  update:
    require-evaluation-before-activate: true
    allow-auto-activate-when-evaluation-passed: false
```

默认建议评测通过后仍由管理员显式发布，避免评测集覆盖不足时自动上线错误规则。

### 5.4 冲突定义与处理

| 场景 | 检查结果 | 是否确认 | 处理 |
|---|---|---:|---|
| 新文件名、新内容 | `NEW_DOCUMENT` | 可配置，默认是 | 创建逻辑文档和首个候选版本 |
| 同名且内容哈希相同 | `NO_CHANGE` | 否 | 幂等返回，不生成版本、不向量化 |
| 同名但内容变化 | `CONTENT_CHANGED` | 是 | 展示版本差异，确认后生成新版本 |
| 同一版本标签、相同哈希 | `VERSION_ALREADY_EXISTS` | 否 | 返回已有版本 |
| 同一版本标签、不同哈希 | `VERSION_LABEL_CONFLICT` | 是，但不能覆盖 | 用户需采用新版本标签或取消 |
| 文件名不同、内容哈希相同 | `DUPLICATE_CONTENT` | 是 | 可取消、作为独立文档导入或关联已有文档 |
| 检查后活动版本被他人更新 | `CONCURRENT_UPDATE` | 是，需重新检查 | 原令牌失效，不能沿用旧确认 |
| 解析后无有效正文 | `EMPTY_CONTENT` | 不适用 | 拒绝更新 |

“用户确认”必须是对确定内容的确认，令牌应绑定：用户、知识空间、文档 ID、旧活动版本、新文件哈希、建议版本、检查时间和过期时间。任何绑定字段变化均需重新检查，不能只靠前端弹窗状态。

### 5.5 指定版本回退

查询版本：

```http
GET /api/admin/knowledge-bases/{space}/documents/{documentId}/revisions
```

检查回退：

```http
POST /api/admin/knowledge-bases/{space}/documents/{documentId}/revisions/{revisionId}:rollback-check
```

确认回退：

```http
POST /api/admin/knowledge-bases/{space}/documents/{documentId}/revisions/{revisionId}:rollback

{
  "confirmationToken": "...",
  "expectedActiveRevisionId": "rev_current"
}
```

回退不删除“较新”版本，也不重新向量化目标版本，只切换活动指针。回退同样需要确认、乐观锁和审计记录。目标版本必须满足：片段完整、向量模型兼容、可信等级可发布；不兼容时需要先迁移向量，不能强行激活。

### 5.6 自动版本生成

用户未填写 `versionLabel` 时，由服务端按北京时间生成：

```text
yyyy.MM.dd.sequence
```

例如：`2026.09.04.1`、`2026.09.04.2`。真正用于排序和并发控制的是单调递增的 `versionNo`，不能用字符串版本或文件修改时间判断新旧。

内容变化但用户提交了与历史相同的版本标签时，不允许覆盖。文件系统 `lastModified` 只作为展示信息，不作为版本事实来源。

### 5.7 兼容全量重建

保留全量重建能力，但用途调整为：

- embedding 模型或维度变化；
- `chunkingVersion` 变化；
- ES mapping 变化；
- 历史数据修复；
- 灾难恢复。

普通知识内容更新禁止调用全量重建。全量迁移完成后仍以 `ActiveRevisionRegistry` 决定当前版本。

## 6. 功能三：支持 Word 文档上传

### 6.1 支持范围

首期只支持 `.docx`，不支持旧二进制 `.doc`：

- `.docx` 可以使用 Apache POI `XWPF` 解析；
- `.doc` 格式复杂且安全风险更高，可提示用户另存为 `.docx`；
- 图片型 Word、嵌入 PDF 和文本框首期不做 OCR；检测到正文过少时返回可解释的解析警告。

本期必须同时交付一份可用于联调和验收的工单系统知识库 Word 文档：`计算机运维自助排障知识库.docx`。文档内容覆盖员工可安全自行处理的常见计算机、网络、VPN、账号、办公应用、打印和磁盘问题；每个主题必须包含“适用现象、自助步骤、验证方式、停止自助并创建工单的条件、创建工单时应提供的信息”。安全事件、疑似硬件损坏、数据丢失、权限审批和多用户影响等场景不得引导用户继续自行操作。

该文档既是首批生产候选知识，也是 DOCX 解析能力的验收样本，至少用于验证：多级标题、编号步骤、项目符号、提示框和表格解析。首次导入仍须经过上传检查、冲突确认、评测和激活流程，不能因其随代码交付而绕过知识发布门禁。

### 6.2 统一解析接口

引入格式无关的解析层：

```java
interface KnowledgeDocumentParser {
    boolean supports(FileDescriptor file);
    ParsedDocument parse(InputStream input, ParseOptions options);
}
```

输出统一为：

```text
ParsedDocument
  - title
  - normalizedText
  - sections[]
      - headingPath
      - blockType
      - text
      - sourceLocator
  - warnings[]
  - parserVersion
```

Markdown 和 Word 都先转为该结构，再进入统一分片器，避免为不同文件类型维护两套检索逻辑。

### 6.3 DOCX 解析规则

- `Title`、`Heading 1`～`Heading 6` 转为章节路径；
- 普通段落作为自然段；
- 有序和无序列表保留序号及层级；
- 表格按“表名/章节 + 表头 + 当前行”组织，每个分片重复必要表头；
- 页眉、页脚、批注、修订记录默认不作为正文，可在上传检查结果中提示；
- 超链接保留展示文本和规范化 URL；
- 图片只记录占位和替代文本，首期不做视觉理解；
- 空段、样式噪声和重复空白在计算 `contentHash` 前规范化；
- `sourceLocator` 优先记录章节和块序号。DOCX 的逻辑页码受排版环境影响，不作为稳定引用位置。

分片仍优先在标题、段落、列表项和表格行边界进行；超过限制后再按句子和 token 切分。建议把当前“1200字符/160字符重叠”升级为“最大450 tokens、重叠约60 tokens”，具体值由评测结果确定。

### 6.4 上传安全

- 同时校验扩展名、MIME 和 ZIP/DOCX 文件签名；
- 限制原文件大小、解压后大小、压缩比、段落数、表格单元格数和解析时间；
- 防止 ZIP Bomb、路径穿越和外部实体解析；
- 不执行宏、OLE对象或任何嵌入脚本；
- 原文件进入隔离存储，解析成功并确认后再转入正式存储；
- 文件名只用于显示和同名匹配，不直接拼接成本地路径；
- 保存上传人、文件哈希、IP/渠道、检查结果和确认记录。

## 7. 功能四：文档更新后的知识库评测与测试功能

本功能纳入本期实现范围。候选文档版本创建后必须能够通过接口执行 Baseline/Candidate 对比评测、查询评测结果，并依据评测门禁决定是否允许激活。除自动化单元测试和集成测试外，还应在 Elasticsearch、嵌入模型和智谱 LLM 可用的环境中执行一次端到端验证。

### 7.1 评测目标

每次候选版本创建后，需要回答四个问题：

1. 新增或修改知识是否能够被正确召回；
2. 原有关键问题是否发生回归；
3. 已删除或废弃内容是否仍被错误召回；
4. 最终回答是否忠于当前生效版本并具有正确来源。

### 7.2 数据集组成

#### 固定回归集

保留现有 golden set，并扩展覆盖：

- 状态、类型、权限、SLA、流程；
- 精确枚举和易混淆枚举；
- 同义表达、错别字和多轮省略；
- 无答案问题；
- 越权、无关和提示注入问题；
- 应调用 RAG 与不应调用 RAG 的意图路由样例。

#### 文档变更集

每次更新要求维护者为变化内容提供：

- 至少3个正向问题；
- 至少1个相近但答案不同的问题；
- 删除内容时至少1个“旧答案不得出现”的问题；
- 涉及枚举、权限或SLA时提供精确期望答案。

系统可以根据文档差异生成候选问题，但必须由维护者确认后才能进入正式评测集。

### 7.3 双版本对比

评测时同时运行：

```text
Baseline：当前 ACTIVE 版本集合
Candidate：仅把待更新 documentId 替换为候选 revisionId
```

其他文档版本、embedding模型、分片参数、检索参数和生成模型必须保持一致，从而把差异归因于本次文档更新。

### 7.4 评测层次与指标

#### 意图路由层

- RAG调用准确率；
- `KNOWLEDGE_QA` 召回率；
- 不必要RAG调用率；
- `OUT_OF_SCOPE` 和 `CLARIFY` 准确率；
- RAG路由失败时的安全降级率。

#### 检索层

- Recall@5；
- MRR@5；
- nDCG@5；
- 无答案拒检准确率；
- 新版本命中率；
- 旧版本泄漏率；
- P50/P95检索延迟。

“旧版本泄漏”定义为：Candidate 环境回答当前问题时，检索结果中出现该文档非活动 `revisionId`。目标必须为0。

#### 回答层

- 答案正确性；
- 证据忠实度；
- 引用完整率和引用正确率；
- 被删除规则残留率；
- 无依据时的拒答正确率；
- 与实时工具结果冲突时是否以实时状态为准。

答案评测采用“确定性规则优先，LLM Judge 辅助”：枚举、数值、权限、版本和引用使用程序断言；开放说明类问题可由 Judge 评分，但抽样人工复核。

### 7.5 建议发布门禁

| 指标 | 建议门禁 |
|---|---:|
| 变更集 Recall@5 | 100% |
| 全量固定集 Recall@5 | 不低于基线，且不低于90% |
| 精确枚举/权限/SLA正确率 | 100% |
| 旧版本泄漏率 | 0 |
| 无答案拒答正确率 | 不低于95% |
| 引用正确率 | 100% |
| P95检索延迟 | 相对基线增长不超过20% |

任何强门禁失败，候选版本保持 `EVALUATING` 或转为 `FAILED`，不得切换活动指针。管理员可以查看失败样例、修改文档后创建新版本，或者明确记录例外审批；不允许覆盖原候选版本。

### 7.6 评测报告

每次评测生成不可变报告：

```text
evaluationId
documentId / candidateRevisionId
baselineRegistryVersion / candidateRegistryVersion
datasetVersion
embeddingModel / chatModel
parserVersion / chunkingVersion
retrievalParameters
各项指标
失败样例及命中片段
执行人、开始时间、结束时间
最终结论 PASS / FAIL
```

激活记录必须关联通过的 `evaluationId`，确保以后可以解释“某个文档版本为何被发布”。

### 7.7 本期测试接口

```http
POST /api/admin/knowledge-bases/{space}/documents/{documentId}/revisions/{revisionId}:evaluate
GET  /api/admin/knowledge-bases/{space}/evaluations/{evaluationId}
POST /api/admin/knowledge-bases/{space}/evaluations/{evaluationId}:activate
```

`evaluate` 支持两类输入：一是服务端固定回归集；二是上传检查或管理员提交的文档变更集。接口返回 `evaluationId`，评测可同步完成或进入后台任务。结果必须至少包含路由是否应调用RAG、候选版本 Recall@K、基线对比、旧版本泄漏、失败样例和是否通过门禁。

本期自动化测试至少覆盖：

- 意图类型与 `ragMode` 决策矩阵；
- `NEVER` 时不调用检索器，`REQUIRED` 时调用一次；
- Markdown 与 DOCX 标题、段落、列表、表格解析；
- 同名同内容幂等、同名内容变化冲突、版本标签冲突；
- 确认令牌过期、调用人变化、内容哈希变化和并发更新；
- 单文档更新不重新向量化其他文档；
- Candidate 检索只替换目标文档版本；
- 指定历史版本回退及旧版本不泄漏；
- 评测不通过禁止激活，评测通过允许激活；
- 智谱 LLM 端到端问答能够命中新 Word 文档，非知识问答不会调用RAG。

## 8. 一致性、并发与审计

### 8.1 状态机

```text
上传检查 -> STAGED -> EVALUATING -> ACTIVE
                 |          |
                 |          -> FAILED
                 -> REJECTED

ACTIVE --新版本激活--> INACTIVE
INACTIVE --指定回退--> ACTIVE
```

一个逻辑文档同一时刻只能有一个 `ACTIVE` 版本。激活事务至少需要同时完成：

1. 校验候选版本完整；
2. 使用 `expectedActiveRevisionId` 和 `lockVersion` 做并发检查；
3. 更新活动指针及注册表版本；
4. 写入 Outbox 事件；
5. 事务提交后刷新检索缓存。

如果 ES 片段写入成功但元数据事务失败，新版本仍是不可检索的孤立候选，可由后台清理或重试，不影响当前版本。

### 8.2 审计事件

至少记录：

- `DOCUMENT_CHECKED`
- `CONFLICT_CONFIRMED/CANCELLED`
- `REVISION_CREATED`
- `EVALUATION_STARTED/PASSED/FAILED`
- `REVISION_ACTIVATED`
- `REVISION_ROLLED_BACK`
- `REVISION_MIGRATED`

日志记录 ID、哈希、版本和操作者，不记录完整文档正文、向量或用户敏感问题。

## 9. 配置建议

```yaml
knowledge-base:
  storage:
    type: filesystem
    root: ./knowledge-storage
  upload:
    allowed-types: [MARKDOWN, DOCX]
    max-file-size: 20MB
    staging-ttl: PT30M
  update:
    require-confirmation-on-new-document: true
    require-confirmation-on-content-change: true
    require-evaluation-before-activate: true
  revision-registry:
    cache-ttl: PT1M
  chunking:
    version: structured-token-v1
    max-tokens: 450
    overlap-tokens: 60
```

生产环境应使用持久化卷或对象存储；`./knowledge-storage` 只适合本地开发。

## 10. 建议实施阶段

### 阶段一：路由真正生效

- 增加 `ragMode` 和服务端决策矩阵；
- 将 RAG 调用从 `AgentLoop` 前置到路由编排层；
- 补充是否调用 RAG 的评测集；
- 从 `SHADOW` 灰度到 `ENFORCE`。

### 阶段二：版本模型与增量索引

- 建立文档、版本、检查任务、评测和审计数据模型；
- 建立 `ActiveRevisionRegistry`；
- 实现检查、确认、增量写入、激活和指定回退接口；
- 将普通更新与全量迁移入口分离。

### 阶段三：Word 上传

- 引入统一解析接口；
- 实现 Markdown 与 DOCX 解析器；
- 增加上传安全限制、表格分片和解析预览；
- 使用真实业务 Word 样本验证格式保真度。

### 阶段四：评测和发布门禁

- 建立固定回归集与变更集；
- 支持 Baseline/Candidate 双版本运行；
- 生成评测报告并绑定激活操作；
- 增加失败阻断、人工审批和监控告警。
- 实现评测执行、结果查询和按评测结果激活接口；
- 增加自动化测试，并在真实依赖可用时执行智谱 LLM 端到端测试。

## 11. 验收标准

1. `SYSTEM_HELP`、`OUT_OF_SCOPE`、`CLARIFY` 和普通控制请求不会调用知识库；
2. `KNOWLEDGE_QA` 必须在取得可信证据后回答，无证据时不会猜测；
3. 更新一份文件时，只对该文件的新版本执行解析、分片和向量化；
4. 同名同内容重复上传是幂等操作，不产生新版本；
5. 同名内容变化必须返回差异并经过显式确认；
6. 冲突确认令牌过期、内容变化或并发版本变化后不能继续使用；
7. 原版本文件、元数据和向量均被保留；
8. 可以查看版本列表并指定任意兼容历史版本回退；
9. 生产检索只命中当前活动版本，旧版本泄漏率为0；
10. `.docx` 的标题、正文、列表和表格可以正确解析和检索；
11. 候选版本未通过评测门禁时不能激活；
12. 所有检查、确认、激活和回退操作均可审计和复现。

## 12. 不在本期范围

- 定时目录扫描、文件系统监听和第三方网盘自动同步；
- `.doc`、PDF、Excel、图片OCR和多媒体内容理解；
- 面向普通用户的通用知识库管理平台；
- 无人审核情况下自动生成评测问题并直接发布；
- 删除历史版本。历史清理策略应在合规和保留周期明确后单独设计。
