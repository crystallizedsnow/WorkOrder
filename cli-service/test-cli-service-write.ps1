$baseUrl = "http://localhost:5000"
$traceId = "test-trace-id-" + [guid]::NewGuid().ToString()

# 普通用户 Token（role=user），用于非 admin 接口测试和权限校验测试
$userToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiLlvKDkuIkiLCJpZCI6MiwibmFtZSI6IuW8oOS4iSIsImNvbXBhbnkiOiLmgLvlhazlj7giLCJkZXBhcnRtZW50Ijoi5oC75Yqh6YOoIiwicG9zaXRpb24iOiLmgLvnu4_nkIYiLCJzdGF0dXMiOjAsInBob25lIjoiMTM4MTIzNDU2NzgiLCJlbWFpbCI6InpoYW5nc2FuQGV4YW1wbGUuY29tIiwicm9sZSI6InVzZXIiLCJpYXQiOjE3ODUyMDA0OTAsImV4cCI6MTc4NTI4Njg5MH0.2gbUsC4mHEFXYBzihW7QL3_tk8-Z7xn1AuWm2qwCMhM"

# 管理员 Token（role=admin），用于 admin 接口测试（work_order_delete、flow_create/edit/delete）
# 请根据实际情况替换为有效的管理员 Token
$adminToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiLlvKDkuIkiLCJpZCI6MiwibmFtZSI6IuW8oOS4iSIsImNvbXBhbnkiOiLmgLvlhazlj7giLCJkZXBhcnRtZW50Ijoi5oC75Yqh6YOoIiwicG9zaXRpb24iOiLmgLvnu4_nkIYiLCJzdGF0dXMiOjAsInBob25lIjoiMTM4MTIzNDU2NzgiLCJlbWFpbCI6InpoYW5nc2FuQGV4YW1wbGUuY29tIiwicm9sZSI6InVzZXIiLCJpYXQiOjE3ODUyMDA0OTAsImV4cCI6MTc4NTI4Njg5MH0.2gbUsC4mHEFXYBzihW7QL3_tk8-Z7xn1AuWm2qwCMhM"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CLI Service Write API Test Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$script:passCount = 0
$script:failCount = 0
$script:skipCount = 0

function Send-Request {
    param(
        [string]$method,
        [string]$url,
        [string]$body,
        [string]$token,
        [string]$description,
        [string]$caseId,
        [scriptblock]$validator
    )

    Write-Host "[$caseId] [$method] $url" -ForegroundColor Yellow
    Write-Host "Desc: $description" -ForegroundColor Gray

    $httpRequest = [System.Net.HttpWebRequest]::Create($url)
    $httpRequest.Method = $method
    $httpRequest.Headers["X-Trace-ID"] = $traceId

    if ($token) {
        $httpRequest.Headers.Set("Authorization", $token)
    }

    if ($method -eq "POST") {
        $httpRequest.ContentType = "application/json"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $httpRequest.ContentLength = $bytes.Length
        $stream = $httpRequest.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
    }

    $responseBody = ""
    $statusCode = 0
    try {
        $response = $httpRequest.GetResponse()
        $statusCode = [int]$response.StatusCode
        $streamReader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $responseBody = $streamReader.ReadToEnd()
        $streamReader.Close()
        $response.Close()
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $streamReader.ReadToEnd()
            $streamReader.Close()
        } else {
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            $script:failCount++
            Write-Host "  Result: FAIL" -ForegroundColor Red
            Write-Host ""
            return $null
        }
    }

    Write-Host "Status: $statusCode" -ForegroundColor Gray
    Write-Host "Response: $responseBody" -ForegroundColor Green

    if ($validator) {
        $result = & $validator $statusCode $responseBody
        if ($result -eq $true) {
            $script:passCount++
            Write-Host "  Result: PASS" -ForegroundColor Green
        } elseif ($result -eq "skip") {
            $script:skipCount++
            Write-Host "  Result: SKIP" -ForegroundColor Yellow
        } else {
            $script:failCount++
            Write-Host "  Result: FAIL" -ForegroundColor Red
        }
    }
    Write-Host ""
    return $responseBody
}

# 辅助函数：解析 JSON
function Parse-Json {
    param($body)
    try {
        return $body | ConvertFrom-Json
    } catch {
        return $null
    }
}

# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "Part 1: Basic Function Tests (5.1.1)" -ForegroundColor Cyan
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host ""

# # 1.1 dataCodes 接口 - 验证返回读写全部 dataCode，含 type 字段
# Write-Host "----------- 1.1 dataCodes API (含 type 字段) -----------" -ForegroundColor Cyan
# Send-Request -method "GET" -url "$baseUrl/api/dataCodes" -token $userToken `
#     -description "获取全部 dataCode，预期包含写 dataCode 且带 type=read/write 字段" `
#     -caseId "BASE-001" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -ne 0) { return $false }
#         $writeCodes = $json.data | Where-Object { $_.type -eq "write" }
#         if ($writeCodes.Count -lt 8) { return $false }
#         $readCodes = $json.data | Where-Object { $_.type -eq "read" }
#         if ($readCodes.Count -lt 1) { return $false }
#         return $true
#     }

# # 1.2 Schema 查询 - 各写 dataCode 的 schema
# Write-Host "----------- 1.2 Schema API (写 dataCode) -----------" -ForegroundColor Cyan
# $writeSchemas = @("work_order_create", "work_order_handle", "work_order_delete", "work_order_cancel", "work_order_approval", "flow_create", "flow_edit", "flow_delete")
# foreach ($schema in $writeSchemas) {
#     Send-Request -method "GET" -url "$baseUrl/api/schema/$schema" -token $userToken `
#         -description "查询写接口 Schema: $schema" `
#         -caseId "BASE-002" `
#         -validator {
#             param($statusCode, $body)
#             $json = Parse-Json $body
#             if ($null -eq $json) { return $false }
#             if ($json.code -ne 0) { return $false }
#             if (-not $json.data.inputSchema) { return $false }
#             return $true
#         }
# }

# # 1.3 错误处理 - 不存在的 dataCode，返回 404
# Write-Host "----------- 1.3 Error Handling (未知 dataCode) -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "unknown_data_code", "params": {}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "调用未知的写 dataCode，预期返回 code=404" `
#     -caseId "BASE-003" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 404) { return $true }
#         return $false
#     }

# # 1.4 权限校验 - 普通用户调用 admin 接口（flow_create），返回 403
# Write-Host "----------- 1.4 Permission Check (普通用户调用 admin 接口) -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "flow_create", "params": {"flowName": "test", "nodes": [], "distributeNode": {}, "checkNode": {}}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "普通用户调用 flow_create（admin 接口），预期返回 code=403" `
#     -caseId "BASE-004" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 403) { return $true }
#         return $false
#     }

# # 1.5 无 Token - 返回 401
# Write-Host "----------- 1.5 Auth Test (无 Token) -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_create", "params": {"type": 0, "title": "test", "content": "test", "priorityLevel": 0, "flowId": 1}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token "" `
#     -description "写操作不带 Token，预期返回 HTTP 401" `
#     -caseId "BASE-005" `
#     -validator {
#         param($statusCode, $body)
#         if ($statusCode -eq 401) { return $true }
#         return $false
#     }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Part 2: Write Operation Tests (5.1.2, 6001-6012)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 6001 创建工单
# Write-Host "----------- 6001 创建工单 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_create", "params": {"type": 0, "title": "test_from_cli_service_test", "content": "test content", "priorityLevel": 0, "flowId": 2081909482228682752}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "创建工单，预期返回 code=0，data 含工单 id 和编号" `
#     -caseId "6001" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -ne 0) { return $false }
#         if (-not $json.data) { return $false }
#         return $true
#     }

# 6002 处理工单（完成）
# Write-Host "----------- 6002 处理工单（完成） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_handle", "params": {"id": 35, "handleType": 4, "remark": "已完成处理"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "处理工单（handleType=4 完成），预期返回 code=0" `
#     -caseId "6002" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# 6003 处理工单（分配）
# Write-Host "----------- 6003 处理工单（分配） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_handle", "params": {"id": 35, "handleType": 1, "assignedUserId": 2, "remark": "分配给张三"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "处理工单（handleType=1 分配），预期返回 code=0" `
#     -caseId "6003" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6004 审批工单（通过）
# Write-Host "----------- 6004 审批工单（通过） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_approval", "params": {"id": 35, "isApproved": true, "remark": "审批通过"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "审批工单（isApproved=true 通过），预期返回 code=0" `
#     -caseId "6004" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6005 审批工单（拒绝）
# Write-Host "----------- 6005 审批工单（拒绝） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_approval", "params": {"id": 35, "isApproved": false, "remark": "审批拒绝"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "审批工单（isApproved=false 拒绝），预期返回 code=0" `
#     -caseId "6005" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6006 取消工单
# Write-Host "----------- 6006 取消工单 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_cancel", "params": {"id": 1}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "取消工单，预期返回 code=0" `
#     -caseId "6006" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6007 删除工单（admin 接口，需管理员 Token）
# Write-Host "----------- 6007 删除工单（admin 接口） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_delete", "params": {"id": 1}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $adminToken `
#     -description "删除工单" `
#     -caseId "6007" `
#     -validator {
#         param($statusCode, $body)
#         if ($adminToken -eq "REPLACE_WITH_ADMIN_TOKEN") { return "skip" }
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# 6008 创建流程（含嵌套参数，admin 接口）
# Write-Host "----------- 6008 创建流程（含嵌套参数） -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "flow_create", "params": {"flowName": "test_flow_from_cli_service", "nodes": [{"handlerId": 2, "handlerName": "张三"}], "distributeNode": {"handlerId": 2, "handlerName": "张三"}, "checkNode": {"handlerId": 2, "handlerName": "张三"}}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $adminToken `
#     -description "创建流程（含 FlowNode 列表），预期返回 code=0，data 含 flowId；若 adminToken 未配置则 SKIP" `
#     -caseId "6008" `
#     -validator {
#         param($statusCode, $body)
#         if ($adminToken -eq "REPLACE_WITH_ADMIN_TOKEN") { return "skip" }
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6009 编辑流程（admin 接口）
# Write-Host "----------- 6009 编辑流程 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "flow_edit", "params": {"flowId": 1, "flowName": "updated_flow_name"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $adminToken `
#     -description "编辑流程，预期返回 code=0；若 adminToken 未配置则 SKIP" `
#     -caseId "6009" `
#     -validator {
#         param($statusCode, $body)
#         if ($adminToken -eq "REPLACE_WITH_ADMIN_TOKEN") { return "skip" }
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6010 删除流程（admin 接口）
# Write-Host "----------- 6010 删除流程 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "flow_delete", "params": {"flowId": 1}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $adminToken `
#     -description "删除流程，预期返回 code=0；若 adminToken 未配置则 SKIP" `
#     -caseId "6010" `
#     -validator {
#         param($statusCode, $body)
#         if ($adminToken -eq "REPLACE_WITH_ADMIN_TOKEN") { return "skip" }
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6011 嵌套参数反序列化（重点验证 FlowNode 列表能正确解析）
# Write-Host "----------- 6011 嵌套参数反序列化 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "flow_create", "params": {"flowName": "test_nested_param", "nodes": [{"handlerId": 1, "handlerName": "审批人A"}, {"handlerId": 2, "handlerName": "审批人B"}], "distributeNode": {"handlerId": 3, "handlerName": "分配人C"}, "checkNode": {"handlerId": 4, "handlerName": "验收人D"}}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $adminToken `
#     -description "创建流程（多节点嵌套参数），验证嵌套 JSON 反序列化正确；若 adminToken 未配置则 SKIP" `
#     -caseId "6011" `
#     -validator {
#         param($statusCode, $body)
#         if ($adminToken -eq "REPLACE_WITH_ADMIN_TOKEN") { return "skip" }
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         if ($json.code -eq 0) { return $true }
#         return $false
#     }

# # 6012 工单编号查询参数（work_order_handle 使用 code 替代 id）
# Write-Host "----------- 6012 工单编号查询参数 -----------" -ForegroundColor Cyan
# $body = '{"dataCode": "work_order_handle", "params": {"code": "WO20260101001", "handleType": 4, "remark": "按编号处理"}}'
# Send-Request -method "POST" -url "$baseUrl/api/execute" -body $body -token $userToken `
#     -description "处理工单（使用 code 替代 id），验证编号参数正确处理" `
#     -caseId "6012" `
#     -validator {
#         param($statusCode, $body)
#         $json = Parse-Json $body
#         if ($null -eq $json) { return $false }
#         # code=0 表示成功，code 非 0 且非 404/500 表示参数被正确接收但业务校验未通过
#         if ($json.code -eq 0 -or $json.code -eq 1) { return $true }
#         return $false
#     }

# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "Test Summary" -ForegroundColor Cyan
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "PASS: $script:passCount" -ForegroundColor Green
# Write-Host "FAIL: $script:failCount" -ForegroundColor Red
# Write-Host "SKIP: $script:skipCount" -ForegroundColor Yellow
# Write-Host "========================================" -ForegroundColor Cyan
