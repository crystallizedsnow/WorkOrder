package dryrun

import "fmt"

var priorityLevelMap = map[int]string{
	0: "高",
	1: "中",
	2: "低",
}

var workOrderTypeMap = map[int]string{
	0: "需求",
	1: "故障",
}

var handleTypeMap = map[int]string{
	1: "分配",
	2: "请求协助",
	3: "催单",
	4: "完成",
	5: "确认完成",
	6: "仍有问题",
}

var approvalStatusMap = map[bool]string{
	true:  "通过",
	false: "拒绝",
}

var paramDescriptions = map[string]map[string]string{
	"work_order_create": {
		"type":          "工单类型",
		"title":         "工单标题",
		"content":       "工单详情",
		"priorityLevel": "优先级",
		"flowId":        "关联流程ID",
		"deadlineTime":  "截止时间戳",
	},
	"work_order_handle": {
		"id":             "工单ID",
		"code":           "工单编号",
		"handleType":     "处理类型",
		"assignedUserId": "分配给用户ID",
		"remark":         "操作备注",
	},
	"work_order_delete": {
		"id":   "工单ID",
		"code": "工单编号",
	},
	"work_order_cancel": {
		"id":     "工单ID",
		"code":   "工单编号",
		"reason": "取消原因",
	},
	"work_order_approval": {
		"id":         "工单ID",
		"code":       "工单编号",
		"isApproved": "审批结果",
		"remark":     "审批意见",
	},
	"flow_create": {
		"flowName":    "流程名称",
		"nodes":       "审核节点列表",
		"distributeNode": "分配节点",
		"checkNode":   "验收节点",
	},
	"flow_edit": {
		"flowId":      "流程ID",
		"flowName":    "流程名称",
		"nodes":       "审核节点列表",
		"distributeNode": "分配节点",
		"checkNode":   "验收节点",
	},
	"flow_delete": {
		"flowId": "流程ID",
	},
}

func TranslateValue(key string, value interface{}, dataCode string) string {
	switch key {
	case "priorityLevel":
		if v, ok := toInt(value); ok {
			if label, exists := priorityLevelMap[v]; exists {
				return fmt.Sprintf("%d(%s)", v, label)
			}
		}
	case "type":
		if v, ok := toInt(value); ok {
			if label, exists := workOrderTypeMap[v]; exists {
				return fmt.Sprintf("%d(%s)", v, label)
			}
		}
	case "handleType":
		if v, ok := toInt(value); ok {
			if label, exists := handleTypeMap[v]; exists {
				return fmt.Sprintf("%d(%s)", v, label)
			}
		}
	case "isApproved":
		if v, ok := value.(bool); ok {
			if label, exists := approvalStatusMap[v]; exists {
				return fmt.Sprintf("%v(%s)", v, label)
			}
		}
	case "assignedUserId":
		if v, ok := toInt(value); ok {
			return fmt.Sprintf("%d(用户ID)", v)
		}
	case "flowId":
		if v, ok := toInt(value); ok {
			return fmt.Sprintf("%d(流程ID)", v)
		}
	case "id":
		if v, ok := toInt(value); ok {
			return fmt.Sprintf("%d(工单ID)", v)
		}
	}

	return fmt.Sprintf("%v", value)
}

func getParamDescription(key string, dataCode string) string {
	if descriptions, ok := paramDescriptions[dataCode]; ok {
		if desc, exists := descriptions[key]; exists {
			return desc
		}
	}
	return ""
}

func toInt(value interface{}) (int, bool) {
	switch v := value.(type) {
	case int:
		return v, true
	case int32:
		return int(v), true
	case int64:
		return int(v), true
	case float64:
		return int(v), true
	case string:
		var i int
		if _, err := fmt.Sscanf(v, "%d", &i); err == nil {
			return i, true
		}
	}
	return 0, false
}
