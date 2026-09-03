package dryrun

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strings"
)

type Plan struct {
	PreviewID            string                 `json:"previewId"`
	RequestDigest        string                 `json:"requestDigest"`
	RequiresConfirmation bool                   `json:"requiresConfirmation"`
	ExpiresAt            string                 `json:"expiresAt"`
	Operation            string                 `json:"operation"`
	OperationCN          string                 `json:"operationCN"`
	Endpoint             string                 `json:"endpoint"`
	Params               map[string]interface{} `json:"params"`
	ReadableParams       []ParamDisplay         `json:"readableParams"`
	Impact               string                 `json:"impact"`
	RiskLevel            string                 `json:"riskLevel"`
}

type ParamDisplay struct {
	Key          string      `json:"key"`
	Value        interface{} `json:"value"`
	DisplayValue string      `json:"displayValue"`
	Description  string      `json:"description"`
}

type Engine struct{}

func NewEngine() *Engine {
	return &Engine{}
}

func DecodePlan(data interface{}) (*Plan, error) {
	raw, err := json.Marshal(data)
	if err != nil {
		return nil, fmt.Errorf("failed to encode preview response: %w", err)
	}
	var plan Plan
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.UseNumber()
	if err := decoder.Decode(&plan); err != nil {
		return nil, fmt.Errorf("failed to decode preview response: %w", err)
	}
	if plan.Operation == "" || plan.OperationCN == "" || plan.RiskLevel == "" {
		return nil, fmt.Errorf("preview response is missing required fields")
	}
	return &plan, nil
}

func (e *Engine) PrintPlan(plan *Plan) {
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("[dry-run] 执行计划预览")
	fmt.Println(strings.Repeat("=", 50))
	fmt.Printf("  操作类型: %s (%s)\n", plan.OperationCN, plan.Operation)
	fmt.Printf("  目标端点: %s\n", plan.Endpoint)
	fmt.Printf("  风险等级: %s\n", e.riskLabel(plan.RiskLevel))
	fmt.Printf("  预期影响: %s\n", plan.Impact)
	fmt.Printf("  预演凭证: %s\n", plan.PreviewID)
	fmt.Printf("  凭证有效期: %s\n", plan.ExpiresAt)
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
	fmt.Printf("  确认执行请将 --dry-run 替换为 --preview-id %s。\n", plan.PreviewID)
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
