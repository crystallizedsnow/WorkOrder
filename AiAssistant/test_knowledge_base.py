"""
RAG golden set 端到端测试。

使用方式：
    python test_knowledge_base.py

通过注释 SELECTED_CASES 中的行选择需要执行的 case，例如：
    SELECTED_CASES = [
        1,
        # 2,  # 注释后不执行 case 2
        3,
    ]

环境变量：
    WORKORDER_TEST_ACCESS_TOKEN  已有 Access Token，优先使用
    WORKORDER_TEST_PHONE         测试账号手机号
    WORKORDER_TEST_PASSWORD      测试账号密码
    AIASSISTANT_BASE_URL         默认 http://localhost:8081/assistant
    WORKORDER_BACKEND_URL        默认 http://localhost:8080
    RAG_CASE_DELAY_SECONDS       case 间隔，默认 1 秒
"""

import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import requests


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


BASE_URL = os.getenv("AIASSISTANT_BASE_URL", "http://localhost:8081/assistant").rstrip("/")
BACKEND_URL = os.getenv("WORKORDER_BACKEND_URL", "http://localhost:8080").rstrip("/")
GOLDEN_SET = Path(__file__).parent / "src/main/resources/knowledge-base/rag-golden-set.tsv"
CASE_DELAY_SECONDS = float(os.getenv("RAG_CASE_DELAY_SECONDS", "1"))
SESSION_ID_BASE = 840000
AUTH_TOKEN = None


# ============================================================
# Case 选择区：注释掉不需要执行的行即可。
# 编号与 rag-golden-set.tsv 中的问题顺序一致。
# ============================================================
SELECTED_CASES = [
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    # 11,
    # 12,
    # 13,
    # 14,
    # 15,
    # 16,
    # 17,
    # 18,
    # 19,
    # 20,
    # 21,
    # 22,
    # 23,
    # 24,
    # 25,
    # 26,
    # 27,
    # 28,
    # 29,
    # 30,
    # 31,
    # 32,
    # 33,
    # 34,
    # 35,
    # 36,
    # 37,
    # 38,
    # 39,
    # 40,
    # 41,
    # 42,
    # 43,
    # 44,
    # 45,
    # 46,
    # 47,
    # 48,
    # 49,
    # 50,
]


SOURCE_NAMES = {
    "workorder-enums": "工单系统权威枚举",
    "work-order-process": "工单处理流程",
    "system-overview": "工单系统介绍",
    "response-format": "信息展示规范",
}

CITATION_PATTERN = re.compile(r"【来源S\d+】")
QUESTION_STOP_WORDS = {
    "什么", "怎么", "如何", "哪些", "是否", "应该", "采用", "对应", "工单",
    "状态", "操作", "处理", "支持", "一个", "哪个", "是什么", "有哪些",
}


@dataclass(frozen=True)
class GoldenCase:
    number: int
    question: str
    expected_source_id: str


@dataclass(frozen=True)
class CaseResult:
    case: GoldenCase
    passed: bool
    elapsed_seconds: float
    checks: dict
    response: str


def load_golden_set():
    if not GOLDEN_SET.exists():
        raise FileNotFoundError(f"golden set 不存在: {GOLDEN_SET}")

    cases = []
    with GOLDEN_SET.open("r", encoding="utf-8") as stream:
        for line in stream:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            fields = stripped.split("\t")
            if len(fields) != 2:
                raise ValueError(f"非法 golden set 行: {stripped}")
            cases.append(GoldenCase(len(cases) + 1, fields[0].strip(), fields[1].strip()))
    return cases


def select_cases(all_cases):
    selected = []
    seen = set()
    for number in SELECTED_CASES:
        if number in seen:
            raise ValueError(f"SELECTED_CASES 包含重复编号: {number}")
        if number < 1 or number > len(all_cases):
            raise ValueError(f"case 编号超出范围: {number}，有效范围 1..{len(all_cases)}")
        seen.add(number)
        selected.append(all_cases[number - 1])
    return selected


def login():
    global AUTH_TOKEN
    supplied = os.getenv("WORKORDER_TEST_ACCESS_TOKEN", "").strip()
    if supplied:
        AUTH_TOKEN = supplied[7:] if supplied.startswith("Bearer ") else supplied
        print("✓ 已使用 WORKORDER_TEST_ACCESS_TOKEN")
        return True

    phone = os.getenv("WORKORDER_TEST_PHONE", "13812345678").strip()
    password = os.getenv("WORKORDER_TEST_PASSWORD", "newPassword123!").strip()
    if not phone or not password:
        print("✗ 缺少认证信息，请设置 WORKORDER_TEST_ACCESS_TOKEN，或账号密码环境变量")
        return False

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
            print(f"✗ 登录失败: {payload.get('msg') or payload.get('message') or '未返回 accessToken'}")
            return False
        AUTH_TOKEN = data["accessToken"]
        print(f"✓ 登录成功，Access Token 到期时间: {data.get('accessTokenExpiresAt', '未知')}")
        return True
    except (requests.RequestException, ValueError) as exc:
        print(f"✗ 登录请求失败: {exc}")
        return False


def decode_sse(response):
    events = []
    for raw_line in response.iter_lines(decode_unicode=False):
        line = raw_line.decode("utf-8", errors="replace") if raw_line else ""
        if line.startswith("data:"):
            events.append(line[5:].lstrip())
    return "".join(events)


def call_agent(session_id, question):
    headers = {
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "Authorization": f"Bearer {AUTH_TOKEN}",
    }
    response = requests.post(
        f"{BASE_URL}/chat",
        headers=headers,
        json={"memoryId": session_id, "message": question},
        stream=True,
        timeout=180,
    )
    response.raise_for_status()
    return decode_sse(response)


def evaluate_response(case, response):
    response = response or ""
    question_terms = extract_question_terms(case.question)
    matched_terms = [term for term in question_terms if term in response]
    checks = {
        "响应非空": bool(response.strip()),
        "回答与问题有内容关联": bool(matched_terms),
        "未展示行内引用": not CITATION_PATTERN.search(response),
        "未展示可信来源列表": "可信来源：" not in response,
        "未提示知识库不可用": "知识库暂不可用" not in response and "知识库当前尚未就绪" not in response,
        "未返回通用失败": "抱歉，服务暂时不可用" not in response and "模型本轮未生成有效内容" not in response,
    }
    return checks, all(checks.values())


def extract_question_terms(question):
    """提取宽松的内容相关性关键词；精确数字优先，中文按连续词组匹配。"""
    numbers = re.findall(r"\d+", question)
    words = re.findall(r"[\u4e00-\u9fff]{2,}", question)
    terms = list(numbers)
    for word in words:
        if word not in QUESTION_STOP_WORDS:
            terms.append(word)
        # 长问句补充二字片段，避免完整句式与自然语言回答措辞不一致。
        if len(word) >= 4:
            terms.extend(word[index:index + 2] for index in range(0, len(word) - 1, 2))
    return list(dict.fromkeys(term for term in terms if term and term not in QUESTION_STOP_WORDS))


def run_case(case):
    session_id = SESSION_ID_BASE + case.number
    print("\n" + "=" * 80)
    print(f"CASE {case.number:02d}: {case.question}")
    print(f"预期来源: {case.expected_source_id} / {SOURCE_NAMES.get(case.expected_source_id, '未知')}")
    print(f"会话 ID: {session_id}")
    print("-" * 80)

    started = time.time()
    try:
        response = call_agent(session_id, case.question)
        elapsed = time.time() - started
        checks, passed = evaluate_response(case, response)
    except requests.RequestException as exc:
        elapsed = time.time() - started
        response = f"请求失败: {exc}"
        checks = {"请求成功": False}
        passed = False

    print(f"响应耗时: {elapsed:.2f} 秒")
    print("Agent 响应:")
    print(response)
    print("-" * 80)
    for name, success in checks.items():
        print(f"[{'PASS' if success else 'FAIL'}] {name}")
    print(f"CASE {case.number:02d}: {'PASS' if passed else 'FAIL'}")
    return CaseResult(case, passed, elapsed, checks, response)


def check_services():
    try:
        response = requests.get(f"{BASE_URL}/", timeout=5)
        response.raise_for_status()
        print("✓ AiAssistant 服务可用")
    except requests.RequestException as exc:
        print(f"✗ AiAssistant 服务不可用: {exc}")
        return False

    try:
        response = requests.get(f"{BASE_URL.rsplit('/assistant', 1)[0]}/actuator/rag", timeout=5)
        response.raise_for_status()
        status = response.json()
        print(
            f"✓ RAG 状态: {status.get('status')}, "
            f"version={status.get('buildVersion')}, chunks={status.get('chunkCount')}"
        )
        if status.get("status") not in ("READY", "DEGRADED"):
            print("✗ RAG 尚未就绪")
            return False
    except (requests.RequestException, ValueError) as exc:
        print(f"✗ 无法读取 RAG 状态: {exc}")
        return False
    return True


def print_summary(results, total_golden_cases):
    passed = sum(1 for result in results if result.passed)
    failed = len(results) - passed
    print("\n" + "#" * 80)
    print("RAG GOLDEN SET 测试汇总")
    print("#" * 80)
    print(f"golden set 总数: {total_golden_cases}")
    print(f"本次选择执行: {len(results)}")
    print(f"PASS: {passed}")
    print(f"FAIL: {failed}")
    print(f"通过率: {(passed / len(results) * 100) if results else 0:.2f}%")
    if failed:
        print("失败 case:")
        for result in results:
            if not result.passed:
                failed_checks = [name for name, success in result.checks.items() if not success]
                print(f"- CASE {result.case.number:02d}: {result.case.question}；失败项={failed_checks}")
    print("#" * 80)


def main():
    print("=" * 80)
    print("AiAssistant RAG Golden Set 端到端测试")
    print("=" * 80)

    try:
        all_cases = load_golden_set()
        selected_cases = select_cases(all_cases)
    except (OSError, ValueError) as exc:
        print(f"✗ 测试配置错误: {exc}")
        return 2

    if not selected_cases:
        print("未选择任何 case，请在 SELECTED_CASES 中取消注释需要执行的编号")
        return 2
    print(f"已加载 {len(all_cases)} 条 golden case，本次执行 {len(selected_cases)} 条")

    if not check_services() or not login():
        return 2

    results = []
    for index, case in enumerate(selected_cases):
        results.append(run_case(case))
        if index < len(selected_cases) - 1 and CASE_DELAY_SECONDS > 0:
            time.sleep(CASE_DELAY_SECONDS)

    print_summary(results, len(all_cases))
    return 0 if all(result.passed for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
