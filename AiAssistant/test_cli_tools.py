"""CLI 工具及短期记忆压缩的本地端到端测试。"""

import os
import re
import sys
import time

import requests


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE_URL = os.getenv("AI_ASSISTANT_TEST_URL", "http://localhost:8081/assistant")
BACKEND_URL = os.getenv("WORKORDER_BACKEND_URL", "http://localhost:8080")
AUTH_TOKEN = None


def login():
    """获取测试用户 Access Token，也可直接通过环境变量传入。"""
    global AUTH_TOKEN
    supplied = os.getenv("WORKORDER_TEST_ACCESS_TOKEN", "").strip()
    if supplied:
        AUTH_TOKEN = supplied[7:] if supplied.startswith("Bearer ") else supplied
        return True

    phone = os.getenv("WORKORDER_TEST_PHONE", "13812345678")
    password = os.getenv("WORKORDER_TEST_PASSWORD", "newPassword123!")
    try:
        response = requests.post(
            f"{BACKEND_URL}/user/login",
            json={"phone": phone, "password": password},
            timeout=15,
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") or {}
        if payload.get("code") != 1 or not data.get("accessToken"):
            print(f"登录失败: {payload.get('msg') or payload.get('message') or '未返回 accessToken'}")
            return False
        AUTH_TOKEN = data["accessToken"]
        print("登录成功")
        return True
    except (requests.RequestException, ValueError) as exc:
        print(f"登录请求失败: {exc}")
        return False


def auth_headers(accept="application/json"):
    headers = {"Accept": accept}
    if AUTH_TOKEN:
        headers["Authorization"] = f"Bearer {AUTH_TOKEN}"
    return headers


def decode_sse(response):
    events = []
    for raw_line in response.iter_lines(decode_unicode=False):
        line = raw_line.decode("utf-8", errors="replace") if raw_line else ""
        if line.startswith("data:"):
            events.append(line[5:].lstrip())
    return "".join(events)


def call_agent(session_id, message, description=None):
    if description:
        print(f"\n{'=' * 70}\n测试场景: {description}\n{'=' * 70}")
    print(f"会话ID: {session_id}\n用户输入: {message}\n{'-' * 70}")
    try:
        started = time.time()
        response = requests.post(
            f"{BASE_URL}/chat",
            headers={**auth_headers("text/event-stream"), "Content-Type": "application/json"},
            json={"memoryId": session_id, "message": message},
            stream=True,
            timeout=180,
        )
        response.raise_for_status()
        result = decode_sse(response)
        print(f"响应耗时: {time.time() - started:.2f} 秒\nAgent 响应:\n{result}\n{'=' * 70}")
        return result
    except requests.RequestException as exc:
        print(f"请求失败: {exc}")
        return None


def clear_memory(session_id):
    try:
        response = requests.delete(
            f"{BASE_URL}/memory/{session_id}", headers=auth_headers(), timeout=15
        )
        response.raise_for_status()
        print(f"已清理测试会话 {session_id} 的旧短期记忆")
        return True
    except requests.RequestException as exc:
        print(f"清理测试会话失败: {exc}")
        return False


def test_cli_single_commands():
    scenarios = [
        # (1001, "列出所有可用的 CLI 命令", "命令发现"),
        (1002, "查询第一页工单，每页 3 条，只概括编号、标题和状态", "工单列表"),
    ]
    for session_id, message, description in scenarios:
        call_agent(session_id, message, description)
        time.sleep(1)


def test_short_term_memory_compression():
    """同一会话多轮查询和追问；固定 2K 窗口下应出现压缩且 Agent 能继续响应。

    摘要允许丢弃非关键细节；模型诚实回答不知道或建议重新查询不视为故障。
    """
    session_id = 4001
    if not clear_memory(session_id):
        return False

    turns = [
        "查询第一页工单，每页3条。请告诉我每条工单的ID、编号、标题、状态和优先级。",
        "查看刚才列表中第一条工单的详情，说明描述、创建人、负责人和当前处理信息。",
        "结合前两轮结果，概括第一条工单当前最值得关注的问题，不要重新查询已经得到的信息。",
        "请回忆本次会话最开始查询到的第一条工单编号，并总结到目前为止我们查询过哪些内容。",
    ]

    print(f"\n{'#' * 70}\n短期记忆滚动压缩场景（固定会话 {session_id}）\n{'#' * 70}")
    passed = True
    first_work_order_code = None
    for index, message in enumerate(turns, start=1):
        result = call_agent(session_id, message, f"短期记忆压缩 - 第 {index}/{len(turns)} 轮")
        failure_markers = ("服务暂时不可用", "模型本轮未生成有效内容", "达到最大思考次数")
        if not result or any(marker in result for marker in failure_markers):
            passed = False
            print(
                f"第 {index} 轮失败，停止后续对话。若响应为空，请检查模型 finish_reason；"
                "思考模式下 LLM_MAX_TOKENS 不应低于 1024。"
            )
            break
        if index == 1:
            match = re.search(r"WO\d{12,}", result)
            if not match:
                passed = False
                print("首轮响应中没有找到工单编号，停止测试")
                break
            first_work_order_code = match.group()
        if index == len(turns) and first_work_order_code not in result:
            passed = False
            print(f"最终回忆未保留首轮工单编号 {first_work_order_code}")
        time.sleep(1)

    print("短期记忆长对话场景: " + ("PASS" if passed else "FAIL"))
    print("请在 AiAssistant 日志中过滤 MEMORY-COMPRESSION-SUCCESS 确认实际压缩。")
    return passed


def check_services():
    try:
        response = requests.get(f"{BACKEND_URL}/api/auth/validate", timeout=5)
        if response.status_code >= 500:
            raise requests.RequestException(f"backend HTTP {response.status_code}")
        response = requests.get(f"{BASE_URL}/", timeout=5)
        if response.status_code >= 500:
            raise requests.RequestException(f"AiAssistant HTTP {response.status_code}")
        return True
    except requests.RequestException as exc:
        print(f"服务不可用: {exc}")
        return False


def main():
    print("CLI 工具与短期记忆测试脚本 v5.0")
    if not check_services():
        return 1
    if not login():
        return 1

    # 其他短对话 case 暂停执行，本脚本当前只验证长对话记忆压缩。
    # test_cli_single_commands()
    return 0 if test_short_term_memory_compression() else 1


if __name__ == "__main__":
    sys.exit(main())
