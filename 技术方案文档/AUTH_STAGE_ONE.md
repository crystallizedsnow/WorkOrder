# 第一阶段认证改造：本地部署与验证

本文面向本地开发环境。默认端口：Backend `8080`、CLI Service `5000`、AiAssistant `8081`。

JWT 开发密钥已经直接配置在：

`backend/workorder-server/src/main/resources/application.yaml`

本地启动不需要设置 `WORKORDER_JWT_SECRET`。该固定密钥只能用于本地开发；如果仓库公开、多人共享或部署到生产环境，应改回环境变量或密钥管理系统，因为任何能读取配置文件的人都可以据此伪造 Token。

## 一、备份 MySQL 的 `staff` 表

以下示例数据库名为项目默认的 `wos`。先确认工具可用：

```powershell
mysql --version
mysqldump --version
```

创建项目外的备份目录并备份：

```powershell
$backupDir = Join-Path $env:LOCALAPPDATA 'WorkOrder\backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staffBackup = Join-Path $backupDir "wos-staff-$timestamp.sql"

mysqldump --host=127.0.0.1 --port=3306 --user=root --password `
  --single-transaction --set-gtid-purged=OFF --default-character-set=utf8mb4 `
  wos staff --result-file="$staffBackup"
```

`--password` 后不要直接填写密码，等待 MySQL 交互提示。检查备份：

```powershell
Get-Item -LiteralPath $staffBackup | Select-Object FullName, Length, LastWriteTime
Select-String -LiteralPath $staffBackup -Pattern 'CREATE TABLE.*staff'
```

备份文件不应为空，并应包含 `staff` 建表语句。备份中含密码字段，不要提交到 Git 或发送到聊天。

恢复备份前先停止 Backend。恢复会覆盖当前 `staff` 表：

```powershell
cmd /c "mysql --host=127.0.0.1 --port=3306 --user=root --password wos < `"$staffBackup`""
```

## 二、执行数据库迁移

停止旧版 Backend，确认备份成功，然后执行：

```powershell
$migration = 'D:\JavaCode\-WorkOrder\backend\workorder-server\src\main\resources\auth_stage_one_migration.sql'
cmd /c "mysql --host=127.0.0.1 --port=3306 --user=root --password wos < `"$migration`""
```

迁移脚本不是幂等脚本，同一数据库只执行一次。验证：

```powershell
mysql --host=127.0.0.1 --port=3306 --user=root --password --database=wos `
  --execute="SHOW COLUMNS FROM staff LIKE 'auth_version'; SHOW CREATE TABLE auth_refresh_session;"
```

预期 `staff` 存在 `auth_version`，并且已创建 `auth_refresh_session`。

## 三、构建和启动

先构建并安装新版共享契约：

```powershell
Set-Location D:\JavaCode\-WorkOrder\backend
mvn clean install -DskipTests
```

构建其他组件：

```powershell
Set-Location D:\JavaCode\-WorkOrder\cli-service
mvn clean package -DskipTests

Set-Location D:\JavaCode\-WorkOrder\workorder-cli
go build -o workorder-cli.exe .

Set-Location D:\JavaCode\-WorkOrder\AiAssistant
mvn clean package -DskipTests
```

建议按以下顺序启动：

1. Backend。
2. CLI Service。
3. AiAssistant。

分别在三个 PowerShell 窗口运行：

```powershell
# Backend，端口 8080
Set-Location D:\JavaCode\-WorkOrder\backend
mvn spring-boot:run -pl workorder-server
```

```powershell
# CLI Service，端口 5000
Set-Location D:\JavaCode\-WorkOrder\cli-service
mvn spring-boot:run
```

```powershell
# AiAssistant，端口 8081
Set-Location D:\JavaCode\-WorkOrder\AiAssistant
mvn spring-boot:run
```

旧版调用方不能和新版后端混用，因为新版只接受：

```text
Authorization: Bearer <accessToken>
```

## 四、验证登录和 Bearer Token

在新 PowerShell 中执行。密码使用交互输入，脚本不会显示 Token：

```powershell
$backend = 'http://localhost:8080'
$phone = Read-Host '测试用户手机号'
$securePassword = Read-Host '测试用户密码' -AsSecureString
$credential = [pscredential]::new($phone, $securePassword)

$login = Invoke-RestMethod -Method Post -Uri "$backend/user/login" `
  -ContentType 'application/json' `
  -Body (@{
    phone = $phone
    password = $credential.GetNetworkCredential().Password
  } | ConvertTo-Json)

if ($login.code -ne 1) { throw "登录失败：$($login.msg)" }
$accessToken = $login.data.accessToken
$refreshToken = $login.data.refreshToken

[PSCustomObject]@{
  TokenType = $login.data.tokenType
  AccessExpiresAt = $login.data.accessTokenExpiresAt
  RefreshExpiresAt = $login.data.refreshTokenExpiresAt
  AccessTokenPresent = -not [string]::IsNullOrWhiteSpace($accessToken)
  RefreshTokenPresent = -not [string]::IsNullOrWhiteSpace($refreshToken)
}
```

验证 Access Token：

```powershell
$headers = @{ Authorization = "Bearer $accessToken" }
$validation = Invoke-RestMethod -Method Get -Uri "$backend/api/auth/validate" -Headers $headers
$validation
```

预期 `valid=true`，并返回用户 ID、角色、会话 ID和到期时间，但不返回完整 Token。

裸 Token 必须被拒绝：

```powershell
try {
  Invoke-WebRequest -Method Get -Uri "$backend/api/auth/validate" `
    -Headers @{ Authorization = $accessToken } -ErrorAction Stop
  throw '错误：后端接受了旧裸 Token'
} catch {
  if ($_.Exception.Response.StatusCode.value__ -notin 400,401,403) { throw }
  '通过：旧裸 Token 已拒绝'
}
```

## 五、验证刷新、重放保护和注销

刷新一次：

```powershell
$oldRefreshToken = $refreshToken
$refresh = Invoke-RestMethod -Method Post -Uri "$backend/api/auth/refresh" `
  -ContentType 'application/json' `
  -Body (@{ refreshToken = $oldRefreshToken } | ConvertTo-Json)

if ($refresh.code -ne 1) { throw "刷新失败：$($refresh.msg)" }
$accessToken = $refresh.data.accessToken
$refreshToken = $refresh.data.refreshToken
```

再次提交旧 Refresh Token 应失败，并撤销整个会话族：

```powershell
$replay = Invoke-RestMethod -Method Post -Uri "$backend/api/auth/refresh" `
  -ContentType 'application/json' `
  -Body (@{ refreshToken = $oldRefreshToken } | ConvertTo-Json)

if ($replay.code -eq 1) { throw '错误：旧 Refresh Token 被重复接受' }
$replay.msg
```

重放测试会撤销该会话族，继续测试前请重新登录。然后验证注销：

```powershell
$login = Invoke-RestMethod -Method Post -Uri "$backend/user/login" `
  -ContentType 'application/json' `
  -Body (@{
    phone = $phone
    password = $credential.GetNetworkCredential().Password
  } | ConvertTo-Json)

$accessToken = $login.data.accessToken
$headers = @{ Authorization = "Bearer $accessToken" }
Invoke-RestMethod -Method Post -Uri "$backend/api/auth/logout" -Headers $headers

$afterLogout = Invoke-RestMethod -Method Get -Uri "$backend/api/auth/validate" -Headers $headers
if ($afterLogout.valid) { throw '错误：注销后 Access Token 仍有效' }
```

## 六、验证密码迁移

已有明文密码会在该用户下一次成功登录时升级为 BCrypt。只统计格式，不输出密码：

```powershell
mysql --host=127.0.0.1 --port=3306 --user=root --password --database=wos `
  --execute="SELECT COUNT(*) AS total, SUM(password REGEXP '^\\$2[aby]\\$') AS bcrypt_count, SUM(password NOT REGEXP '^\\$2[aby]\\$') AS pending_migration FROM staff;"
```

使用一个待迁移测试用户成功登录后，`bcrypt_count` 应增加，`pending_migration` 应减少。新建用户和修改密码会直接保存 BCrypt；修改密码还会递增 `auth_version`，使旧 Token 失效。

## 七、验证 Go CLI、CLI Service 和 AiAssistant

Go CLI：

```powershell
Set-Location D:\JavaCode\-WorkOrder\workorder-cli
.\workorder-cli.exe auth login --phone $phone --password $credential.GetNetworkCredential().Password
.\workorder-cli.exe auth status
.\workorder-cli.exe list
.\workorder-cli.exe auth refresh
.\workorder-cli.exe auth logout
```

预期登录和状态输出不显示完整 Token；刷新能够轮换 Token；注销后受保护请求失败。

CLI Service 无 Token 时应返回 401：

```powershell
try {
  Invoke-WebRequest -Method Get -Uri 'http://localhost:5000/api/dataCodes' -ErrorAction Stop
  throw '错误：CLI Service 接受了无 Token 请求'
} catch {
  if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw }
  '通过：CLI Service 拒绝无 Token 请求'
}
```

AiAssistant 请求：

```powershell
$chatBody = @{ memoryId = 1; message = '查询我的工单' } | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/assistant/chat' `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -ContentType 'application/json' -Body $chatBody
```

还应确认无 Header 和裸 Token 都返回 401，并检查 Backend、CLI Service、AiAssistant 日志中没有密码、完整 Authorization Header、Access Token 或 Refresh Token。

旧版本曾创建的明文账号文件不再使用。确认无需回滚旧客户端后可以删除：

```powershell
$legacyAccount = Join-Path $HOME '.workorder\account'
if (Test-Path -LiteralPath $legacyAccount) {
  Remove-Item -LiteralPath $legacyAccount
}
```

## 八、测试命令

```powershell
Set-Location D:\JavaCode\-WorkOrder\backend
mvn -pl workorder-server -am '-Dtest=AuthControllerTest,TokenUtilTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

Set-Location D:\JavaCode\-WorkOrder\cli-service
mvn test

Set-Location D:\JavaCode\-WorkOrder\workorder-cli
go test ./...

Set-Location D:\JavaCode\-WorkOrder\AiAssistant
mvn package -DskipTests
```

项目部分全量测试依赖 Elasticsearch、MongoDB、RabbitMQ、MySQL 等本地基础设施，运行全量测试前需要先启动对应服务。

## 九、回滚

1. 停止 AiAssistant、CLI Service 和 Backend。
2. 同时恢复同一批次的旧版 Backend、CLI Service、Go CLI 和 AiAssistant，不能只回滚一个组件。
3. 停止数据库写入后恢复部署前的 `staff` 备份。
4. 如果确认不保留新版认证数据，可删除新增结构：

```sql
DROP TABLE IF EXISTS auth_refresh_session;
ALTER TABLE staff DROP COLUMN auth_version;
```

不要只删除新增结构但保留已经迁移成 BCrypt 的密码给只能比较明文密码的旧后端，否则这些用户将无法登录。完整回滚应恢复部署前的 `staff` 备份。
