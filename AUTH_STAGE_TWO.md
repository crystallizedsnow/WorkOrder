# 第二阶段身份绑定与服务换票：本地部署与验证

## 数据库迁移

执行一次：

```powershell
$migration = 'D:\JavaCode\-WorkOrder\backend\workorder-server\src\main\resources\auth_stage_two_migration.sql'
cmd /c "mysql --host=127.0.0.1 --port=3306 --user=root --password wos < `"$migration`""
```

验证存在 `channel_binding_challenge` 和 `external_identity_binding` 两张表。

## 本地配置

Backend 与 AiAssistant 已配置相同的本地服务密钥：

- Backend：`workorder.auth.channel-service-key`
- AiAssistant：`workorder.channel.service-key`

该固定值只适用于本地开发。内部接口使用 `X-Workorder-Service-Key`，普通用户 Bearer Token 无法替代服务身份。

## Go CLI

```powershell
.\workorder-cli.exe auth login --phone <手机号> --password <密码>
.\workorder-cli.exe channel feishu bind-code
.\workorder-cli.exe channel feishu unbind
```

绑定码有效期 5 分钟、仅可使用一次。`bind-code` 输出的码应在飞书单聊中提交；飞书接入完成前，可直接调用内部确认接口测试。

## 接口验证

申请绑定码（用户 Bearer）：`POST /api/channel/identity/binding-challenges`。

确认绑定（AiAssistant 服务身份）：`POST /api/channel/identity/internal/confirm`：

```json
{"code":"绑定码","tenantKey":"test-tenant","unionId":"test-union","openId":"test-open"}
```

查询绑定：`POST /api/channel/identity/internal/find`。

服务换票：`POST /api/channel/identity/internal/exchange`：

```json
{"tenantKey":"test-tenant","unionId":"test-union","openId":"test-open","channelSessionId":"test-session"}
```

换票返回 5 分钟代理 Access Token，不返回 Refresh Token。该 Token 具有真实用户身份和飞书来源审计 Claims，但不包含 unionId、openId、姓名等平台或个人信息。

## 必测行为

1. 无效服务密钥不能确认、查询或换票。
2. 同一个绑定码只能成功一次。
3. 一个有效飞书身份不能同时绑定多个工单用户。
4. 一个工单用户不能同时绑定多个有效飞书身份。
5. 解绑后旧代理 Token 立即失效。
6. 用户禁用或改密导致认证版本变化后，旧代理 Token 立即失效。
7. 解绑后可申请新码重新绑定；历史记录复用并递增 `binding_version`。
8. 数据库只保存绑定码 SHA-256 哈希，不保存原始绑定码。
