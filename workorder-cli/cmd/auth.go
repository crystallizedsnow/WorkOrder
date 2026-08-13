package cmd

import (
	"context"
	"fmt"
	"os"
	"strings"

	"workorder-cli/internal/api"
	"workorder-cli/internal/auth"
	"workorder-cli/internal/output"
)

func handleAuthCommand(args []string) {
	if len(args) < 2 {
		output.PrintError(4, "缺少子命令，可用子命令: login, logout, status")
		return
	}

	subCmd := args[1]

	switch subCmd {
	case "login":
		handleAuthLogin(args)
	case "logout":
		handleAuthLogout(args)
	case "refresh":
		handleAuthRefresh(args)
	case "status":
		handleAuthStatus(args)
	default:
		output.PrintError(4, fmt.Sprintf("未知子命令: %s，可用子命令: login, logout, status", subCmd))
	}
}

func handleAuthLogin(args []string) {
	phone := getArgValue(args, "--phone")
	password := getArgValue(args, "--password")
	if phone == "" || password == "" {
		output.PrintError(4, "手机号和密码不能为空，请使用 --phone 和 --password 参数交互登录")
		return
	}

	client := api.NewClient()
	ctx := context.Background()

	resp, err := client.Login(ctx, phone, password)
	if err != nil {
		output.PrintError(1, fmt.Sprintf("登录失败: %v", err))
		return
	}

	if resp.Code != 1 {
		output.PrintError(2, fmt.Sprintf("登录失败: %s", resp.Msg))
		return
	}

	if err := auth.SaveToken(resp.Data.AccessToken, resp.Data.RefreshToken, phone, resp.Data.AccessTokenExpiresAt); err != nil {
		output.PrintError(1, fmt.Sprintf("保存Token失败: %v", err))
		return
	}

	output.PrintSuccess(map[string]interface{}{
		"phone":     phone,
		"tokenType": resp.Data.TokenType,
		"expiresAt": resp.Data.AccessTokenExpiresAt,
	})
}

func handleAuthLogout(args []string) {
	if info, _ := auth.GetTokenInfo(); info != nil && info.Token != "" {
		_ = api.NewClient().Logout(context.Background(), info.Token)
	}
	if err := auth.DeleteToken(); err != nil {
		output.PrintError(1, fmt.Sprintf("登出失败: %v", err))
		return
	}
	output.PrintSuccess(map[string]interface{}{"message": "登出成功"})
}

func handleAuthRefresh(args []string) {
	info, err := auth.GetTokenInfo()
	if err != nil || info == nil || info.RefreshToken == "" {
		output.PrintError(2, "没有可用的刷新凭证，请重新登录")
		return
	}
	resp, err := api.NewClient().Refresh(context.Background(), info.RefreshToken)
	if err != nil || resp.Code != 1 {
		output.PrintError(2, "刷新失败，请重新登录")
		return
	}
	if err := auth.SaveToken(resp.Data.AccessToken, resp.Data.RefreshToken, info.Phone, resp.Data.AccessTokenExpiresAt); err != nil {
		output.PrintError(1, fmt.Sprintf("保存刷新结果失败: %v", err))
		return
	}
	output.PrintSuccess(map[string]interface{}{"tokenType": resp.Data.TokenType, "expiresAt": resp.Data.AccessTokenExpiresAt})
}

func handleAuthStatus(args []string) {
	tokenInfo, err := auth.GetTokenInfo()
	if err != nil {
		output.PrintError(1, fmt.Sprintf("获取Token状态失败: %v", err))
		return
	}

	if tokenInfo == nil {
		output.PrintSuccess(map[string]interface{}{
			"loggedIn": false,
			"message":  "未登录",
		})
		return
	}

	isExpired := auth.IsTokenExpired(tokenInfo)

	output.PrintSuccess(map[string]interface{}{
		"loggedIn":   !isExpired,
		"phone":      tokenInfo.Phone,
		"createTime": tokenInfo.CreateTime.Format("2006-01-02 15:04:05"),
		"expireTime": tokenInfo.ExpireTime.Format("2006-01-02 15:04:05"),
		"isExpired":  isExpired,
	})
}

func getArgValue(args []string, flag string) string {
	for i, arg := range args {
		if arg == flag && i+1 < len(args) {
			return args[i+1]
		}
	}
	return ""
}

// resolveBodyArg reads the --body argument value. If it starts with "@",
// the content is read from the specified file. This avoids PowerShell/shell
// quote-stripping issues when passing inline JSON.
func resolveBodyArg(args []string) (string, error) {
	bodyStr := getArgValue(args, "--body")
	if bodyStr == "" {
		return "", nil
	}

	if strings.HasPrefix(bodyStr, "@") {
		filePath := strings.TrimPrefix(bodyStr, "@")
		data, err := os.ReadFile(filePath)
		if err != nil {
			return "", fmt.Errorf("读取body文件失败: %v", err)
		}
		// Strip UTF-8 BOM (0xEF 0xBB 0xBF) if present
		if len(data) >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF {
			data = data[3:]
		}
		return string(data), nil
	}

	return bodyStr, nil
}
