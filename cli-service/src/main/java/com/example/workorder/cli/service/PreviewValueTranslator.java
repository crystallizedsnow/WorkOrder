package com.example.workorder.cli.service;

import com.example.workorder.cli.dto.response.ParamDisplayDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PreviewValueTranslator {

    private static final Map<Integer, String> PRIORITY_LEVELS = Map.of(0, "高", 1, "中", 2, "低");
    private static final Map<Integer, String> WORK_ORDER_TYPES = Map.of(0, "需求", 1, "故障");
    private static final Map<Integer, String> HANDLE_TYPES = Map.of(
            1, "分配", 2, "请求协助", 3, "催单", 4, "完成", 5, "确认完成", 6, "仍有问题");

    public List<ParamDisplayDTO> translate(Map<String, Object> params, Map<String, Object> inputSchema) {
        List<ParamDisplayDTO> result = new ArrayList<>();
        if (params == null) {
            return result;
        }
        params.forEach((key, value) -> result.add(new ParamDisplayDTO(
                key, value, displayValue(key, value), description(inputSchema, key))));
        return result;
    }

    private String displayValue(String key, Object value) {
        Integer number = asInteger(value);
        if ("priorityLevel".equals(key) && number != null && PRIORITY_LEVELS.containsKey(number)) {
            return number + "(" + PRIORITY_LEVELS.get(number) + ")";
        }
        if ("type".equals(key) && number != null && WORK_ORDER_TYPES.containsKey(number)) {
            return number + "(" + WORK_ORDER_TYPES.get(number) + ")";
        }
        if ("handleType".equals(key) && number != null && HANDLE_TYPES.containsKey(number)) {
            return number + "(" + HANDLE_TYPES.get(number) + ")";
        }
        if ("isApproved".equals(key) && value instanceof Boolean approved) {
            return approved + "(" + (approved ? "通过" : "拒绝") + ")";
        }
        if ("assignedUserId".equals(key) && number != null) {
            return value + "(用户ID)";
        }
        if ("flowId".equals(key) && number != null) {
            return value + "(流程ID)";
        }
        if ("id".equals(key) && number != null) {
            return value + "(工单ID)";
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private String description(Map<String, Object> inputSchema, String key) {
        if (inputSchema == null) {
            return "";
        }
        Object definition = inputSchema.get(key);
        if (definition instanceof Map<?, ?> map) {
            Object description = map.get("description");
            return description == null ? "" : String.valueOf(description);
        }
        return "";
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
