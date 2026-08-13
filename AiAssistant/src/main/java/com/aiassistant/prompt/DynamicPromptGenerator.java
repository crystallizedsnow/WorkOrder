package com.aiassistant.prompt;

import com.aiassistant.agent.ToolDispatcher;
import com.aiassistant.loader.SkillLoader;
import com.aiassistant.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 动态系统提示词生成器。
 * <p>
 * 将工具、技能以结构化 JSON 注入提示词，todo 工具并入 tools 列表，
 * ReAct 流程附带输入输出 JSON 示例，最终回答采用 thinking 标签方案
 * （LLM 在 &lt;thinking&gt;...&lt;/thinking&gt; 内推理，答案在标签外，
 * 系统解析时剥离 thinking 部分，仅暴露答案给用户）。
 */
@Component
@Slf4j
public class DynamicPromptGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private ToolDispatcher toolDispatcher;

    private String basePrompt;

    @PostConstruct
    public void initialize() throws IOException {
        loadBasePrompt();
    }

    private void loadBasePrompt() throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource("workOrder-prompt-template.txt");
            if (resource.exists()) {
                basePrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } else {
                basePrompt = getDefaultPrompt();
            }
        } catch (IOException e) {
            log.warn("加载提示词模板失败，使用默认提示词: {}", e.getMessage());
            basePrompt = getDefaultPrompt();
        }
    }

    public String generate() {
        String prompt = basePrompt;

        prompt = prompt.replace("{{TOOLS}}", buildToolsSection());
        prompt = prompt.replace("{{SKILLS}}", buildSkillsSection());
        prompt = prompt.replace("{{SKILL_CONTENT}}", buildSkillContentSection());
        prompt = prompt.replace("{{REACT_FORMAT}}", buildReActSection());
        prompt = prompt.replace("{{OUTPUT_FORMAT}}", buildOutputFormatSection());

        return prompt;
    }

    private String buildSkillContentSection() {
        try {
            Map<String, SkillLoader.SkillMetadata> skillMap = getSkillMap();
            if (skillMap.isEmpty()) {
                return "暂无已加载技能文档\n";
            }

            StringBuilder sb = new StringBuilder();
            for (String skillKey : skillMap.keySet()) {
                sb.append("\n### ").append(skillKey).append("\n");
                sb.append(skillLoader.loadSkillContent(skillKey)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("注入技能全文失败: {}", e.getMessage());
            return "技能全文加载失败，请调用 loadSkill 获取完整技能文档。\n";
        }
    }

    /**
     * 以 JSON 数组格式输出全部可用工具（含 todo 系列工具，不再单独列出）。
     * 每项结构：{"name":"工具名","description":"描述","parameters":{JSON Schema}}
     */
    private String buildToolsSection() {
        try {
            List<ToolDefinition> defs = toolDispatcher.getToolDefinitions();
            if (defs == null || defs.isEmpty()) {
                return "[]\n";
            }
            ArrayNode arr = MAPPER.createArrayNode();
            for (ToolDefinition def : defs) {
                ObjectNode item = arr.addObject();
                if (def.getFunction() != null) {
                    item.put("name", def.getFunction().getName());
                    item.put("description", def.getFunction().getDescription());
                    if (def.getFunction().getParameters() != null) {
                        item.set("parameters", def.getFunction().getParameters());
                    } else {
                        item.putObject("parameters");
                    }
                }
            }
            return MAPPER.writeValueAsString(arr);
        } catch (Exception e) {
            log.warn("序列化工具列表为 JSON 失败: {}", e.getMessage());
            return "[]\n";
        }
    }

    /**
     * 以 JSON 数组格式输出全部可用技能元数据。
     * 每项结构：{"key":"技能键","name":"展示名","description":"描述","type":"类型","version":"版本"}
     */
    private String buildSkillsSection() {
        try {
            Map<String, SkillLoader.SkillMetadata> skillMap = getSkillMap();
            if (skillMap.isEmpty()) {
                return "[]\n";
            }
            ArrayNode arr = MAPPER.createArrayNode();
            for (Map.Entry<String, SkillLoader.SkillMetadata> entry : skillMap.entrySet()) {
                String skillKey = entry.getKey();
                SkillLoader.SkillMetadata meta = entry.getValue();
                ObjectNode item = arr.addObject();
                item.put("key", skillKey);
                item.put("name", meta.getName() != null ? meta.getName() : skillKey);
                item.put("description", meta.getDescription() != null ? meta.getDescription() : "");
                item.put("type", meta.getType() != null ? meta.getType() : "");
                item.put("version", meta.getVersion() != null ? meta.getVersion() : "");
            }
            return MAPPER.writeValueAsString(arr);
        } catch (Exception e) {
            log.warn("序列化技能列表为 JSON 失败: {}", e.getMessage());
            return "[]\n";
        }
    }

    /**
     * ReAct 工具调用流程，附输入输出 JSON 示例。
     */
    private String buildReActSection() {
        return """
            ## 工具调用与思考流程（function calling，全程结构化 JSON）
            系统已通过上方 TOOLS JSON 数组向你提供可用工具（含 todo 系列任务管理工具），
            每个工具的名称、描述、参数 schema 均已结构化注入。
            所有输入输出均为结构化 JSON，禁止在正文中用自然语言描述工具调用计划或参数。

            ### 流程
            1. 思考：在 <thinking>...</thinking> 标签内分析用户需求，决定是否需要调用工具及调用哪个工具
            2. 行动/调用：通过 tool_calls 字段结构化发起调用，arguments 须为合法 JSON 且字段名与 schema 一致
            3. 接收：工具结果会以 role=tool 消息结构化回传（含 tool/success/result 字段）
            4. 总结：根据工具结果继续思考或给出最终回答

            ### 结构化规则
            - 工具调用：必须通过响应的 tool_calls 数组发起，禁止用自然语言描述
            - 工具参数：必须是合法 JSON 对象，字段名与 schema 严格一致，类型匹配（string/integer/boolean/array）
            - 工具结果：以 role=tool 消息回传，结构为 {"tool":"工具名","success":true/false,"result":"执行结果"}
            - 一次可调用一个或多个工具，调用后须等待工具结果再决定下一步
            - 不需要工具时，按下方"最终回答格式"返回

            ### 长任务编排与 todo（Agent 通用能力）
            - 当请求包含三个及以上相互依赖的业务阶段，或前一步结果会为后一步提供 ID、文件、查询条件等输入时，判定为长任务
            - 长任务在调用任何业务工具前，必须先调用 `todoWrite`，为每个可验证的业务阶段分别建立任务；不要只在正文或 thinking 中列计划
            - 开始执行某阶段前调用 `todoUpdate` 标记 `in_progress`，拿到满足该阶段目标的结果后调用 `todoUpdate` 标记 `completed`
            - 用户确认、补充信息等等待点不会取消 todo；下一轮应读取并继续未完成任务，不能遗忘原始长任务目标
            - 用户只要求查询或说明后续写操作时，完成查询和说明对应的 todo，但不得替用户执行该写操作
            - 单一查询、单一写操作或两个无依赖的小步骤不创建 todo，避免过度拆分
            - todo 只记录和恢复任务进度，不提供业务授权，也不能改变已加载 SKILL.md 的步骤顺序；领域 skill 的 Schema、dry-run、用户确认和终止点始终优先
            - 某阶段到达 skill 规定的用户确认点时，最多更新 todo 状态，随后必须停止并等待用户；不得因为还有未完成 todo 就越过确认点
            - 确认执行某个写阶段后，该轮必须在返回执行结果后停止；剩余 todo 留到用户下一轮继续

            ### 工具失败处理（重要）
            - 当工具结果 success=false 时，表示工具执行失败，必须检查 result 中的错误信息
            - 根据错误类型参考已加载的技能文档（SKILL.md）中的错误处理流程
            - CLI 命令返回 code=401（认证失败）时立即停止，提示调用方重新登录并携带新的 Bearer Access Token；禁止读取账号文件、调用 login、刷新 Token 或索要密码
            - 当前请求的用户 Access Token 已由系统隔离注入 CLI 子进程；任何工具命令都不得包含 login、auth login、--password 或 --token
            - 不要把可恢复的参数错误直接转述给用户，应先按技能文档重新检查运行时 Schema；认证错误除外，认证错误必须停止本轮

            ### 技能工作流纪律（重要）
            - 已加载的 SKILL.md 是当前任务的可执行操作规程；当用户请求命中某个技能时，必须完整遵循该技能中的步骤、前置条件、确认点、参数规范和错误处理流程
            - 不要把中间工具结果当作最终完成；只有当 SKILL.md 要求的全部必要步骤已经完成，或 SKILL.md 明确要求停下来等待用户确认/补充信息时，才给最终回答
            - 如果 SKILL.md 要求某个工具结果必须原样展示给用户，则最终回答必须保留该工具结果原文，不要改写成摘要
            - 如果 SKILL.md 要求等待用户确认，则在确认前必须停止后续工具调用；用户确认、取消或修改后，再按同一 SKILL.md 的后续分支继续

            ### 输入输出 JSON 示例

            #### 示例 1：单工具调用
            用户问：查询工单 WO202506191935695546618613760 的状态
            模型响应（assistant 消息）：
            {
              "content": "<thinking>用户要查工单状态，调用 getWorkOrderDetail 工具</thinking>",
              "tool_calls": [
                {
                  "id": "call_001",
                  "type": "function",
                  "function": {
                    "name": "getWorkOrderDetail",
                    "arguments": "{\"workOrderNo\":\"WO202506191935695546618613760\"}"
                  }
                }
              ]
            }

            #### 示例 2：工具结果回传（role=tool 消息）
            {
              "role": "tool",
              "tool_call_id": "call_001",
              "name": "getWorkOrderDetail",
              "content": "{\"tool\":\"getWorkOrderDetail\",\"success\":true,\"result\":\"工单状态：已确认完成\"}"
            }

            #### 示例 3：多工具并行调用
            模型响应 tool_calls 数组含多个工具调用项，每项有独立 id；
            系统逐个执行后，对每个 tool_call_id 回传一条 role=tool 消息。

            #### 示例 4：todo 工具调用（任务管理已并入 tools）
            <thinking>这是复杂任务，先创建 todo 列表</thinking>
            tool_calls:
            [
              {"id":"call_1","type":"function","function":{"name":"todoWrite","arguments":"{\\"content\\":\\"查询工单\\",\\"priority\\":1}"}},
              {"id":"call_2","type":"function","function":{"name":"todoWrite","arguments":"{\\"content\\":\\"生成报表\\",\\"priority\\":2}"}}
            ]

            #### 示例 5：工具失败后按技能文档恢复
            工具结果回传（success=false）：
            {"tool":"someTool","success":false,"result":"{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}"}

            模型响应：
            <thinking>工具失败，需要先检查已加载 SKILL.md 中对该错误的恢复步骤；如果技能要求先修复认证或参数，再按技能流程继续</thinking>
            随后的 tool_calls 必须使用 TOOLS JSON 中真实存在的工具名称和参数，并严格遵循 SKILL.md 的恢复步骤；不能编造工具。
            """;
    }

    /**
     * 最终回答格式：采用 thinking 标签方案。
     * <p>
     * 要求 LLM：
     * <ul>
     *   <li>每次响应在 &lt;thinking&gt;...&lt;/thinking&gt; 内做简短推理</li>
     *   <li>最终答案写在标签外（自然语言）</li>
     * </ul>
     * 系统解析时剥离 thinking 部分，仅暴露标签外内容给用户。
     */
    private String buildOutputFormatSection() {
        return """
            ## 最终回答格式（thinking 标签方案）
            每次响应都先用 <thinking>...</thinking> 标签进行简短推理（用户不可见），
            然后在标签外给出最终自然语言回答（用户可见）。
            系统会自动剥离 <thinking> 标签内容，只展示标签外的部分给用户。

            ### 规则
            - <thinking> 标签内：分析需求、决定是否调用工具、组织回答逻辑（必须推理，不得省略）
            - 标签外：直接给用户的自然语言回答，简洁明了
            - 工具调用阶段：content 可仅含 <thinking> 推理，无需标签外内容
            - 最终回答阶段：先 <thinking> 推理，再在标签外给出答案

            ### 示例
            用户问：帮我查一下工单 WO202506191935695546618613760 的状态

            第一次响应（调用工具）：
            <thinking>用户询问工单状态，需要调用 getWorkOrderDetail 工具获取数据</thinking>
            （通过 tool_calls 调用工具）

            工具结果返回后，最终响应：
            <thinking>工具返回工单状态为已确认完成，直接转述给用户</thinking>
            工单 WO202506191935695546618613760 当前状态为：已确认完成。

            ### 注意
            - <thinking> 标签必须成对出现，内容不能包含 </thinking> 字符串
            - 默认最终答案使用自然语言；但当已加载的 SKILL.md 要求原样展示工具结果时，必须在标签外保留工具返回原文，不要改写成摘要或其它说明
            - 当已加载的 SKILL.md 要求等待用户确认/补充信息时，最终回答必须停在该确认/补充请求上，不要继续调用后续工具
            - 推理过程要简短，避免冗长
            """;
    }

    private Map<String, SkillLoader.SkillMetadata> getSkillMap() {
        try {
            java.lang.reflect.Field field = SkillLoader.class.getDeclaredField("skillMetadataMap");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, SkillLoader.SkillMetadata> map = (Map<String, SkillLoader.SkillMetadata>) field.get(skillLoader);
            return map;
        } catch (Exception e) {
            log.warn("获取技能映射失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String getDefaultPrompt() {
        return """
            # 工单客服 Agent - 系统提示词

            ## 角色定位
            你是一个专业的工单管理系统客服助手，使用ReAct模式思考和行动。你的主要职责是帮助用户查询和管理工单信息。

            ## 知识来源
            - RAG检索：系统会自动从知识库中检索相关信息，包含工单状态、类型、流程等领域知识
            - 技能文档：通过 loadSkill(技能名) 动态加载完整技能文档

            ## 可用工具（JSON 数组，含 todo 任务管理工具）
            {{TOOLS}}

            ## 可用技能（JSON 数组）
            {{SKILLS}}

            ## 已加载技能文档（SKILL.md）
            {{SKILL_CONTENT}}

            ## 思考-行动循环
            {{REACT_FORMAT}}

            ## 最终回答格式
            {{OUTPUT_FORMAT}}

            ## 交互原则
            1. 先理解用户需求
            2. 根据需求匹配可用技能的描述
            3. 调用 loadSkill(技能名) 获取完整技能文档
            4. 根据技能文档的说明调用相应工具
            5. 将技术结果转换为友好的自然语言回答

            ## 注意事项
            - 优先使用工具获取数据，而不是凭记忆回答
            - 工具调用后必须根据返回结果继续思考或总结
            - 如果工具执行失败，尝试使用其他工具或方法
            - 回答要简洁明了，避免冗长
            - 对于复杂任务，需要分步执行
            """;
    }
}
