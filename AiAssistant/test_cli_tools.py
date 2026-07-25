import requests
import json
import time
import sys
import os

BASE_URL = "http://localhost:8081/assistant"
BACKEND_URL = "http://localhost:8080"


def call_agent(session_id, message, description=None):
    url = f"{BASE_URL}/chat"
    headers = {"Content-Type": "application/json"}
    payload = {"memoryId": session_id, "message": message}

    if description:
        print(f"\n{'='*70}")
        print(f"测试场景: {description}")
        print(f"{'='*70}")
    else:
        print(f"\n{'='*70}")

    print(f"会话ID: {session_id}")
    print(f"用户输入: {message}")
    print(f"-"*70)

    try:
        start_time = time.time()
        response = requests.post(url, headers=headers, json=payload, stream=True, timeout=120)
        response.raise_for_status()

        buffer = bytearray()
        for chunk in response.iter_content(chunk_size=1024):
            if chunk:
                buffer.extend(chunk)

        try:
            result = buffer.decode('utf-8')
        except UnicodeDecodeError:
            result = buffer.decode('utf-8', errors='replace')

        elapsed_time = time.time() - start_time

        print(f"响应耗时: {elapsed_time:.2f}秒")
        print(f"-"*70)
        print(f"完整响应:")
        print(f"{'='*70}")
        print(result)
        print(f"{'='*70}")

        return result

    except requests.exceptions.RequestException as e:
        print(f"请求失败: {e}")
        return None


def test_cli_single_commands():
    print("\n" + "="*70)
    print("【单个CLI工具调用测试】")
    print("="*70)

    scenarios = [
        # (1001, "列出所有可用的CLI命令", "listCliCommands"),
        # (1002, "获取work_order_page命令的参数Schema", "getCliCommandSchema"),
        # (1003, "查询第一页工单信息，每页5条", "executeCliCommand-work_order_page"),
        # (1004, "查询工单ID为28的详情", "executeCliCommand-work_order_detail"),
        # (1005, "获取数据看板概览", "executeCliCommand-dashboard"),
        # (1006, "搜索关键词为'321'的工单", "executeCliCommand-work_order_search"),
        # (1007, "查询当前认证状态", "executeCliCommand-auth_status"),
    ]

    for session_id, message, desc in scenarios:
        call_agent(session_id, message, f"场景: {desc}")
        time.sleep(2)


def test_cli_compound_scenarios():
    print("\n" + "="*70)
    print("【复合场景测试 - 多工具链式/一次调用】")
    print("="*70)

    scenarios = [
        (2001, "查询第一页工单列表，然后获取第一条工单的详情", "列表→详情(链式)"),
        # (2002, "获取work_order_search的Schema，然后搜索关键词'故障'的工单", "Schema→搜索(链式)"),
        # (2003, "获取数据看板，然后查询处理中的工单", "看板→处理中(链式)"),
        # (2004, "同时获取数据看板和第一页工单列表，汇总显示结果", "看板+列表(一次调用多工具)"),
        # (2005, "使用快捷命令查询工单列表，再使用快捷命令获取数据概览", "快捷命令组合"),
    ]

    for session_id, message, desc in scenarios:
        call_agent(session_id, message, f"场景: {desc}")
        time.sleep(3)


def test_cli_login_recovery():
    print("\n" + "="*70)
    print("【登录恢复测试 - 401后自动登录】")
    print("="*70)

    call_agent(3001, "先查询工单列表，如果未登录则先登录", "登录恢复测试")


def main():
    print("="*70)
    print("CLI工具测试脚本")
    print("版本: v4.0 - 直接输出结果，不做关键词匹配")
    print("="*70)

    print("\n正在检查服务是否可用...")
    try:
        response = requests.get(f"{BASE_URL}/", timeout=5)
        print("AiAssistant服务可用")
    except requests.exceptions.RequestException:
        print("AiAssistant服务不可用，请先启动服务")
        print("命令: cd AiAssistant && mvn spring-boot:run")
        sys.exit(1)

    test_cli_single_commands()
    test_cli_compound_scenarios()
    test_cli_login_recovery()

    print("\n" + "="*70)
    print("测试完成")
    print("="*70)


if __name__ == "__main__":
    main()
