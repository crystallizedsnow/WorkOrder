package cmd

import (
	"context"
	"encoding/json"
	"fmt"

	"workorder-cli/internal/api"
	"workorder-cli/internal/output"

	"github.com/spf13/viper"
)

func handleApiCommand(args []string) {
	if len(args) < 2 {
		output.PrintError(4, "缺少子命令，可用子命令: call")
		return
	}

	subCmd := args[1]

	switch subCmd {
	case "call":
		handleApiCall(args)
	default:
		output.PrintError(4, fmt.Sprintf("未知子命令: %s，可用子命令: call", subCmd))
	}
}

func handleApiCall(args []string) {
	method := getArgValue(args, "--method")
	endpoint := getArgValue(args, "--endpoint")

	if endpoint == "" {
		output.PrintError(4, "endpoint参数不能为空")
		return
	}

	if method == "" {
		method = "GET"
	}

	client := api.NewClient()
	ctx := context.Background()

	var reqBody interface{}
	body, bodyErr := resolveBodyArg(args)
	if bodyErr != nil {
		output.PrintError(4, bodyErr.Error())
		return
	}
	if body != "" {
		var bodyData map[string]interface{}
		if err := json.Unmarshal([]byte(body), &bodyData); err != nil {
			output.PrintError(4, fmt.Sprintf("body参数不是有效的JSON: %v。建议使用 --body @文件路径 从文件读取JSON", err))
			return
		}
		reqBody = bodyData
	}

	url := fmt.Sprintf("%s%s", viper.GetString("backend-url"), endpoint)
	headers := api.GetAuthHeaders()

	var resp *api.ApiResponse
	var err error

	switch method {
	case "GET":
		resp, err = client.Get(ctx, url, headers)
	case "POST":
		resp, err = client.Post(ctx, url, reqBody, headers)
	default:
		output.PrintError(4, fmt.Sprintf("不支持的HTTP方法: %s", method))
		return
	}

	if err != nil {
		output.PrintError(1, fmt.Sprintf("请求失败: %v", err))
		return
	}

	output.PrintResponse(output.Response{
		Code:    resp.Code,
		Message: resp.Message,
		Data:    resp.Data,
		TraceId: resp.TraceId,
	})
}
