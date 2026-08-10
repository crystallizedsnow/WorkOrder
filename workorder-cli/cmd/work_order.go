package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"os"

	"workorder-cli/internal/api"
	"workorder-cli/internal/dryrun"
	"workorder-cli/internal/output"

	"github.com/spf13/viper"
)

func handleWorkOrderCommand(args []string) {
	if len(args) < 2 {
		output.PrintError(4, "缺少子命令，可用子命令: page, detail, search, create, handle, delete, cancel, approval")
		return
	}

	subCmd := args[1]

	switch subCmd {
	case "page":
		handleWorkOrderPage(args)
	case "detail":
		handleWorkOrderDetail(args)
	case "search":
		handleWorkOrderSearch(args)
	case "create":
		handleWorkOrderCreate(args)
	case "handle":
		handleWorkOrderHandle(args)
	case "delete":
		handleWorkOrderDelete(args)
	case "cancel":
		handleWorkOrderCancel(args)
	case "approval":
		handleWorkOrderApproval(args)
	default:
		output.PrintError(4, fmt.Sprintf("未知子命令: %s，可用子命令: page, detail, search, create, handle, delete, cancel, approval", subCmd))
	}
}

func checkDryRun() bool {
	// DisableFlagParsing: true 时 cobra 不解析 flag，viper 无法获取 --dry-run 值
	// 需要直接扫描 os.Args 判断是否包含 --dry-run
	for _, arg := range os.Args {
		if arg == "--dry-run" {
			return true
		}
	}
	return viper.GetBool("dry-run")
}

func handleDryRun(dataCode string, params map[string]interface{}) {
	if checkDryRun() {
		engine := dryrun.NewEngine()
		plan := engine.BuildPlan(dataCode, params)
		engine.PrintPlan(plan)
		return
	}
}

func handleWorkOrderCreate(args []string) {
	workType := parseIntArg(args, "--type", -1)
	title := getArgValue(args, "--title")
	content := getArgValue(args, "--content")
	priorityLevel := parseIntArg(args, "--priority-level", -1)
	flowId := parseIntArg(args, "--flow-id", 0)
	deadlineTime := parseIntArg(args, "--deadline-time", 0)

	if workType == -1 {
		output.PrintError(4, "type参数不能为空")
		return
	}
	if title == "" {
		output.PrintError(4, "title参数不能为空")
		return
	}
	if content == "" {
		output.PrintError(4, "content参数不能为空")
		return
	}
	if priorityLevel == -1 {
		output.PrintError(4, "priority-level参数不能为空")
		return
	}
	if flowId == 0 {
		output.PrintError(4, "flow-id参数不能为空")
		return
	}

	params := map[string]interface{}{
		"type":          int(workType),
		"title":         title,
		"content":       content,
		"priorityLevel": int(priorityLevel),
		"flowId":        flowId,
	}
	if deadlineTime != 0 {
		params["deadlineTime"] = deadlineTime
	}

	if checkDryRun() {
		handleDryRun("work_order_create", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "work_order_create", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("创建工单失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleWorkOrderHandle(args []string) {
	id := parseIntArg(args, "--id", 0)
	code := getArgValue(args, "--code")
	handleType := parseIntArg(args, "--handle-type", -1)
	assignedUserId := parseIntArg(args, "--assigned-user-id", 0)
	remark := getArgValue(args, "--remark")

	if id == 0 && code == "" {
		output.PrintError(4, "必须指定id或code参数")
		return
	}
	if handleType == -1 {
		output.PrintError(4, "handle-type参数不能为空")
		return
	}

	params := map[string]interface{}{
		"handleType": int(handleType),
	}
	if id != 0 {
		params["id"] = id
	}
	if code != "" {
		params["code"] = code
	}
	if assignedUserId != 0 {
		params["assignedUserId"] = assignedUserId
	}
	if remark != "" {
		params["remark"] = remark
	}

	if checkDryRun() {
		handleDryRun("work_order_handle", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "work_order_handle", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("处理工单失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleWorkOrderDelete(args []string) {
	id := parseIntArg(args, "--id", 0)
	code := getArgValue(args, "--code")

	if id == 0 && code == "" {
		output.PrintError(4, "必须指定id或code参数")
		return
	}

	params := map[string]interface{}{}
	if id != 0 {
		params["id"] = id
	}
	if code != "" {
		params["code"] = code
	}

	if checkDryRun() {
		handleDryRun("work_order_delete", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "work_order_delete", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("删除工单失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleWorkOrderCancel(args []string) {
	id := parseIntArg(args, "--id", 0)
	code := getArgValue(args, "--code")
	reason := getArgValue(args, "--reason")

	if id == 0 && code == "" {
		output.PrintError(4, "必须指定id或code参数")
		return
	}

	params := map[string]interface{}{}
	if id != 0 {
		params["id"] = id
	}
	if code != "" {
		params["code"] = code
	}
	if reason != "" {
		params["reason"] = reason
	}

	if checkDryRun() {
		handleDryRun("work_order_cancel", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "work_order_cancel", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("取消工单失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func handleWorkOrderApproval(args []string) {
	id := parseIntArg(args, "--id", 0)
	code := getArgValue(args, "--code")
	isApproved := getArgValue(args, "--is-approved")
	remark := getArgValue(args, "--remark")

	if id == 0 && code == "" {
		output.PrintError(4, "必须指定id或code参数")
		return
	}
	if isApproved == "" {
		output.PrintError(4, "is-approved参数不能为空")
		return
	}

	params := map[string]interface{}{
		"isApproved": isApproved == "true",
	}
	if id != 0 {
		params["id"] = id
	}
	if code != "" {
		params["code"] = code
	}
	if remark != "" {
		params["remark"] = remark
	}

	if checkDryRun() {
		handleDryRun("work_order_approval", params)
		return
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Execute(ctx, "work_order_approval", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("审批工单失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}

func parseBodyArg(args []string) map[string]interface{} {
	bodyStr, err := resolveBodyArg(args)
	if err != nil || bodyStr == "" {
		return nil
	}

	var body map[string]interface{}
	if err := json.Unmarshal([]byte(bodyStr), &body); err != nil {
		return nil
	}
	return body
}

func handleWorkOrderPage(args []string) {
	pageNum := parseIntArg(args, "--page-num", 1)
	pageSize := parseIntArg(args, "--page-size", 10)
	title := getArgValue(args, "--title")
	code := getArgValue(args, "--code")
	workType := parseIntArg(args, "--type", -1)
	content := getArgValue(args, "--content")
	createTimeTo := parseIntArg(args, "--create-time-to", 0)

	client := api.NewClient()
	ctx := context.Background()

	params := map[string]interface{}{
		"pageNum":  int(pageNum),
		"pageSize": int(pageSize),
	}
	if title != "" {
		params["title"] = title
	}
	if code != "" {
		params["code"] = code
	}
	if workType != -1 {
		params["type"] = int(workType)
	}
	if content != "" {
		params["content"] = content
	}
	if createTimeTo != 0 {
		params["createTimeTo"] = createTimeTo
	}

	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, "work_order_page", params, headers)
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

func handleWorkOrderDetail(args []string) {
	id := parseIntArg(args, "--id", 0)
	code := getArgValue(args, "--code")

	client := api.NewClient()
	ctx := context.Background()

	params := map[string]interface{}{}
	if id != 0 {
		params["id"] = id
	} else if code != "" {
		params["code"] = code
	} else {
		output.PrintError(4, "必须指定id或code参数")
		return
	}

	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, "work_order_detail", params, headers)
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

func handleWorkOrderSearch(args []string) {
	keyword := getArgValue(args, "--keyword")
	pageNum := parseIntArg(args, "--page-num", 1)
	pageSize := parseIntArg(args, "--page-size", 10)

	if keyword == "" {
		output.PrintError(4, "keyword参数不能为空")
		return
	}

	client := api.NewClient()
	ctx := context.Background()

	params := map[string]interface{}{
		"keyword":  keyword,
		"pageNum":  int(pageNum),
		"pageSize": int(pageSize),
	}

	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, "work_order_search", params, headers)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("搜索失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}
