import requests
import json
import time
import sys
import os

# Windows 控制台默认代码页可能不是 UTF-8；确保测试标题、请求和 Agent 响应一致显示中文。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE_URL = "http://localhost:8081/assistant"
BACKEND_URL = "http://localhost:8080"
AUTH_TOKEN = None


def login():
    """使用新认证契约获取短期 Access Token，不再读取旧 .auth_token 或复用密码文件。"""
    global AUTH_TOKEN
    supplied = os.getenv("WORKORDER_TEST_ACCESS_TOKEN", "").strip()
    if supplied:
        AUTH_TOKEN = supplied[7:] if supplied.startswith("Bearer ") else supplied
        return True

    phone = "13812345678"
    password = "newPassword123!"
    if not phone or not password:
        print("缺少测试凭证：请设置 WORKORDER_TEST_ACCESS_TOKEN，或设置 WORKORDER_TEST_PHONE 和 WORKORDER_TEST_PASSWORD")
        return False
    try:
        response = requests.post(f"{BACKEND_URL}/user/login",
                                 json={"phone": phone, "password": password}, timeout=15)
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") or {}
        if payload.get("code") != 1 or not data.get("accessToken"):
            print(f"登录失败: {payload.get('msg') or payload.get('message') or '未返回 accessToken'}")
            return False
        AUTH_TOKEN = data["accessToken"]
        print(f"登录成功，Access Token 到期时间: {data.get('accessTokenExpiresAt', '未知')}")
        return True
    except (requests.RequestException, ValueError) as exc:
        print(f"登录请求失败: {exc}")
        return False


def decode_sse(response):
    """只提取 SSE data 字段，避免把协议字段或框架对象当作 Agent 正文。"""
    events = []
    # requests 对 text/event-stream 没有可靠的默认 charset，可能按 ISO-8859-1
    # 解码 UTF-8 中文并产生 mojibake。始终按协议实际编码 UTF-8 解码原始字节。
    for raw_line in response.iter_lines(decode_unicode=False):
        line = raw_line.decode("utf-8", errors="replace") if raw_line else ""
        if line.startswith("data:"):
            events.append(line[5:].lstrip())
    return "".join(events)


def call_agent(session_id, message, description=None):
    url = f"{BASE_URL}/chat"
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if AUTH_TOKEN:
        headers["Authorization"] = f"Bearer {AUTH_TOKEN}"
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
        response = requests.post(url, headers=headers, json=payload, stream=True, timeout=180)
        response.raise_for_status()

        result = decode_sse(response)

        elapsed_time = time.time() - start_time

        print(f"响应耗时: {elapsed_time:.2f}秒")
        print(f"-"*70)
        print(f"Agent响应:")
        print(f"{'='*70}")
        print(result)
        print(f"{'='*70}")

        return result

    except requests.exceptions.RequestException as e:
        print(f"请求失败: {e}")
        return None


def check_dry_run_output(result):
    """检查响应中是否包含dry-run预演输出"""
    if result is None:
        return False
    # 检查dry-run标记（SKILL.md中第三步返回string类型的预演结果）
    has_marker = "[dry-run]" in result or "操作类型" in result or "预演" in result
    has_operation = "操作类型" in result or "风险等级" in result
    has_params = "参数详情" in result or "预期影响" in result
    return has_marker or (has_operation and has_params)


def check_execution_result(result):
    """检查响应中是否包含真实执行后的JSON结果"""
    if result is None:
        return False
    # 写操作执行成功后返回归一化JSON，包含code/data字段
    if '"code"' in result and '"data"' in result:
        return True
    # 或包含成功创建/删除等结果提示
    if "创建成功" in result or "处理成功" in result or "删除成功" in result or "审批成功" in result:
        return True
    return False


def check_no_unrelated_followup(result):
    """检查真实执行后没有继续输出无关流程说明"""
    if result is None:
        return False
    unrelated_keywords = [
        "后续确认流程",
        "确认流程",
        "流程说明",
        "确认人",
        "确认通过",
        "确认失败",
        "建议查询",
        "取消工单功能",
        "已取消(700)",
    ]
    return not any(k in result for k in unrelated_keywords)


def check_cancel_message(result):
    """检查响应中是否包含取消确认消息"""
    if result is None:
        return False
    keywords = ["已取消", "取消操作", "不执行", "已停止", "没有执行"]
    return any(k in result for k in keywords)


def check_no_dry_run_in_cancel_response(result):
    """检查取消阶段没有补做dry-run预演"""
    if result is None:
        return False
    dry_run_markers = ["[dry-run]", "执行计划预览", "操作类型", "参数详情"]
    return not any(marker in result for marker in dry_run_markers)


def check_missing_params_error(result, param_name=None):
    """检查响应中是否包含参数缺失错误"""
    if result is None:
        return False
    # CLI返回JSON格式错误: {"code": 4, "message": "xxx参数不能为空"}
    has_error_code = '"code": 4' in result or '"code":4' in result
    has_empty_msg = "不能为空" in result or "参数不能为空" in result or "参数缺失" in result
    if param_name:
        has_param = param_name in result
        return has_error_code and has_empty_msg and has_param
    return has_error_code and has_empty_msg


def check_asking_for_params(result):
    """检查Agent是否在提示用户补充参数"""
    if result is None:
        return False
    keywords = ["缺少", "缺失", "需要", "补充", "填写", "提供", "必填", "不能为空"]
    return any(k in result for k in keywords)


# ============================================================
# 场景1: 用户确认通过执行 (dry-run -> 用户确认 -> 真实执行)
# ============================================================
def test_scenario_1_user_confirm_execute():
    """
    场景1: 用户确认通过执行
    流程: 用户创建工单请求 -> Agent dry-run预演 -> 用户确认执行 -> Agent真实执行
    验证点:
      - 第1轮: Agent返回dry-run预演结果（string类型，包含操作类型/风险等级/参数详情）
      - 第2轮: 用户确认后，Agent去掉--dry-run真实执行，返回JSON结果
    """
    print("\n" + "#"*70)
    print("# 场景1: 用户确认通过执行 (dry-run + confirm + execute)")
    print("#"*70)

    session_id = 7001

    # 第1轮: 用户发送写操作请求，Agent应先dry-run预演
    msg1 = "帮我创建一个工单，类型为需求类，标题'测试工单-确认执行场景'，详情'这是一个确认执行的测试工单'，高优先级，流程ID为2081909482228682752"
    result1 = call_agent(session_id, msg1, "场景1-步骤1: 用户请求创建工单，Agent返回dry-run预演结果")
    time.sleep(5)

    print("\n[检查点1-1] 是否返回dry-run预演结果（string类型，包含操作类型/参数详情）:")
    is_dry_run = check_dry_run_output(result1)
    print(f"  结果: {'PASS' if is_dry_run else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户用结构化JSON确认执行，Agent应去掉--dry-run真实执行
    msg2 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result2 = call_agent(session_id, msg2, "场景1-步骤2: 用户确认执行，Agent真实执行写操作")
    time.sleep(5)

    print("\n[检查点1-2] 用户确认后是否真实执行（返回JSON执行结果）:")
    is_executed = check_execution_result(result2)
    print(f"  结果: {'PASS' if is_executed else 'FAIL'}")
    no_unrelated = check_no_unrelated_followup(result2)
    print(f"  无无关后续说明: {'PASS' if no_unrelated else 'FAIL'}")

    return is_dry_run and is_executed and no_unrelated


# ============================================================
# 场景2: 用户取消执行 (dry-run -> 用户取消 -> 停止执行)
# ============================================================
def test_scenario_2_user_cancel():
    """
    场景2: 用户取消执行
    流程: 用户创建工单请求 -> Agent dry-run预演 -> 用户取消 -> Agent停止，不执行写操作
    验证点:
      - 第1轮: Agent返回dry-run预演结果
      - 第2轮: 用户取消后，Agent确认取消，不发送写请求
    """
    print("\n" + "#"*70)
    print("# 场景2: 用户取消执行 (dry-run + cancel + stop)")
    print("#"*70)

    session_id = 7002

    # 第1轮: 用户发送写操作请求
    msg1 = "帮我创建一个工单，类型为故障类，标题'测试工单-取消场景'，详情'这个工单应该被取消，不会实际创建'，低优先级，流程ID为2081909482228682752"
    result1 = call_agent(session_id, msg1, "场景2-步骤1: 用户请求创建工单，Agent返回dry-run预演")
    time.sleep(5)

    print("\n[检查点2-1] 是否返回dry-run预演结果:")
    is_dry_run = check_dry_run_output(result1)
    print(f"  结果: {'PASS' if is_dry_run else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户用结构化JSON取消执行
    msg2 = '{"action":"cancel_execute","target":"last_dry_run","confirmed":false}'
    result2 = call_agent(session_id, msg2, "场景2-步骤2: 用户取消，Agent确认取消操作")
    time.sleep(3)

    print("\n[检查点2-2] 用户取消后，Agent是否确认取消（不执行写操作）:")
    is_cancelled = check_cancel_message(result2)
    print(f"  结果: {'PASS' if is_cancelled else 'FAIL'}")
    no_cancel_dry_run = check_no_dry_run_in_cancel_response(result2)
    print(f"  取消阶段无补做预演: {'PASS' if no_cancel_dry_run else 'FAIL'}")

    return is_dry_run and is_cancelled and no_cancel_dry_run


# ============================================================
# 场景3: 用户修改参数后执行 (dry-run -> 用户改参数 -> 重新dry-run -> 确认执行)
# ============================================================
def test_scenario_3_modify_params():
    """
    场景3: 用户修改参数后执行
    流程:
      用户创建工单请求(高优先级) -> Agent dry-run预演(高优先级)
      -> 用户说'优先级改成中，标题改一下' -> Agent重新dry-run预演(修改后的参数)
      -> 用户确认执行 -> Agent真实执行(修改后的参数)
    验证点:
      - 第1轮: 返回初次dry-run预演结果（高优先级）
      - 第2轮: 用户修改参数后，Agent重新dry-run，参数详情中优先级变为中，标题更新
      - 第3轮: 用户确认后真实执行，返回JSON结果
    """
    print("\n" + "#"*70)
    print("# 场景3: 用户修改参数后执行 (dry-run -> modify params -> redry-run -> confirm -> execute)")
    print("#"*70)

    session_id = 7003

    # 第1轮: 用户初始请求（高优先级，标题A）
    msg1 = "帮我创建一个工单，标题'测试工单-参数修改前'，类型需求类，详情'初始参数'，高优先级，流程ID为2081909482228682752"
    result1 = call_agent(session_id, msg1, "场景3-步骤1: 初始请求，dry-run返回高优先级")
    time.sleep(5)

    print("\n[检查点3-1] 初次dry-run预演是否成功（返回预演结果）:")
    dry_run_1 = check_dry_run_output(result1)
    if dry_run_1 and result1:
        has_high_priority = "高" in result1 and ("优先级" in result1 or "priority" in result1.lower())
        print(f"  预演成功: {'PASS' if dry_run_1 else 'FAIL'}")
        print(f"  包含高优先级: {'PASS' if has_high_priority else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户修改参数（优先级改为中，标题改为新标题，详情也修改）
    msg2 = "把优先级改成中优先级，标题改成'测试工单-参数修改后'，详情改为'参数被修改了'，重新预览"
    result2 = call_agent(session_id, msg2, "场景3-步骤2: 用户修改参数，Agent重新dry-run（中优先级）")
    time.sleep(5)

    print("\n[检查点3-2] 修改参数后，重新dry-run的参数是否更新:")
    dry_run_2 = check_dry_run_output(result2)
    if dry_run_2 and result2:
        has_mid_priority = "中" in result2 and ("优先级" in result2 or "priority" in result2.lower())
        has_new_title = "参数修改后" in result2
        print(f"  重新预演成功: {'PASS' if dry_run_2 else 'FAIL'}")
        print(f"  优先级更新为中: {'PASS' if has_mid_priority else 'FAIL'}")
        print(f"  标题更新为新标题: {'PASS' if has_new_title else 'FAIL'}")
    time.sleep(2)

    # 第3轮: 用户用结构化JSON确认执行（使用修改后的参数）
    msg3 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result3 = call_agent(session_id, msg3, "场景3-步骤3: 用户确认，Agent用修改后的参数执行")
    time.sleep(5)

    print("\n[检查点3-3] 修改参数后确认执行是否成功:")
    executed = check_execution_result(result3)
    print(f"  执行结果: {'PASS' if executed else 'FAIL'}")

    return dry_run_1 and dry_run_2 and executed


# ============================================================
# 场景4: 用户修改命令类型后执行 (dry-run创建工单 -> 用户改为删除工单 -> 重新dry-run删除 -> 确认删除)
# ============================================================
def test_scenario_4_modify_command():
    """
    场景4: 用户修改命令类型后执行
    流程:
      用户说'帮我创建工单xxx' -> Agent dry-run预演(work_order_create)
      -> 用户说'不对，我不是创建，是删除工单WO202607290001' -> Agent改为work_order_delete，重新查schema，重新dry-run预演(删除操作)
      -> 用户确认删除 -> Agent真实执行删除
    验证点:
      - 第1轮: 返回创建工单的dry-run预演
      - 第2轮: 用户修改命令为删除，Agent重新返回删除工单的dry-run（操作类型变为删除工单，风险等级高）
      - 第3轮: 用户确认删除后真实执行删除，返回JSON结果
    """
    print("\n" + "#"*70)
    print("# 场景4: 修改命令类型 (create-dryrun -> switch to delete -> delete-dryrun -> confirm delete)")
    print("#"*70)

    session_id = 7004

    # 第1轮: 用户初始说创建工单（后续要改成删除）
    msg1 = "帮我创建一个工单，标题'误操作-应该是删除不是创建'，类型需求类，高优先级，流程ID为2081909482228682752"
    result1 = call_agent(session_id, msg1, "场景4-步骤1: 用户初始要求创建，Agent dry-run创建工单")
    time.sleep(5)

    print("\n[检查点4-1] 初次dry-run预演（创建工单）是否成功:")
    dry_run_1 = check_dry_run_output(result1)
    if dry_run_1 and result1:
        is_create = "创建工单" in result1 or "work_order_create" in result1
        print(f"  预演成功: {'PASS' if dry_run_1 else 'FAIL'}")
        print(f"  操作类型为创建工单: {'PASS' if is_create else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户改命令，不创建了，改为删除指定工单
    msg2 = "不对，我不要创建工单了。改成删除工单，工单编号是WO202608112087191158408220672，重新预览"
    result2 = call_agent(session_id, msg2, "场景4-步骤2: 用户改为删除工单，Agent重新dry-run删除操作")
    time.sleep(5)

    print("\n[检查点4-2] 修改命令为删除后，重新dry-run是否为删除操作:")
    dry_run_2 = check_dry_run_output(result2)
    if dry_run_2 and result2:
        is_delete = "删除工单" in result2 or "work_order_delete" in result2
        is_high_risk = "高" in result2 and ("风险" in result2 or "不可逆" in result2)
        print(f"  重新预演成功: {'PASS' if dry_run_2 else 'FAIL'}")
        print(f"  操作类型变为删除工单: {'PASS' if is_delete else 'FAIL'}")
        print(f"  删除操作风险等级为高: {'PASS' if is_high_risk else 'FAIL'}")
    time.sleep(2)

    # 第3轮: 用户用结构化JSON确认删除（注意：如果怕真实删除数据，可在此步骤改为取消）
    msg3 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result3 = call_agent(session_id, msg3, "场景4-步骤3: 用户确认删除，Agent执行删除")
    time.sleep(5)

    print("\n[检查点4-3] 确认删除后执行是否成功（或返回删除结果）:")
    executed = check_execution_result(result3)
    print(f"  执行结果: {'PASS' if executed else 'FAIL'}")

    return dry_run_1 and dry_run_2 and executed


# ============================================================
# 场景5: 复合操作 - 创建后确认审批人并审批（链式多工具调用）
# ============================================================
def test_scenario_5_chain_operations():
    """
    场景5: 创建工单后查询审批流程，确认当前用户是审批人，再审批工单
    流程:
      用户请求创建工单，并要求创建成功后查询审批流程并审批
      -> Agent dry-run创建工单预演
      -> 用户确认创建
      -> Agent真实执行创建
      -> 用户要求继续剩余任务
      -> Agent查询工单详情和流程详情，确认未完成审批节点属于当前登录用户
      -> Agent查询审批Schema并执行审批dry-run
      -> 用户确认审批
      -> Agent真实执行审批
    验证点:
      - 创建写操作的dry-run和确认正常工作
      - Agent按通用长任务规则使用todo工具管理阶段（通过Agent工具调用日志核验）
      - 审批前先查询审批链，并确认该工单轮到当前用户审批
      - 审批写操作同样经过dry-run和独立确认
    """
    print("\n" + "#"*70)
    print("# 场景5: 复合操作 - 创建+确认审批人+审批")
    print("#"*70)

    session_id = 7505

    msg1 = "帮我创建一个工单，标题'链式测试-创建后审批'，类型故障类，中优先级，详情'链式操作测试'，流程ID为2081909482228682752。创建成功后先查询审批流程和工单当前审批信息，确认该工单当前确实由我审批，然后审批通过，审批意见为'场景5链路测试通过'。如果这是长任务，必须先调用todo工具列举并跟踪各阶段任务。"
    result1 = call_agent(session_id, msg1, "场景5-步骤1: 请求创建工单，dry-run预演")
    time.sleep(5)
    print("[检查点5-1] 创建工单dry-run预演:", "PASS" if check_dry_run_output(result1) else "FAIL")
    time.sleep(2)

    msg2 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result2 = call_agent(session_id, msg2, "场景5-步骤2: 确认创建工单")
    time.sleep(5)
    print("[检查点5-2] 创建工单执行结果:", "PASS" if check_execution_result(result2) else "FAIL")
    time.sleep(2)

    # 真实创建是确认分支的终止点；下一轮继续查询并预演审批。
    msg3 = "继续完成剩余任务：先查询流程ID 2081909482228682752的审批节点，再查询刚创建工单的当前审批信息，并结合当前登录用户确认该工单当前确实轮到我审批。只有确认属于我审批后，才查询审批Schema并预演审批通过，审批意见为'场景5链路测试通过'；如果不属于我审批，停止并明确说明，不要预演或执行审批。"
    result3 = call_agent(session_id, msg3, "场景5-步骤3: 查询审批链、确认当前审批人并预演审批")
    time.sleep(5)
    approval_dry_run = check_dry_run_output(result3) and ("审批" in result3 or "审核" in result3)
    ownership_checked = any(marker in result3 for marker in ["当前登录用户", "当前用户", "由你审批", "轮到你", "审批人"])
    did_not_execute_approval = "审批成功" not in result3 and "审核成功" not in result3
    print("[检查点5-3] 已查询审批链并核对当前审批人:", "PASS" if ownership_checked else "FAIL")
    print("[检查点5-4] 审批dry-run预演且尚未真实审批:", "PASS" if approval_dry_run and did_not_execute_approval else "FAIL")

    if not approval_dry_run:
        print("[检查点5-5] 未进入审批确认阶段，跳过真实审批: FAIL")
        return False

    msg4 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result4 = call_agent(session_id, msg4, "场景5-步骤4: 确认并执行审批")
    time.sleep(5)
    approval_executed = check_execution_result(result4)
    print("[检查点5-5] 审批执行结果:", "PASS" if approval_executed else "FAIL")

    return (check_dry_run_output(result1) and check_execution_result(result2)
            and ownership_checked and approval_dry_run and did_not_execute_approval
            and approval_executed)


# ============================================================
# 场景6: 缺少必填参数 - Agent提示用户补参后继续执行
# ============================================================
def test_scenario_6_missing_params():
    """
    场景6: 缺少必填参数
    流程:
      用户请求创建工单(缺少flowId参数)
      -> Agent组装命令时缺少必填参数 -> CLI返回JSON错误(code=4, message=参数不能为空)
      -> Agent识别错误，提示用户补充缺失的参数
      -> 用户补充参数(flowId) -> Agent重新执行dry-run -> 用户确认 -> 真实执行
    验证点:
      - 第1轮: CLI返回JSON格式错误(code=4)，Agent提示缺少参数
      - 第2轮: 用户补充参数后，Agent重新执行dry-run预演（成功）
      - 第3轮: 用户确认后真实执行
    """
    print("\n" + "#"*70)
    print("# 场景6: 缺少必填参数 - 提示补参后继续执行")
    print("#"*70)

    session_id = 7006

    # 第1轮: 用户请求创建工单，但缺少flowId参数
    # create的必填参数: type, title, content, priorityLevel, flowId
    msg1 = "帮我创建一个工单，类型为需求类，标题'缺参测试工单'，详情'测试缺少flowId参数的场景'，高优先级"
    result1 = call_agent(session_id, msg1, "场景6-步骤1: 用户请求创建工单(缺flowId)")
    time.sleep(5)

    print("\n[检查点6-1] 缺少必填参数时，CLI返回错误(code=4)且Agent提示补充参数:")
    has_cli_error = check_missing_params_error(result1, "flowId")
    if not has_cli_error:
        has_cli_error = check_missing_params_error(result1, "flow")
    agent_asks = check_asking_for_params(result1)
    print(f"  CLI返回参数错误(code=4): {'PASS' if has_cli_error else 'FAIL'}")
    print(f"  Agent提示补充参数: {'PASS' if agent_asks else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户补充缺失的flowId参数
    msg2 = "补充流程ID为2081909482228682752，重新预演"
    result2 = call_agent(session_id, msg2, "场景6-步骤2: 用户补充flowId参数，重新dry-run")
    time.sleep(5)

    print("\n[检查点6-2] 补充参数后，dry-run预演是否成功:")
    dry_run_ok = check_dry_run_output(result2)
    print(f"  重新dry-run预演: {'PASS' if dry_run_ok else 'FAIL'}")
    time.sleep(2)

    # 第3轮: 用户用结构化JSON确认执行
    msg3 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result3 = call_agent(session_id, msg3, "场景6-步骤3: 用户确认执行")
    time.sleep(5)

    print("\n[检查点6-3] 补充参数后确认执行是否成功:")
    executed = check_execution_result(result3)
    print(f"  执行结果: {'PASS' if executed else 'FAIL'}")

    return has_cli_error and agent_asks and dry_run_ok and executed


# ============================================================
# 场景7: 多个参数缺失 - 逐步补充
# ============================================================
def test_scenario_7_multiple_missing_params():
    """
    场景7: 多个参数缺失
    流程:
      用户请求创建工单(缺少title, content, priorityLevel, flowId)
      -> CLI返回错误，Agent提示缺少参数
      -> 用户只补充title -> 还缺少其他参数 -> CLI继续报错
      -> 用户继续补充 -> 直到所有参数完整 -> dry-run -> 确认执行
    验证点:
      - 每轮Agent都能正确识别缺失的参数并提示
      - 用户补充后能继续流程
    """
    print("\n" + "#"*70)
    print("# 场景7: 多个参数缺失 - 逐步补充")
    print("#"*70)

    session_id = 7007

    # 第1轮: 只提供type，缺少title, content, priorityLevel, flowId
    msg1 = "帮我创建一个工单，类型为故障类"
    result1 = call_agent(session_id, msg1, "场景7-步骤1: 只提供type，缺多个参数")
    time.sleep(5)

    print("\n[检查点7-1] 多个参数缺失时，Agent是否识别并提示:")
    has_error1 = check_missing_params_error(result1)
    asks_params1 = check_asking_for_params(result1)
    print(f"  CLI返回错误: {'PASS' if has_error1 else 'FAIL'}")
    print(f"  Agent提示补充: {'PASS' if asks_params1 else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 补充title
    msg2 = "补充标题为'多参数缺失测试工单'"
    result2 = call_agent(session_id, msg2, "场景7-步骤2: 补充title")
    time.sleep(5)

    print("\n[检查点7-2] 补充title后是否继续提示缺少其他参数:")
    has_error2 = check_missing_params_error(result2)
    asks_params2 = check_asking_for_params(result2)
    print(f"  仍有参数缺失提示: {'PASS' if has_error2 or asks_params2 else 'FAIL'}")
    time.sleep(2)

    # 第3轮: 继续补充参数
    msg3 = "补充详情为'逐步补充参数的测试'，中优先级，流程ID为2081909482228682752，重新预演"
    result3 = call_agent(session_id, msg3, "场景7-步骤3: 补充剩余参数")
    time.sleep(5)

    print("\n[检查点7-3] 参数完整后dry-run是否成功:")
    dry_run_ok = check_dry_run_output(result3)
    print(f"  dry-run预演: {'PASS' if dry_run_ok else 'FAIL'}")
    time.sleep(2)

    # 第4轮: 用户用结构化JSON确认执行
    msg4 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result4 = call_agent(session_id, msg4, "场景7-步骤4: 用户确认执行")
    time.sleep(5)

    print("\n[检查点7-4] 确认后是否成功执行:")
    executed = check_execution_result(result4)
    print(f"  执行结果: {'PASS' if executed else 'FAIL'}")

    return True


# ============================================================
# 场景8: handle命令缺少id/code - 二选一验证
# ============================================================
def test_scenario_8_missing_id_or_code():
    """
    场景8: handle命令缺少id/code
    流程:
      用户请求处理工单但没提供id或code
      -> CLI返回错误: "必须指定id或code参数"
      -> Agent提示用户提供工单id或编号
      -> 用户补充code -> 重新dry-run -> 确认执行
    """
    print("\n" + "#"*70)
    print("# 场景8: handle命令缺少id/code - 二选一验证")
    print("#"*70)

    session_id = 7008

    # 第1轮: 只提供handleType，不提供id/code
    msg1 = "帮我完成工单的处理（handle-type为4，已处理完成）"
    result1 = call_agent(session_id, msg1, "场景8-步骤1: 缺id/code，只有handleType")
    time.sleep(5)

    print("\n[检查点8-1] 缺少id/code时CLI返回错误:")
    has_error = check_missing_params_error(result1)
    asks_id = check_asking_for_params(result1)
    print(f"  CLI返回错误(code=4): {'PASS' if has_error else 'FAIL'}")
    print(f"  Agent提示提供工单标识: {'PASS' if asks_id else 'FAIL'}")
    time.sleep(2)

    # 第2轮: 用户补充code
    msg2 = "工单编号是WO202607290001，重新预演"
    result2 = call_agent(session_id, msg2, "场景8-步骤2: 用户补充code")
    time.sleep(5)

    print("\n[检查点8-2] 补充code后dry-run是否成功:")
    dry_run_ok = check_dry_run_output(result2)
    print(f"  dry-run预演: {'PASS' if dry_run_ok else 'FAIL'}")
    time.sleep(2)

    # 第3轮: 用户用结构化JSON确认执行
    msg3 = '{"action":"confirm_execute","target":"last_dry_run","confirmed":true}'
    result3 = call_agent(session_id, msg3, "场景8-步骤3: 用户确认执行")
    time.sleep(5)

    print("\n[检查点8-3] 确认后是否成功执行:")
    executed = check_execution_result(result3)
    print(f"  执行结果: {'PASS' if executed else 'FAIL'}")

    return True


def main():
    print("="*70)
    print("CLI写服务测试脚本 - Agent层写操作交互场景")
    print("版本: v3.0 - 覆盖确认/取消/改参数/改命令/缺参数 5类交互场景")
    print("="*70)
    print("")
    print("测试场景总览:")
    print("  场景1 (7001): 用户确认通过执行  - dry-run + 确认 + 真实执行")
    print("  场景2 (7002): 用户取消执行      - dry-run + 取消 + 不执行")
    print("  场景3 (7003): 用户修改参数后执行 - dry-run + 改参数 + 重新预演 + 确认 + 执行")
    print("  场景4 (7004): 用户修改命令类型   - 创建预演 + 改删除 + 删除预演 + 确认删除")
    print("  场景5 (7505): 复合链式操作      - 创建 + 查询审批流程 + 确认本人审批 + 审批")
    print("  场景6 (7006): 缺少必填参数     - CLI报错(code=4) + Agent提示补参 + 用户补参后执行")
    print("  场景7 (7007): 多个参数缺失     - 逐步补充，每轮验证直到参数完整")
    print("  场景8 (7008): handle缺id/code    - 验证二选一参数校验逻辑")
    print("")
    print("写操作流程遵循 SKILL.md 规定:")
    print("  Step1: list获取dataCode列表")
    print("  Step2: schema获取参数Schema")
    print("  Step3: dry-run预演（写操作强制，返回string类型预演结果）")
    print("         注意: 参数校验在dry-run之前，缺参数时CLI直接返回JSON错误(code=4)")
    print("  Step4: 用户确认 -> 去掉--dry-run真实执行 / 用户取消 -> 停止 / 修改 -> 重新预演")
    print("  缺参数处理: CLI返回JSON错误 -> Agent识别后提示用户补充 -> 用户补参后重试")
    print("")

    print("\n正在检查服务是否可用...")
    try:
        response = requests.get(f"{BASE_URL}/", timeout=5)
        print("AiAssistant服务可用 (端口8081)")
    except requests.exceptions.RequestException:
        print("AiAssistant服务不可用，请先启动服务")
        print("启动命令: cd AiAssistant && mvn spring-boot:run")
        sys.exit(1)

    try:
        response = requests.get(f"{BACKEND_URL}/", timeout=5)
        print("Backend服务可用 (端口8080)")
    except requests.exceptions.RequestException:
        print("警告: Backend服务不可用 (端口8080)，部分写操作可能失败")

    try:
        response = requests.get("http://localhost:5000/", timeout=5)
        print("cli-service服务可用 (端口5000)")
    except requests.exceptions.RequestException:
        print("警告: cli-service服务不可用 (端口5000)，写命令执行可能失败")

    print("\n" + "="*70)
    print("开始执行测试场景")
    print("="*70)

    if not login():
        print("鉴权准备失败，终止测试；不会把 401 响应误判为 Agent 结果。")
        sys.exit(2)

    # 执行统计
    results = {}

    # ---- 场景1: 用户确认通过执行 ----
    # print("\n" + "*"*70)
    # print("* 执行场景1: 用户确认通过执行")
    # print("*"*70)
    # r1 = test_scenario_1_user_confirm_execute()
    # results["场景1-确认执行"] = r1
    # time.sleep(3)

    # # ---- 场景2: 用户取消执行 ----
    # print("\n" + "*"*70)
    # print("* 执行场景2: 用户取消执行")
    # print("*"*70)
    # r2 = test_scenario_2_user_cancel()
    # results["场景2-取消执行"] = r2
    # time.sleep(3)

    # ---- 场景3: 用户修改参数后执行 ----
    print("\n" + "*"*70)
    print("* 执行场景3: 用户修改参数后执行")
    print("*"*70)
    r3 = test_scenario_3_modify_params()
    results["场景3-修改参数"] = r3
    time.sleep(3)

    # ---- 场景4: 用户修改命令类型后执行 ----
    # print("\n" + "*"*70)
    # print("* 执行场景4: 用户修改命令类型后执行")
    # print("*"*70)
    # r4 = test_scenario_4_modify_command()
    # results["场景4-修改命令"] = r4
    # time.sleep(3)

    # ---- 场景5: 创建后确认审批人并审批 ----
    # print("\n" + "*"*70)
    # print("* 执行场景5: 创建后确认审批人并审批")
    # print("*"*70)
    # r5 = test_scenario_5_chain_operations()
    # results["场景5-创建并审批"] = r5
    # time.sleep(3)

    # ---- 场景6: 缺少必填参数 ----
    # print("\n" + "*"*70)
    # print("* 执行场景6: 缺少必填参数")
    # print("*"*70)
    # r6 = test_scenario_6_missing_params()
    # results["场景6-缺参数"] = r6
    # time.sleep(3)

    # ---- 场景7: 多个参数缺失 ----
    # print("\n" + "*"*70)
    # print("* 执行场景7: 多个参数缺失")
    # print("*"*70)
    # r7 = test_scenario_7_multiple_missing_params()
    # results["场景7-多参数缺失"] = r7
    # time.sleep(3)

    # ---- 场景8: handle缺id/code ----
    # print("\n" + "*"*70)
    # print("* 执行场景8: handle缺id/code")
    # print("*"*70)
    # r8 = test_scenario_8_missing_id_or_code()
    # results["场景8-handle缺id"] = r8

    # ---- 测试汇总 ----
    print("\n" + "="*70)
    print("测试结果汇总")
    print("="*70)
    if results:
        for name, passed in results.items():
            status = "PASS" if passed else "FAIL"
            print(f"  {name}: {status}")
    else:
        print("  所有场景默认已注释，未实际执行。")
        print("")
        print("  使用说明:")
        print("    1. 根据需要取消注释 main() 函数中的场景调用")
        print("    2. 建议按顺序逐个场景测试，避免写操作冲突")
        print("    3. 场景2(取消执行)、场景6-8(缺参数)不产生真实数据，可优先运行")
        print("    4. 场景1/3/5会创建真实工单，注意数据清理")
        print("    5. 场景4(修改命令-删除)会删除数据，请确认工单存在后再运行")
        print("    6. 缺参数场景(6/7/8)验证CLI在dry-run前的参数校验逻辑")

    print("\n" + "="*70)
    print("脚本执行结束")
    print("="*70)
    print("")
    print("SKILL.md 写操作流程回顾:")
    print("  完整流程 = 参数校验 -> dry-run预演 -> 用户确认 -> 真实执行")
    print("  缺参数时: CLI返回JSON错误(code=4) -> Agent提示补参 -> 用户补参重试")
    print("  参数完整时: dry-run返回纯文本string预演结果 -> 用户确认 -> 返回JSON执行结果")


if __name__ == "__main__":
    main()
