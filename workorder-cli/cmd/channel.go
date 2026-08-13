package cmd

import (
	"context"
	"fmt"
	"workorder-cli/internal/api"
	"workorder-cli/internal/auth"
	"workorder-cli/internal/output"
)

func handleChannelCommand(args []string) {
	if len(args) < 3 || args[1] != "feishu" {
		output.PrintError(4, "用法: channel feishu bind-code|unbind")
		return
	}
	switch args[2] {
	case "bind-code":
		handleFeishuBindCode()
	case "unbind":
		handleFeishuUnbind()
	default:
		output.PrintError(4, fmt.Sprintf("未知子命令: %s", args[2]))
	}
}

func handleFeishuBindCode() {
	token := auth.GetToken()
	if token == "" {
		output.PrintError(2, "请先登录")
		return
	}
	result, err := api.NewClient().CreateBindingChallenge(context.Background(), token)
	if err != nil {
		output.PrintError(2, fmt.Sprintf("申请绑定码失败: %v", err))
		return
	}
	output.PrintSuccess(map[string]interface{}{"bindingCode": result.Code, "expiresAt": result.ExpiresAt, "usage": "在飞书中单聊机器人并提交该绑定码"})
}

func handleFeishuUnbind() {
	token := auth.GetToken()
	if token == "" {
		output.PrintError(2, "请先登录")
		return
	}
	if err := api.NewClient().UnbindFeishu(context.Background(), token); err != nil {
		output.PrintError(2, fmt.Sprintf("解绑失败: %v", err))
		return
	}
	output.PrintSuccess(map[string]interface{}{"message": "飞书身份已解绑"})
}
