# Apifox 测试集合

将 `CampusPass.apifox.json` 作为 Apifox 格式导入，即可查看项目接口和 94 个脱敏测试用例。用例包含正常请求、参数校验、权限、资源不存在和业务状态冲突等场景。

## 运行前配置

导入后在本地环境中按测试数据配置以下变量：

| 变量 | 用途 |
| --- | --- |
| `bearerToken` | 普通测试用户登录后得到的 JWT，不含 `Bearer ` 前缀 |
| `adminToken` | 管理员登录后得到的 JWT，不含 `Bearer ` 前缀 |
| `draftActivityId` | 当前用户创建的草稿活动 |
| `publishedActivityId` | 报名中的已发布活动 |
| `otherUserActivityId` | 其他用户创建的活动 |
| `alreadyJoinedActivityId` | 当前用户已经报名或候补的活动 |
| `closedActivityId` | 不在报名时间内的活动 |
| `registeredActivityId` | 当前用户已成功报名的活动 |
| `waitlistedActivityId` | 当前用户正在候补的活动 |
| `notJoinedActivityId` | 当前用户没有报名和候补的活动 |
| `enabledUserId` | 正常状态的普通用户 |
| `disabledUserId` | 已禁用的普通用户 |
| `requestId` | 当前用户提交过的异步报名请求 ID |

登录、注册、改密等用例中的手机号、密码和验证码都是公开示例值。运行成功用例前，需要替换为当前测试环境中的实际测试账号数据。

部分用例依赖前置状态，例如“重复报名”“取消候补”和“短时间重复发送验证码”，需要先完成用例名称描述的准备操作。

## 安全说明

该导出文件已经删除所有 `requestResult` 请求历史，并确认不包含真实 JWT、私有 IP、中间件连接地址或数据库连接信息。

如果以后重新从 Apifox 导出文件，可运行：

```powershell
node tests/apifox/prepare-export.mjs
```

脚本会删除请求执行历史、替换敏感示例，并重新生成测试用例。提交前仍应再次检查以下内容：

- JWT 和 Authorization 实际值；
- MySQL、Redis、RabbitMQ 密码；
- 真实手机号、验证码和账号密码；
- 私有服务器地址。
