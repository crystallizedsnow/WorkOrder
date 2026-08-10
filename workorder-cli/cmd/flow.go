package cmd

import (
	"context"
	"encoding/json"
	"fmt"

	"workorder-cli/internal/api"
	"workorder-cli/internal/output"
)

func handleFlowCommand(args []string) {
	if len(args) < 2 {
		output.PrintError(4, "缺少子命令，可用子命令: getById, page, create, edit, delete")
		return
	}

	subCmd := args[1]

	switch subCmd {
	case "getById":
		handleFlowGetById(args)
	case "page":
		handleFlowPage(args)
	case "create":
		handleFlowCreate(args)
	case "edit":
		handleFlowEdit(args)
	case "delete":
		handleFlowDelete(args)
	default:
		output.PrintError(4, fmt.Sprintf("未知子命令: %s，可用子命令: getById, page, create, edit, delete", subCmd))
	}
}

func handleFlowGetById(args []string) {
	flowId := parseIntArg(args, "--id", 0)

	if flowId == 0 {
		output.PrintError(4, "id参数不能为空")
		return
	}

	client := api.NewClient()
	ctx := context.Background()

	params := map[string]interface{}{
		"flowId": flowId,
	}

	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, "flow_get_by_id", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("查询失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleFlowPage(args []string) {
	pageNum := parseIntArg(args, "--page-num", 1)
	pageSize := parseIntArg(args, "--page-size", 10)

	client := api.NewClient()
	ctx := context.Background()

	params := map[string]interface{}{
		"pageNum":  int(pageNum),
		"pageSize": int(pageSize),
	}

	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, "flow_page", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("查询失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleFlowCreate(args []string) {
	flowName := getArgValue(args, "--flow-name")

	if flowName == "" {
		output.PrintError(4, "flow-name参数不能为空")
		return
	}

	params := map[string]interface{}{
		"flowName": flowName,
	}

	bodyStr, err := resolveBodyArg(args)
	if err != nil {
		output.PrintError(4, err.Error())
		return
	}
	if bodyStr != "" {
		var bodyParams map[string]interface{}
		if err := json.Unmarshal([]byte(bodyStr), &bodyParams); err != nil {
			output.PrintError(4, fmt.Sprintf("解析--body参数失败: %v。建议使用 --body @文件路径 从文件读取JSON", err))
			return
		}
		for k, v := range bodyParams {
			params[k] = v
		}
	}

	if checkDryRun() {
		handleDryRun("flow_create", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "flow_create", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("创建流程失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleFlowEdit(args []string) {
	flowId := parseIntArg(args, "--flow-id", 0)
	flowName := getArgValue(args, "--flow-name")

	if flowId == 0 {
		output.PrintError(4, "flow-id参数不能为空")
		return
	}

	params := map[string]interface{}{
		"flowId": flowId,
	}

	if flowName != "" {
		params["flowName"] = flowName
	}

	bodyStr, err := resolveBodyArg(args)
	if err != nil {
		output.PrintError(4, err.Error())
		return
	}
	if bodyStr != "" {
		var bodyParams map[string]interface{}
		if err := json.Unmarshal([]byte(bodyStr), &bodyParams); err != nil {
			output.PrintError(4, fmt.Sprintf("解析--body参数失败: %v。建议使用 --body @文件路径 从文件读取JSON", err))
			return
		}
		for k, v := range bodyParams {
			params[k] = v
		}
	}

	if checkDryRun() {
		handleDryRun("flow_edit", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "flow_edit", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("编辑流程失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleFlowDelete(args []string) {
	flowId := parseIntArg(args, "--flow-id", 0)

	if flowId == 0 {
		output.PrintError(4, "flow-id参数不能为空")
		return
	}

	params := map[string]interface{}{
		"flowId": flowId,
	}

	if checkDryRun() {
		handleDryRun("flow_delete", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "flow_delete", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("删除流程失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}
