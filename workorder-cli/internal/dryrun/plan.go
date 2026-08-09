package dryrun

import (
	"fmt"
	"strings"
)

type Plan struct {
	Operation    string                 `json:"operation"`
	OperationCN  string                 `json:"operationCN"`
	Endpoint     string                 `json:"endpoint"`
	Params       map[string]interface{} `json:"params"`
	ReadableParams []ParamDisplay       `json:"readableParams"`
	Impact       string                 `json:"impact"`
	RiskLevel    string                 `json:"riskLevel"`
}

type ParamDisplay struct {
	Key         string      `json:"key"`
	Value       interface{} `json:"value"`
	DisplayValue string     `json:"displayValue"`
	Description string      `json:"description"`
}

type Engine struct{}

func NewEngine() *Engine {
	return &Engine{}
}

func (e *Engine) BuildPlan(dataCode string, params map[string]interface{}) *Plan {
	def := GetOperationDef(dataCode)
	if def == nil {
		return &Plan{
			Operation:   dataCode,
			OperationCN: dataCode,
			Endpoint:    "/api/execute",
			Params:      params,
			Impact:      "未知操作",
			RiskLevel:   "unknown",
		}
	}

	readableParams := make([]ParamDisplay, 0)
	for key, value := range params {
		displayValue := TranslateValue(key, value, dataCode)
		readableParams = append(readableParams, ParamDisplay{
			Key:          key,
			Value:        value,
			DisplayValue: displayValue,
			Description:  getParamDescription(key, dataCode),
		})
	}

	return &Plan{
		Operation:      dataCode,
		OperationCN:    def.Name,
		Endpoint:       def.Endpoint,
		Params:         params,
		ReadableParams: readableParams,
		Impact:         def.Impact,
		RiskLevel:      def.RiskLevel,
	}
}

func (e *Engine) PrintPlan(plan *Plan) {
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("[dry-run] 执行计划预览")
	fmt.Println(strings.Repeat("=", 50))
	fmt.Printf("  操作类型: %s (%s)\n", plan.OperationCN, plan.Operation)
	fmt.Printf("  目标端点: %s\n", plan.Endpoint)
	fmt.Printf("  风险等级: %s\n", e.riskLabel(plan.RiskLevel))
	fmt.Printf("  预期影响: %s\n", plan.Impact)
	fmt.Println(strings.Repeat("-", 50))
	fmt.Println("  参数详情:")
	if len(plan.ReadableParams) == 0 {
		fmt.Println("    (无参数)")
	}
	for _, p := range plan.ReadableParams {
		if p.DisplayValue != fmt.Sprintf("%v", p.Value) {
			fmt.Printf("    %s: %v (%s)\n", p.Key, p.Value, p.DisplayValue)
		} else {
			fmt.Printf("    %s: %v\n", p.Key, p.Value)
		}
		if p.Description != "" {
			fmt.Printf("      [%s]\n", p.Description)
		}
	}
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("  注意: 此为 dry-run 预览模式，不会实际执行写操作。")
	fmt.Println("  确认执行请去掉 --dry-run 参数。")
	fmt.Println(strings.Repeat("=", 50))
}

func (e *Engine) riskLabel(level string) string {
	switch level {
	case "high":
		return "高 (不可逆操作)"
	case "medium":
		return "中 (状态变更)"
	case "low":
		return "低 (可撤销)"
	default:
		return "未知"
	}
}

type OperationDef struct {
	Operation   string
	Name        string
	Endpoint    string
	Impact      string
	RiskLevel   string
}

var operationDefs = map[string]*OperationDef{
	"work_order_create": {
		Operation: "work_order_create", Name: "创建工单", Endpoint: "/api/execute",
		Impact:    "新增一条工单记录，分配给处理人", RiskLevel: "medium",
	},
	"work_order_handle": {
		Operation: "work_order_handle", Name: "处理工单", Endpoint: "/api/execute",
		Impact:    "变更工单状态，可能通知相关人员", RiskLevel: "medium",
	},
	"work_order_delete": {
		Operation: "work_order_delete", Name: "删除工单", Endpoint: "/api/execute",
		Impact:    "物理删除工单记录，不可恢复", RiskLevel: "high",
	},
	"work_order_cancel": {
		Operation: "work_order_cancel", Name: "取消工单", Endpoint: "/api/execute",
		Impact:    "将工单状态变更为已取消", RiskLevel: "medium",
	},
	"work_order_approval": {
		Operation: "work_order_approval", Name: "审批工单", Endpoint: "/api/execute",
		Impact:    "审批通过/拒绝，变更工单生命周期", RiskLevel: "medium",
	},
	"flow_create": {
		Operation: "flow_create", Name: "创建流程", Endpoint: "/api/execute",
		Impact:    "新增流程定义，影响后续工单的处理流程", RiskLevel: "high",
	},
	"flow_edit": {
		Operation: "flow_edit", Name: "编辑流程", Endpoint: "/api/execute",
		Impact:    "修改流程定义，可能影响使用该流程的工单", RiskLevel: "high",
	},
	"flow_delete": {
		Operation: "flow_delete", Name: "删除流程", Endpoint: "/api/execute",
		Impact:    "删除流程定义，不可恢复", RiskLevel: "high",
	},
}

func GetOperationDef(dataCode string) *OperationDef {
	return operationDefs[dataCode]
}
