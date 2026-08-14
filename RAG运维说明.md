# RAG 运维说明

## 状态与健康检查

- `GET /actuator/health`：查看整体健康状态和 `rag` 健康组件。
- `GET /actuator/rag`：查看 RAG 状态、构建版本、文档数、片段数及最近错误。
- `GET /actuator/metrics/rag.build.total`：查看构建成功和失败次数。
- `GET /actuator/metrics/rag.retrieval.total`：查看检索命中、空结果和异常次数。
- `GET /actuator/metrics/rag.answer.sanitization.total`：查看使用可信知识并执行出站引用清理的回答次数。

状态含义：

- `READY`：当前 Alias 已成功发布，可以正常检索。
- `BUILDING`：新索引正在后台构建；已有 Alias 仍可继续查询。
- `DEGRADED`：新构建失败，但旧索引仍可用。
- `FAILED`：没有可用索引。
- `DISABLED`：`llm.embedding.enabled=false`，RAG 已显式关闭。

## 运维操作

- `POST /actuator/ragrebuild`：异步构建并发布一个新索引版本。重复提交时返回 `accepted=false`。
- `POST /actuator/ragrollback`：将 Alias 切换到最近一个保留版本。
- `POST /actuator/ragevaluate`：运行 50 条 golden set，返回 Recall@K 和未命中问题。

管理端点应只暴露在受保护的管理网络或管理端口，不应直接开放到公网。

## 发布门禁

建议在知识库或嵌入模型发生变化后执行：

1. 请求 `rebuild`。
2. 等待状态变为 `READY`。
3. 请求 `evaluate`。
4. 确认 Recall@K 不低于 0.90，精确枚举问题无漏召回。
5. 若质量下降，立即执行 `rollback` 并检查未命中列表。

首次上线仍需在真实 Elasticsearch 环境验证 Alias 原子切换、Bulk 部分失败、回滚和旧索引保留行为。
