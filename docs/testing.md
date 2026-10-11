# CampusPass 测试说明

## 1. 自动化测试

项目中的测试不连接 MySQL、Redis 或 RabbitMQ，适合在每次提交前快速运行：

```powershell
.\mvnw.cmd clean test
```

目前覆盖：

- 活动四个时间字段的先后关系；
- JWT 的生成、解析、错误密钥及非法格式；
- 手机号和用户名校验。

## 2. Apifox 接口测试

建议保留一份 Apifox 项目导出文件，覆盖登录、个人资料、活动、报名、候补和管理员接口。导出前删除环境中的 JWT、数据库密码、RabbitMQ 密码等敏感值。

Apifox 各版本入口文字可能略有不同，通常位于“项目设置 → 导出数据”。要连同测试用例一起保存时选择 Apifox 格式；只导出 OpenAPI 文档不会包含完整的测试用例。建议保存到：

```text
tests/apifox/CampusPass.apifox.json
```

## 3. JMeter 报名并发测试

脚本位于 `tests/jmeter`，使用 JMeter 5.6.3。请先在终端进入项目根目录，再执行下面的命令。测试前需要：

1. 启动 CampusPass、MySQL、Redis 和 RabbitMQ。
2. 创建一个已发布、正在报名的全新活动，记下活动 ID。
3. 根据要执行的测试准备 Token 或测试账号。

### 测试 A：同一用户并发报名

复制 `tests/jmeter/tokens.csv.example` 为 `tests/jmeter/tokens.csv`，在其中重复放入同一个用户的 Token，例如 20 行，然后运行 20 个线程。预期只有第一次请求建立有效报名或候补关系，其余请求返回重复报名冲突；数据库中该用户与活动只能有一条有效关系。`tokens.csv` 已被 Git 忽略，不会把真实凭证提交到仓库。

在项目根目录执行：

```bat
jmeter -n -t tests\jmeter\registration-concurrency.jmx ^
  -Jhost=localhost -Jport=8080 -JactivityId=123 -Jthreads=20 -JrampUp=1 ^
  -l tests\results\registration.jtl ^
  -e -o tests\results\report
```

### 测试 B：不同用户并发抢少量名额

复制 `tests/jmeter/accounts.csv.example` 为 `tests/jmeter/accounts.csv`，填写多个不同测试用户的手机号和密码。新活动可以设置 5 个名额，再以 20 个线程同时报名。脚本会分别登录这些账号、提取各自的 Token，并等待全部用户登录后同时报名。`accounts.csv` 已被 Git 忽略。

在项目根目录执行：

```bat
jmeter -n -t tests\jmeter\registration-multi-user.jmx ^
  -Jhost=localhost -Jport=8080 -JactivityId=123 -Jthreads=20 -JrampUp=1 ^
  -l tests\results\registration-multi-user.jtl ^
  -e -o tests\results\registration-multi-user-report
```

CSV 文件默认从对应 JMX 脚本所在目录读取。需要放在其他位置时，可以额外传入 `-JtokenFile=绝对路径` 或 `-JaccountFile=绝对路径`。

报名采用异步消息，HTTP 成功只代表请求已受理。等待 RabbitMQ 队列中的 Ready 和 Unacked 都变为 0 后，再核对数据库。再次运行前删除对应的旧 JTL 文件和报告目录，否则 JMeter 会拒绝覆盖。正式压测时使用命令行模式；GUI 适合编辑脚本和查看少量调试请求。

## 4. 最终状态核对

以下 SQL 中把 `123` 替换为本次测试活动 ID。最好使用一个没有历史取消记录的新活动，结果更容易判断。

```sql
-- 活动容量和剩余库存
SELECT activity_id, total_capacity, remaining_capacity
FROM tb_activity
WHERE activity_id = 123;

-- 有效报名数，不能超过总名额
SELECT COUNT(*) AS active_registration_count
FROM tb_registration
WHERE activity_id = 123 AND status = 0;

-- 正在候补的人数
SELECT COUNT(*) AS active_wait_count
FROM tb_wait_list
WHERE activity_id = 123 AND status = 0;

-- 同一用户不应有重复的有效报名记录
SELECT user_id, COUNT(*) AS duplicate_count
FROM tb_registration
WHERE activity_id = 123 AND status = 0
GROUP BY user_id
HAVING COUNT(*) > 1;

-- 同一用户不应同时处于报名成功和候补中
SELECT r.user_id
FROM tb_registration r
JOIN tb_wait_list w
  ON w.activity_id = r.activity_id AND w.user_id = r.user_id
WHERE r.activity_id = 123 AND r.status = 0 AND w.status = 0;
```

对于全新的活动，最终还应满足：

```text
有效报名数 <= 总名额
剩余名额 = 总名额 - 有效报名数
重复报名查询无结果
同时报名和候补查询无结果
RabbitMQ 原队列与死信队列无异常积压
```

将最终数字、JMeter 错误率、响应时间和一致性核对结果整理到实际测试报告中。本项目的已完成报告见 `docs/concurrency-test-report.md`。
