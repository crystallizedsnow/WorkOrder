import requests
import json
import time
import sys
import os

BASE_URL = "http://localhost:8081/assistant"
AUTH_TOKEN = None
TOKEN_FILE = ".auth_token"


def load_token():
    global AUTH_TOKEN
    if os.path.exists(TOKEN_FILE):
        try:
            with open(TOKEN_FILE, 'r') as f:
                AUTH_TOKEN = f.read().strip()
                print(f"✓ 已加载缓存的Token")
                return True
        except Exception:
            pass
    return False


def login():
    global AUTH_TOKEN
    if load_token():
        return True
    return False


def call_agent(session_id, message):
    url = f"{BASE_URL}/chat"
    headers = {"Content-Type": "application/json"}
    if AUTH_TOKEN:
        headers["Authorization"] = AUTH_TOKEN
    
    payload = {"memoryId": session_id, "message": message}
    
    try:
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
        
        return result
    except requests.exceptions.RequestException as e:
        print(f"✗ 请求失败: {e}")
        return None


def test_workorder_status():
    print("\n" + "="*70)
    print("测试场景1: 查询工单状态码含义")
    print("="*70)
    
    result = call_agent(4001, "工单状态200是什么意思？")
    if result:
        print(f"响应:\n{result}")
        if "审核" in result or "200" in result:
            print("✓ 状态码查询成功")
        else:
            print("✗ 状态码查询失败")


def test_workorder_process():
    print("\n" + "="*70)
    print("测试场景2: 查询工单处理流程")
    print("="*70)
    
    result = call_agent(4002, "工单处理流程是怎样的？")
    if result:
        print(f"响应:\n{result}")
        if "流程" in result or "处理" in result:
            print("✓ 流程查询成功")
        else:
            print("✗ 流程查询失败")


def test_workorder_types():
    print("\n" + "="*70)
    print("测试场景3: 查询工单类型和优先级")
    print("="*70)
    
    result = call_agent(4003, "工单有哪些类型和优先级？")
    if result:
        print(f"响应:\n{result}")
        if "类型" in result or "优先级" in result:
            print("✓ 类型查询成功")
        else:
            print("✗ 类型查询失败")


def test_workorder_priority():
    print("\n" + "="*70)
    print("测试场景4: 查询紧急优先级含义")
    print("="*70)
    
    result = call_agent(4004, "紧急优先级是什么意思？")
    if result:
        print(f"响应:\n{result}")
        if "紧急" in result or "priority" in result.lower():
            print("✓ 优先级查询成功")
        else:
            print("✗ 优先级查询失败")


def test_knowledge_search():
    print("\n" + "="*70)
    print("测试场景5: 搜索知识库")
    print("="*70)
    
    result = call_agent(4005, "搜索关于工单审核的信息")
    if result:
        print(f"响应:\n{result}")
        if "审核" in result:
            print("✓ 知识库搜索成功")
        else:
            print("✗ 知识库搜索失败")


def main():
    print("="*70)
    print("知识库测试脚本")
    print("版本: v1.0")
    print("="*70)
    
    print("\n正在检查服务是否可用...")
    try:
        response = requests.get(f"{BASE_URL}/", timeout=5)
        print("✓ AiAssistant服务可用")
    except requests.exceptions.RequestException:
        print("✗ AiAssistant服务不可用，请先启动服务")
        sys.exit(1)
    
    login()
    
    test_workorder_status()
    time.sleep(2)
    
    test_workorder_process()
    time.sleep(2)
    
    test_workorder_types()
    time.sleep(2)
    
    test_workorder_priority()
    time.sleep(2)
    
    test_knowledge_search()
    
    print("\n" + "="*70)
    print("知识库测试完成")
    print("="*70)


if __name__ == "__main__":
    main()