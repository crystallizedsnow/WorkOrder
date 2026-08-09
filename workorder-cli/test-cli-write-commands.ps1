# CLI Layer Write Commands Test Script
# Reference: workorder-cli-write-service_technical_design.md Section 5.2 and 5.3

# Fix encoding for Chinese characters on Windows
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$cliPath = Join-Path $PSScriptRoot "workorder-cli.exe"
$token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiLlvKDkuIkiLCJpZCI6MiwibmFtZSI6IuW8oOS4iSIsImNvbXBhbnkiOiLmgLvlhazlj7giLCJkZXBhcnRtZW50Ijoi5oC75Yqh6YOoIiwicG9zaXRpb24iOiLmgLvnu4_nkIYiLCJzdGF0dXMiOjAsInBob25lIjoiMTM4MTIzNDU2NzgiLCJlbWFpbCI6InpoYW5nc2FuQGV4YW1wbGUuY29tIiwicm9sZSI6InVzZXIiLCJpYXQiOjE3ODUyNTQ4NTksImV4cCI6MTc4NTM0MTI1OX0.qgrhkJBop6RByhfxZSEyyzkgTwco7rX_0GHAe2JH6Ck"
$adminToken = $token

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CLI Layer Write Commands Test Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$script:passCount = 0
$script:failCount = 0
$script:skipCount = 0

# Step 0: Query dataCodes first to confirm available dataCodes
Write-Host "----------- Step 0: Query DataCodes -----------" -ForegroundColor Cyan
$env:WORKORDER_TOKEN = $token
$listOutput = & $cliPath list 2>&1 | Out-String
Write-Host $listOutput -ForegroundColor Green
Write-Host ""

function Run-CliCommand {
    param(
        [string]$arguments,
        [string]$description,
        [string]$caseId,
        [string]$token,
        [scriptblock]$validator
    )

    Write-Host "[$caseId] $description" -ForegroundColor Yellow
    Write-Host "Command: $cliPath $arguments" -ForegroundColor Gray

    $env:WORKORDER_TOKEN = $token

    # Use Invoke-Expression to properly split arguments string into tokens
    $output = Invoke-Expression "& '$cliPath' $arguments 2>&1" | Out-String
    $exitCode = $LASTEXITCODE

    Write-Host "Exit Code: $exitCode" -ForegroundColor Gray
    Write-Host "Output: $output" -ForegroundColor Green

    if ($validator) {
        $result = & $validator $exitCode $output
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
    return $output
}

# Helper function: check exit code is 0
function Check-ExitZero {
    param($exitCode, $output)
    if ($exitCode -eq 0) { return $true }
    return $false
}

# Helper function: check output contains dry-run marker
function Check-DryRunOutput {
    param($exitCode, $output)
    if ($exitCode -ne 0) { return $false }
    if ($output -match "\[dry-run\]") { return $true }
    return $false
}

# Helper function: check output contains JSON code=0
function Check-CodeZero {
    param($exitCode, $output)
    if ($exitCode -ne 0) { return $false }
    try {
        $json = $output | ConvertFrom-Json
        if ($json.code -eq 0) { return $true }
        return $false
    } catch {
        return $false
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Part 1: Write Commands Test (5.2)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 5001 Work Order Create
Write-Host "----------- 5001 工单创建 -----------" -ForegroundColor Cyan
Run-CliCommand -arguments "work_order create --type 0 --title 'CLI Test WorkOrder' --content 'Test content from CLI' --priority-level 0 --flow-id 2081909482228682752" `
    -description "创建工单" `
    -caseId "5001" `
    -token $token `
    -validator {
        param($exitCode, $output)
        return (Check-CodeZero $exitCode $output)
    }

# # 5002 Work Order Handle (Complete)
# Write-Host "----------- 5002 处理工单（完成） -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "work_order handle --id 1 --handle-type 4 --remark 'CLI Test Handle'" `
#     -description "处理工单（完成）" `
#     -caseId "5002" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# # 5003 Work Order Approval (Approve)
# Write-Host "----------- 5003 审批工单（通过） -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "work_order approval --id 1 --is-approved true --remark 'CLI Test Approval'" `
#     -description "审批工单（通过）" `
#     -caseId "5003" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# # 5004 Work Order Approval (Reject)
# Write-Host "----------- 5004 审批工单（拒绝） -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "work_order approval --id 36 --is-approved false --remark 'CLI Test Reject'" `
#     -description "审批工单（拒绝）" `
#     -caseId "5004" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# # 5005 Work Order Cancel
# Write-Host "----------- 5005 取消工单 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "work_order cancel --id 1" `
#     -description "取消工单" `
#     -caseId "5005" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# # 5006 Work Order Delete (Admin)
# Write-Host "----------- 5006 删除工单 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "work_order delete --id 37" `
#     -description "删除工单" `
#     -caseId "5006" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# 5007 Flow Create (with --body via @file)
# Write-Host "----------- 5007 创建流程（含嵌套参数） -----------" -ForegroundColor Cyan
# $bodyParam = '{"nodes":[{"handlerId":2,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"张三"},"checkNode":{"handlerId":2,"handlerName":"张三"}}'
# $bodyFile = Join-Path $PSScriptRoot "test-flow-body-5007.json"
# [System.IO.File]::WriteAllText($bodyFile, $bodyParam, [System.Text.UTF8Encoding]::new($false))
# Run-CliCommand -arguments "flow create --flow-name 'CLI Test Flow' --body '@$bodyFile'" `
#     -description "创建流程（含 FlowNode 列表）" `
#     -caseId "5007" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }
# Remove-Item $bodyFile -ErrorAction SilentlyContinue

# 5008 Flow Edit
# Write-Host "----------- 5008 编辑流程 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "flow edit --flow-id 2082458960107016200 --flow-name 'CLI Updated Flow'" `
#     -description "编辑流程" `
#     -caseId "5008" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# 5009 Flow Delete
# Write-Host "----------- 5009 删除流程 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "flow delete --flow-id 2082458960107016200" `
#     -description "删除流程" `
#     -caseId "5009" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }

# # 5010 JSON Complex Parameter (--body via @file)
# Write-Host "----------- 5010 JSON复杂参数 -----------" -ForegroundColor Cyan
# $bodyParam2 = '{"nodes":[{"handlerId":1,"handlerName":"审批人A"},{"handlerId":2,"handlerName":"审批人B"}],"distributeNode":{"handlerId":3,"handlerName":"分配人C"},"checkNode":{"handlerId":4,"handlerName":"验收人D"}}'
# $bodyFile2 = Join-Path $PSScriptRoot "test-flow-body-5010.json"
# [System.IO.File]::WriteAllText($bodyFile2, $bodyParam2, [System.Text.UTF8Encoding]::new($false))
# Run-CliCommand -arguments "flow create --flow-name 'CLI Nested Flow' --body '@$bodyFile2'" `
#     -description "创建流程（多节点嵌套参数）" `
#     -caseId "5010" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-CodeZero $exitCode $output)
#     }
# Remove-Item $bodyFile2 -ErrorAction SilentlyContinue

# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "Part 2: Dry-Run Mechanism Test (5.3)" -ForegroundColor Cyan
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host ""

# # 5011 Dry-run Work Order Create
# Write-Host "----------- 5011 dry-run 创建工单 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run work_order create --type 0 --title 'DryRun Test' --content 'Test' --priority-level 0 --flow-id 1" `
#     -description "dry-run 创建工单（不实际执行）" `
#     -caseId "5011" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }

# # 5012 Dry-run Work Order Handle
# Write-Host "----------- 5012 dry-run 处理工单 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run work_order handle --id 1 --handle-type 4" `
#     -description "dry-run 处理工单" `
#     -caseId "5012" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }

# # 5013 Dry-run Work Order Approval
# Write-Host "----------- 5013 dry-run 审批工单 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run work_order approval --id 1 --is-approved true" `
#     -description "dry-run 审批工单" `
#     -caseId "5013" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }

# # 5014 Dry-run Flow Create (with --body via @file)
# Write-Host "----------- 5014 dry-run 创建流程 -----------" -ForegroundColor Cyan
# $bodyParam3 = '{"nodes":[{"handlerId":1,"handlerName":"张三"}],"distributeNode":{"handlerId":2,"handlerName":"李四"},"checkNode":{"handlerId":3,"handlerName":"王五"}}'
# $bodyFile3 = Join-Path $PSScriptRoot "test-flow-body-5014.json"
# [System.IO.File]::WriteAllText($bodyFile3, $bodyParam3, [System.Text.UTF8Encoding]::new($false))
# Run-CliCommand -arguments "--dry-run flow create --flow-name 'DryRun Flow' --body '@$bodyFile3'" `
#     -description "dry-run 创建流程" `
#     -caseId "5014" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }
# Remove-Item $bodyFile3 -ErrorAction SilentlyContinue

# # 5015 Dry-run Flow Edit
# Write-Host "----------- 5015 dry-run 编辑流程 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run flow edit --flow-id 1 --flow-name 'DryRun Edit'" `
#     -description "dry-run 编辑流程" `
#     -caseId "5015" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }

# # 5016 Dry-run Flow Delete
# Write-Host "----------- 5016 dry-run 删除流程 -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run flow delete --flow-id 1" `
#     -description "dry-run 删除流程" `
#     -caseId "5016" `
#     -token $adminToken `
#     -validator {
#         param($exitCode, $output)
#         return (Check-DryRunOutput $exitCode $output)
#     }

# # 5017 Read command with dry-run (should work normally)
# Write-Host "----------- 5017 读命令 dry-run（正常执行） -----------" -ForegroundColor Cyan
# Run-CliCommand -arguments "--dry-run work_order page --page-num 1 --page-size 10" `
#     -description "读命令带 dry-run（正常执行）" `
#     -caseId "5017" `
#     -token $token `
#     -validator {
#         param($exitCode, $output)
#         # Read commands should work normally even with --dry-run
#         return (Check-ExitZero $exitCode $output)
#     }

# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "Test Summary" -ForegroundColor Cyan
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "PASS: $script:passCount" -ForegroundColor Green
# Write-Host "FAIL: $script:failCount" -ForegroundColor Red
# Write-Host "SKIP: $script:skipCount" -ForegroundColor Yellow
# Write-Host "========================================" -ForegroundColor Cyan