package com.aiassistant.prompt;

import com.aiassistant.agent.ToolDispatcher;
import com.aiassistant.loader.SkillLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
public class DynamicPromptGenerator {

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
        prompt = prompt.replace("{{TODO}}", buildTodoSection());
        prompt = prompt.replace("{{REACT_FORMAT}}", buildReActSection());
        
        return prompt;
    }

    private String buildToolsSection() {
        StringBuilder sb = new StringBuilder();
        Map<String, String> tools = toolDispatcher.listTools();
        if (tools.isEmpty()) {
            sb.append("暂无可用工具\n");
        } else {
            for (Map.Entry<String, String> entry : tools.entrySet()) {
                sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        return sb.toString();
    }

    private String buildSkillsSection() {
        StringBuilder sb = new StringBuilder();
        try {
            Map<String, SkillLoader.SkillMetadata> skillMap = getSkillMap();
            if (skillMap.isEmpty()) {
                sb.append("暂无可用技能\n");
            } else {
                sb.append("可用技能列表（当需要使用某个技能时，调用 loadSkill(\"技能名\") 获取完整技能文档）:\n");
                for (Map.Entry<String, SkillLoader.SkillMetadata> entry : skillMap.entrySet()) {
                    String skillKey = entry.getKey();
                    SkillLoader.SkillMetadata meta = entry.getValue();
                    String displayName = meta.getName() != null ? meta.getName() : skillKey;
                    String description = meta.getDescription() != null ? meta.getDescription() : "无描述";
                    sb.append(String.format("  - [%s] %s — %s\n", skillKey, displayName, description));
                }
                sb.append("\n技能调用方式：\n");
                sb.append("  1. 根据用户需求匹配技能描述\n");
                sb.append("  2. 调用 loadSkill(\"技能名\") 获取完整技能文档\n");
                sb.append("  3. 根据技能文档执行对应的工具调用\n");
            }
        } catch (Exception e) {
            log.warn("加载技能内容失败: {}", e.getMessage());
            sb.append("技能加载失败\n");
        }
        return sb.toString();
    }

    private String buildTodoSection() {
        return """
            对于需要多个步骤完成的复杂任务，请先使用 todoWrite 创建任务列表，然后按顺序执行。
            可用工具：
              - todoWrite(content, priority): 创建待办任务，priority默认为1
              - todoList(): 查看当前任务列表
              - todoUpdate(todoId, status): 更新任务状态(status: pending/in_progress/completed)
              - todoClear(): 清空所有任务
            注意：一次只能有一个任务处于in_progress状态
            """;
    }

    private String buildReActSection() {
        return """
            1. 思考：分析用户需求，决定是否需要调用工具
            2. 行动：调用工具获取结果
            3. 总结：根据工具结果给出最终回答
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

            ## 可用工具
            {{TOOLS}}

            ## 技能知识
            {{SKILLS}}

            ## TodoWrite任务管理
            {{TODO}}

            ## 思考-行动循环
            {{REACT_FORMAT}}

            ## 输出格式
            思考：你的思考过程
            行动：调用工具名称(参数名=参数值)

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
