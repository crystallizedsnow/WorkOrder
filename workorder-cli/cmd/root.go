package cmd

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"

	"workorder-cli/internal/api"
	"workorder-cli/internal/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var rootCmd = &cobra.Command{
	Use:                "workorder-cli",
	Short:              "工单系统命令行工具",
	Long:               "面向AI Agent的工单系统命令行工具，提供认证、查询、Schema内省等功能",
	Version:            "1.0.0",
	Args:               cobra.ArbitraryArgs,
	DisableFlagParsing: true,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			cmd.Help()
			return
		}

		// DisableFlagParsing: true 时 cobra 不解析 flag，--dry-run 等标志会出现在 args 中
		// 需要跳过前导 -- 标志，找到实际的命令名称
		booleanFlags := map[string]bool{
			"--dry-run": true,
			"--debug":   true,
		}
		cmdIndex := 0
		for cmdIndex < len(args) && strings.HasPrefix(args[cmdIndex], "--") {
			if booleanFlags[args[cmdIndex]] {
				cmdIndex++
			} else {
				// 字符串型 flag（如 --token），跳过 flag 和它的值
				cmdIndex += 2
			}
		}
		if cmdIndex >= len(args) {
			cmd.Help()
			return
		}

		firstArg := args[cmdIndex]

		switch firstArg {
		case "list":
			handleListCommand(args)
			return
		case "schema":
			handleSchemaCommand(args[cmdIndex:])
			return
		case "auth":
			handleAuthCommand(args[cmdIndex:])
			return
		case "api":
			handleApiCommand(args[cmdIndex:])
			return
		case "work_order":
			handleWorkOrderCommand(args[cmdIndex:])
			return
		case "dashboard":
			handleDashboardCommand(args[cmdIndex:])
			return
		case "flow":
			handleFlowCommand(args[cmdIndex:])
			return
		case "work_order_create":
			handleWorkOrderCreate(args[cmdIndex:])
			return
		case "work_order_handle":
			handleWorkOrderHandle(args[cmdIndex:])
			return
		case "work_order_delete":
			handleWorkOrderDelete(args[cmdIndex:])
			return
		case "work_order_cancel":
			handleWorkOrderCancel(args[cmdIndex:])
			return
		case "work_order_approval":
			handleWorkOrderApproval(args[cmdIndex:])
			return
		case "flow_create":
			handleFlowCreate(args[cmdIndex:])
			return
		case "flow_edit":
			handleFlowEdit(args[cmdIndex:])
			return
		case "flow_delete":
			handleFlowDelete(args[cmdIndex:])
			return
		default:
			handleDataCodeQuery(args[cmdIndex:])
			return
		}
	},
}

func handleDataCodeQuery(args []string) {
	dataCode := args[0]

	params := make(map[string]interface{})
	for i := 1; i < len(args); i++ {
		arg := args[i]
		if strings.HasPrefix(arg, "--") {
			key := strings.TrimPrefix(arg, "--")
			if i+1 < len(args) && !strings.HasPrefix(args[i+1], "--") {
				value := args[i+1]
				params[key] = parseValue(value)
				i++
			} else {
				params[key] = true
			}
		}
	}

	client := api.NewClient()
	ctx := context.Background()
	headers := api.GetAuthHeaders()

	resp, err := client.Query(ctx, dataCode, params, headers)
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

func parseIntArg(args []string, flag string, defaultValue int64) int64 {
	value := getArgValue(args, flag)
	if value == "" {
		return defaultValue
	}
	if num, err := strconv.ParseInt(value, 10, 64); err == nil {
		return num
	}
	return defaultValue
}

func parseValue(value string) interface{} {
	if value == "true" {
		return true
	}
	if value == "false" {
		return false
	}

	if num, err := strconv.ParseInt(value, 10, 64); err == nil {
		return num
	}

	if num, err := strconv.ParseFloat(value, 64); err == nil {
		return num
	}

	return value
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)

	rootCmd.PersistentFlags().StringP("token", "t", "", "认证Token")
	rootCmd.PersistentFlags().String("backend-url", "http://localhost:8080", "后端服务URL")
	rootCmd.PersistentFlags().String("cli-service-url", "http://localhost:5000", "CLI Service URL")
	rootCmd.PersistentFlags().Bool("debug", false, "开启调试模式")
	rootCmd.PersistentFlags().Bool("dry-run", false, "预览模式，不实际执行")

	viper.BindPFlag("token", rootCmd.PersistentFlags().Lookup("token"))
	viper.BindPFlag("backend-url", rootCmd.PersistentFlags().Lookup("backend-url"))
	viper.BindPFlag("cli-service-url", rootCmd.PersistentFlags().Lookup("cli-service-url"))
	viper.BindPFlag("debug", rootCmd.PersistentFlags().Lookup("debug"))
	viper.BindPFlag("dry-run", rootCmd.PersistentFlags().Lookup("dry-run"))

	viper.BindEnv("token", "WORKORDER_TOKEN")
	viper.BindEnv("backend-url", "WORKORDER_BACKEND_URL")
	viper.BindEnv("cli-service-url", "WORKORDER_CLI_SERVICE_URL")
}

func initConfig() {
	viper.SetConfigName("workorder-cli")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("$HOME/.workorder")
	viper.AddConfigPath(".")

	if err := viper.ReadInConfig(); err == nil {
		fmt.Println("Using config file:", viper.ConfigFileUsed())
	}
}
