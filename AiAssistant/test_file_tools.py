import requests
import json
import time
import sys
import os

BASE_URL = "http://localhost:8081/assistant"


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


def test_single_file_operations():
    print("\n" + "="*70)
    print("【单个文件操作测试】")
    print("="*70)

    call_agent(1001, "创建一个test_file.txt文件，内容是Hello File Tools")
    time.sleep(2)
    call_agent(1002, "读取test_file.txt文件")
    time.sleep(2)
    call_agent(1003, "创建一个test_report.md文件，内容是# 测试报告")
    time.sleep(2)


def test_cli_file_compound():
    print("\n" + "="*70)
    print("【CLI+文件复合场景测试】")
    print("="*70)

    call_agent(2001, "查询第一页工单信息，每页3条，然后将结果导出为Excel文件")
    time.sleep(3)
    call_agent(2002, "查询工单ID为28的详情，然后生成一个Markdown格式的工单报告文件")
    time.sleep(3)


def main():
    print("="*70)
    print("文件操作测试脚本")
    print("版本: v3.0 - 直接输出结果，不做关键词匹配")
    print("="*70)

    print("\n正在检查服务是否可用...")
    try:
        response = requests.get(f"{BASE_URL}/", timeout=5)
        print("AiAssistant服务可用")
    except requests.exceptions.RequestException:
        print("AiAssistant服务不可用，请先启动服务")
        sys.exit(1)

    test_single_file_operations()
    test_cli_file_compound()

    print("\n" + "="*70)
    print("测试完成")
    print("="*70)


if __name__ == "__main__":
    main()
