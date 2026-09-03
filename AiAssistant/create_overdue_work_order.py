import json
import os
import subprocess
import sys
import time

DB_HOST = os.getenv("WORKORDER_TEST_DB_HOST", "127.0.0.1")
DB_PORT = os.getenv("WORKORDER_TEST_DB_PORT", "3306")
DB_USER = os.getenv("WORKORDER_TEST_DB_USER", "root")
DB_PASSWORD = os.getenv("WORKORDER_TEST_DB_PASSWORD", "123456")
DB_NAME = os.getenv("WORKORDER_TEST_DB_NAME", "wos")
WAIT_SECONDS = int(os.getenv("WORKORDER_OVERDUE_TEST_WAIT_SECONDS", "90"))


def decode_console_output(value):
    if not value:
        return ""
    for encoding in ("utf-8", "gb18030", "mbcs"):
        try:
            return value.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    return value.decode("utf-8", errors="replace")


def mysql(sql):
    env = os.environ.copy()
    env["MYSQL_PWD"] = DB_PASSWORD
    command = [
        "mysql", "--default-character-set=utf8mb4", "-h", DB_HOST,
        "-P", DB_PORT, "-u", DB_USER, "-D", DB_NAME,
        "--batch", "--skip-column-names", "-e", sql,
    ]
    result = subprocess.run(command, env=env, capture_output=True)
    stdout = decode_console_output(result.stdout)
    stderr = decode_console_output(result.stderr)
    if result.returncode:
        raise RuntimeError(stderr.strip() or f"mysql exited with code {result.returncode}")
    return stdout.strip()


def select_handler():
    row = mysql("""
        SELECT s.id, s.name, s.company_code, COALESCE(s.company, '-'),
               COALESCE(s.department_code, '-'), COALESCE(s.department, '-'),
               CASE WHEN b.id IS NULL THEN 0 ELSE 1 END AS has_feishu_binding
        FROM staff s
        LEFT JOIN external_identity_binding b
          ON b.user_id = s.id AND b.platform = 'FEISHU' AND b.status = 'ACTIVE'
         AND b.open_id IS NOT NULL AND b.open_id <> ''
        WHERE s.status = 0
          AND s.company_code IS NOT NULL
          AND s.department_code IS NOT NULL
        ORDER BY has_feishu_binding DESC, s.id
        LIMIT 1
    """)
    if not row:
        raise RuntimeError("没有找到可作为工单处理人的正常员工")
    return row.split("\t")


def create_overdue_order():
    user_id, name, company_code, company, department_code, department, has_binding = select_handler()
    suffix = str(int(time.time() * 1000))
    code = "WO-OVERDUE-" + suffix
    title = "超时扫描自动化验证-" + suffix
    flow_id = mysql("SELECT COALESCE(MIN(flow_id), 0) FROM flow") or "0"
    sql = f"""
        START TRANSACTION;
        INSERT INTO work_order(code,type,title,priority_level,status,flow_id,deadline_time,content,deleted)
        VALUES('{code}',1,'{title}',0,400,{flow_id},DATE_SUB(NOW(), INTERVAL 2 MINUTE),
               '30秒超时扫描自动化测试数据',0);
        SET @order_id=LAST_INSERT_ID();
        INSERT INTO handle_user_info(order_id,user_id,user_name,handle_type,finished,
            company_code,company_name,department_code,department_name,remark,deleted)
        VALUES(@order_id,{user_id},'{name}',4,0,'{company_code}','{company}',
            '{department_code}','{department}','等待超时扫描',0);
        COMMIT;
        SELECT @order_id,'{code}',{user_id},{has_binding},
               DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 MINUTE),'%Y-%m-%d %H:%i:%s');
    """
    output = mysql(sql)
    order_id, created_code, handler_id, binding, deadline = output.split("\t")[-5:]
    return {
        "workOrderId": int(order_id),
        "workOrderCode": created_code,
        "handlerUserId": int(handler_id),
        "hasFeishuBinding": binding == "1",
        "deadlineTime": deadline,
    }


def wait_for_result(order):
    started = time.monotonic()
    deadline = started + WAIT_SECONDS
    last = None
    while time.monotonic() < deadline:
        row = mysql(f"""
            SELECT w.status,
                   (SELECT COUNT(*) FROM notification_delivery d
                     WHERE d.work_order_id=w.id AND d.event_type='WORK_ORDER_DELAYED') AS delivery_count,
                   (SELECT COALESCE(GROUP_CONCAT(DISTINCT d.status ORDER BY d.status), '-')
                      FROM notification_delivery d WHERE d.work_order_id=w.id) AS delivery_status,
                   (SELECT COUNT(*) FROM message m
                     WHERE m.receiver_id={order['handlerUserId']}
                       AND m.content LIKE CONCAT('%', w.code, '%')) AS message_count
            FROM work_order w WHERE w.id={order['workOrderId']}
        """)
        if row:
            status, delivery_count, delivery_status, message_count = row.split("\t")
            last = {
                "status": int(status),
                "deliveryCount": int(delivery_count),
                "deliveryStatus": delivery_status,
                "messageCount": int(message_count),
            }
            if last["status"] == 410 and last["deliveryCount"] > 0 and last["messageCount"] > 0:
                last["elapsedSeconds"] = round(time.monotonic() - started, 2)
                return last
        time.sleep(2)
    raise RuntimeError(f"等待超时提醒超过{WAIT_SECONDS}秒，最后状态: {last}")


def main():
    order = create_overdue_order()
    print("已创建超时测试工单：")
    print(json.dumps(order, ensure_ascii=False, indent=2))
    result = wait_for_result(order)
    print("超时扫描验证通过：")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"验证失败: {exc}", file=sys.stderr)
        sys.exit(1)
