# Dry-Run Mechanism Test Script
# Tests the dry-run engine functionality for write operations

# Fix encoding for Chinese characters on Windows
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$cliPath = Join-Path $PSScriptRoot "workorder-cli.exe"
$token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiLlvKDkuIkiLCJpZCI6MiwibmFtZSI6IuW8oOS4iSIsImNvbXBhbnkiOiLmgLvlhazlj7giLCJkZXBhcnRtZW50Ijoi5oC75Yqh6YOoIiwicG9zaXRpb24iOiLmgLvnu4_nkIYiLCJzdGF0dXMiOjAsInBob25lIjoiMTM4MTIzNDU2NzgiLCJlbWFpbCI6InpoYW5nc2FuQGV4YW1wbGUuY29tIiwicm9sZSI6InVzZXIiLCJpYXQiOjE3ODUyNTQ4NTksImV4cCI6MTc4NTM0MTI1OX0.qgrhkJBop6RByhfxZSEyyzkgTwco7rX_0GHAe2JH6Ck"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Dry-Run Mechanism Test Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$script:passCount = 0
$script:failCount = 0

# Step 0: Query dataCodes first to confirm available dataCodes
Write-Host "----------- Step 0: Query DataCodes -----------" -ForegroundColor Cyan
$env:WORKORDER_TOKEN = $token
$listOutput = & $cliPath list 2>&1 | Out-String
Write-Host $listOutput -ForegroundColor Green
Write-Host ""

function Test-DryRun {
    param(
        [string]$arguments,
        [string]$description,
        [string]$caseId,
        [string]$token,
        [string]$keyword
    )

    Write-Host "[$caseId] $description" -ForegroundColor Yellow
    Write-Host "Command: $cliPath $arguments" -ForegroundColor Gray

    $env:WORKORDER_TOKEN = $token

    # Use Invoke-Expression to properly split arguments string into tokens
    $output = Invoke-Expression "& '$cliPath' $arguments 2>&1" | Out-String
    $exitCode = $LASTEXITCODE

    Write-Host "Exit Code: $exitCode" -ForegroundColor Gray

    # Check if output contains the expected dry-run structure
    $hasDryRun = $output -match "\[dry-run\]"
    $hasPreview = $output -match "执行计划预览"
    $hasOperation = $output -match "操作类型"
    $hasParams = $output -match "参数详情"
    $hasRisk = $output -match "风险等级"
    $hasImpact = $output -match "预期影响"
    $hasNoExecute = $output -match "不会实际执行"

    $allPass = $hasDryRun -and $hasPreview -and $hasOperation -and $hasParams -and $hasRisk -and $hasImpact -and $hasNoExecute

    # Check for specific keyword if provided
    if ($keyword -ne "") {
        $hasKeyword = $output -match $keyword
        if (-not $hasKeyword) {
            Write-Host "  Missing keyword: $keyword" -ForegroundColor Red
            $allPass = $false
        }
    }

    # Print output in sections
    if ($output.Length -gt 500) {
        Write-Host "Output (truncated):" -ForegroundColor Green
        Write-Host $output.Substring(0, 500) -ForegroundColor Green
        Write-Host "..." -ForegroundColor Green
    } else {
        Write-Host "Output:" -ForegroundColor Green
        Write-Host $output -ForegroundColor Green
    }

    Write-Host "Checks:" -ForegroundColor Gray
    Write-Host "  [dry-run] marker: $(if($hasDryRun){'PASS'}else{'FAIL'})"
    Write-Host "  Plan preview: $(if($hasPreview){'PASS'}else{'FAIL'})"
    Write-Host "  Operation type: $(if($hasOperation){'PASS'}else{'FAIL'})"
    Write-Host "  Parameter details: $(if($hasParams){'PASS'}else{'FAIL'})"
    Write-Host "  Risk level: $(if($hasRisk){'PASS'}else{'FAIL'})"
    Write-Host "  Impact description: $(if($hasImpact){'PASS'}else{'FAIL'})"
    Write-Host "  No-execute warning: $(if($hasNoExecute){'PASS'}else{'FAIL'})"

    if ($allPass) {
        $script:passCount++
        Write-Host "  Result: PASS" -ForegroundColor Green
    } else {
        $script:failCount++
        Write-Host "  Result: FAIL" -ForegroundColor Red
    }
    Write-Host ""
}

# Test 1: Work Order Create - dry-run with full structure
Write-Host "----------- 1. 创建工单 dry-run（完整结构） -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_create --type 0 --title 'Test Ticket' --content 'Test content' --priority-level 0 --flow-id 2081909482228682752" `
    -description "创建工单 dry-run，验证执行计划结构完整" `
    -caseId "DRY-001" `
    -token $token `
    -keyword "创建工单"

# Test 2: Work Order Handle - dry-run with enum translation
Write-Host "----------- 2. 处理工单 dry-run（枚举翻译） -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_handle --id 1 --handle-type 4 --remark 'Completed'" `
    -description "处理工单 dry-run，验证 handleType 枚举翻译" `
    -caseId "DRY-002" `
    -token $token `
    -keyword "完成"

# Test 3: Work Order Approval - dry-run with boolean translation
Write-Host "----------- 3. 审批工单 dry-run（布尔翻译） -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_approval --id 1 --is-approved true --remark 'Approve'" `
    -description "审批工单 dry-run，验证 isApproved 翻译" `
    -caseId "DRY-003" `
    -token $token `
    -keyword "通过"

# Test 4: Work Order Delete - dry-run with high risk
Write-Host "----------- 4. 删除工单 dry-run（高风险） -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_delete --id 1" `
    -description "删除工单 dry-run，验证高风险标记" `
    -caseId "DRY-004" `
    -token $token `
    -keyword "高"

# Test 5: Flow Create - dry-run with nested params (using @file to avoid quote issues)
Write-Host "----------- 5. 创建流程 dry-run（嵌套参数） -----------" -ForegroundColor Cyan
$bodyParam = '{"nodes":[{"handlerId":1,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"李四"},"checkNode":{"handlerId":3,"handlerName":"王五"}}'
$bodyFile = Join-Path $PSScriptRoot "test-flow-body-temp.json"
[System.IO.File]::WriteAllText($bodyFile, $bodyParam, [System.Text.UTF8Encoding]::new($false))
Test-DryRun -arguments "--dry-run flow_create --flow-name 'Test Flow' --body '@$bodyFile'" `
    -description "创建流程 dry-run，验证嵌套参数展示" `
    -caseId "DRY-005" `
    -token $token `
    -keyword "创建流程"
    Remove-Item $bodyFile -ErrorAction SilentlyContinue

# Test 6: Flow Edit - dry-run
Write-Host "----------- 6. 编辑流程 dry-run -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run flow_edit --flow-id 1 --flow-name 'Updated Flow'" `
    -description "编辑流程 dry-run" `
    -caseId "DRY-006" `
    -token $token `
    -keyword "编辑流程"

# Test 7: Flow Delete - dry-run with high risk
Write-Host "----------- 7. 删除流程 dry-run（高风险） -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run flow_delete --flow-id 1" `
    -description "删除流程 dry-run，验证高风险标记" `
    -caseId "DRY-007" `
    -token $token `
    -keyword "高"

# Test 8: Priority level translation
Write-Host "----------- 8. 优先级翻译验证 -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_create --type 0 --title 'Priority Test' --content 'Test' --priority-level 2 --flow-id 1" `
    -description "创建工单 dry-run，验证优先级翻译为低" `
    -caseId "DRY-008" `
    -token $token `
    -keyword "低"

# Test 9: Work order type translation
Write-Host "----------- 9. 工单类型翻译验证 -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_create --type 1 --title 'Type Test' --content 'Test' --priority-level 0 --flow-id 1" `
    -description "创建工单 dry-run，验证类型翻译为故障" `
    -caseId "DRY-009" `
    -token $token `
    -keyword "故障"

# Test 10: Work Order Handle - assign type
Write-Host "----------- 10. 分配处理类型验证 -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_handle --id 1 --handle-type 1 --assigned-user-id 2 --remark 'Assign'" `
    -description "处理工单 dry-run，验证分配类型翻译" `
    -caseId "DRY-010" `
    -token $token `
    -keyword "分配"

# Test 11: Cancel work order
Write-Host "----------- 11. 取消工单 dry-run -----------" -ForegroundColor Cyan
Test-DryRun -arguments "--dry-run work_order_cancel --id 1 --reason 'No longer needed'" `
    -description "取消工单 dry-run" `
    -caseId "DRY-011" `
    -token $token `
    -keyword "取消工单"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PASS: $script:passCount" -ForegroundColor Green
Write-Host "FAIL: $script:failCount" -ForegroundColor Red
Write-Host "========================================" -ForegroundColor Cyan
