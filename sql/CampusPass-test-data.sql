-- CampusPass 本地综合测试数据
-- 包含：
--   1. Apifox 基础业务与边界测试活动；
--   2. 用户报名、候补及活动参与者分页数据；
--   3. JMeter 多用户并发报名所需的 20 个测试账号。
--
-- 可重复执行：活动数据通过 CP_TEST_20261010_ 和 CP_PAGE_TEST_ 前缀清理后重建；
-- 测试账号通过 INSERT IGNORE 创建并统一校正测试密码。
-- 仅限本地开发/测试数据库使用，禁止在生产数据库执行。
--
-- 执行前准备：
--   1. 数据库结构已通过 CampusPass.sql 创建；
--   2. 已存在手机号 13800001001 的普通测试用户；
--   3. 建议存在手机号 13800000000 的管理员用户。
--
-- JMeter 测试账号：
--   手机号 13988000001 ～ 13988000020
--   明文密码 password

CREATE DATABASE IF NOT EXISTS campus_pass
    DEFAULT CHARACTER SET utf8mb4;

USE campus_pass;

-- ============================================================
-- 一、JMeter 并发账号（同时供分页参与者列表使用）
-- ============================================================
INSERT IGNORE INTO tb_user (
    user_name, password, student_id, phone, role, status, auth_version
)
SELECT CONCAT('分页参与者', LPAD(numbers.number_value, 2, '0')),
       '$2a$10$sY/2hnZETecnblXawXsOpuqvBDCdE7CR1XGwRh4xO0v7v99.PqBKC',
       CONCAT('PAGE', LPAD(numbers.number_value, 4, '0')),
       CONCAT('139880000', LPAD(numbers.number_value, 2, '0')),
       0,
       0,
       0
FROM (
    SELECT 1 AS number_value UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL
    SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL
    SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL
    SELECT 20
) AS numbers;

-- INSERT IGNORE 不会更新已经存在的分页测试账号，因此统一校正密码，
-- 确保重复执行脚本后，这 20 个账号仍可用 password 登录。
UPDATE tb_user
SET password = '$2a$10$sY/2hnZETecnblXawXsOpuqvBDCdE7CR1XGwRh4xO0v7v99.PqBKC'
WHERE phone BETWEEN '13988000001' AND '13988000020';

-- 验证并发测试账号，正常应返回 20 行。
SELECT user_id, user_name, phone, status
FROM tb_user
WHERE phone BETWEEN '13988000001' AND '13988000020'
ORDER BY phone;

-- ============================================================
-- 二、基础业务与边界测试数据
-- ============================================================

-- 若你的测试账号手机号不同，只需要修改下面两行。
SET @admin_phone = '13800000000';
SET @test_user_phone = '13800001001';

SET @admin_id = (
    SELECT user_id
    FROM tb_user
    WHERE phone = @admin_phone
      AND role = 1
    LIMIT 1
);

SET @test_user_id = (
    SELECT user_id
    FROM tb_user
    WHERE phone = @test_user_phone
    LIMIT 1
);

-- 管理员不存在时，退回到任意一个正常用户作为活动创建者，避免产生孤立 creator_id。
SET @creator_id = COALESCE(
    @admin_id,
    @test_user_id,
    (SELECT user_id FROM tb_user WHERE status = 0 ORDER BY user_id LIMIT 1)
);

SELECT @creator_id AS creator_id,
       @test_user_id AS test_user_id,
       IF(@creator_id IS NULL,
          '没有可用用户，请先注册或创建管理员',
          '创建者已找到') AS preparation_result;

-- 清理上一次执行本脚本产生的数据。顺序按逻辑外键关系排列。
DELETE req
FROM tb_request req
JOIN tb_activity activity ON activity.activity_id = req.activity_id
WHERE activity.activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!';

DELETE registration
FROM tb_registration registration
JOIN tb_activity activity ON activity.activity_id = registration.activity_id
WHERE activity.activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!';

DELETE wait_record
FROM tb_wait_list wait_record
JOIN tb_activity activity ON activity.activity_id = wait_record.activity_id
WHERE activity.activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!';

DELETE FROM tb_activity
WHERE activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!';

-- 1. 报名进行中且有余量：用于正常报名、取消报名测试。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_01_报名中有余量',
    '测试正常报名、取消报名与库存恢复。',
    0,
    '第一教学楼 101',
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    DATE_ADD(NOW(), INTERVAL 2 DAY),
    DATE_ADD(NOW(), INTERVAL 3 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 3 DAY), INTERVAL 2 HOUR),
    20,
    20,
    1,
    @creator_id,
    0
);

-- 2. 报名进行中但已满：用于加入候补、取消候补测试。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_02_报名中已满员',
    '测试满员后进入候补队列。',
    2,
    '体育馆主馆',
    DATE_SUB(NOW(), INTERVAL 1 HOUR),
    DATE_ADD(NOW(), INTERVAL 2 DAY),
    DATE_ADD(NOW(), INTERVAL 3 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 3 DAY), INTERVAL 3 HOUR),
    3,
    0,
    1,
    @creator_id,
    0
);

-- 3. 已发布但报名未开始：用于时间边界校验。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_03_报名未开始',
    '测试报名开始前不能提交报名。',
    1,
    '创新创业中心',
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 3 DAY),
    DATE_ADD(NOW(), INTERVAL 4 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 4 DAY), INTERVAL 2 HOUR),
    50,
    50,
    1,
    @creator_id,
    0
);

-- 4. 报名已截止但活动尚未开始：不再初始化报名缓存。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_04_报名已截止',
    '测试报名截止后拒绝新请求。',
    3,
    '志愿服务中心',
    DATE_SUB(NOW(), INTERVAL 3 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 2 HOUR),
    30,
    15,
    1,
    @creator_id,
    1
);

-- 5. 草稿活动：用于“我创建的活动”、修改和发布测试。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_05_草稿活动',
    '测试草稿不可被普通活动列表发现，以及修改、发布流程。',
    4,
    '大学生活动中心',
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 3 DAY),
    DATE_ADD(NOW(), INTERVAL 4 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 4 DAY), INTERVAL 2 HOUR),
    40,
    40,
    0,
    @creator_id,
    0
);

-- 6. 已取消活动：用于状态展示和禁止报名测试。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_TEST_20261010_06_已取消活动',
    '测试已取消活动的详情和报名限制。',
    5,
    '线上活动',
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 2 DAY),
    DATE_ADD(NOW(), INTERVAL 3 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 3 DAY), INTERVAL 1 HOUR),
    100,
    100,
    2,
    @creator_id,
    0
);

-- 给“已满员”活动补三条真实用户的成功报名记录，方便管理员查看参与者。
-- 会排除当前测试账号，确保该账号仍可测试进入候补。
INSERT INTO tb_registration (activity_id, user_id, status)
SELECT activity.activity_id, user.user_id, 0
FROM tb_activity activity
JOIN tb_user user
  ON user.role = 0
 AND user.status = 0
 AND user.phone <> @test_user_phone
WHERE activity.activity_name = 'CP_TEST_20261010_02_报名中已满员'
ORDER BY user.user_id
LIMIT 3;

-- 为指定测试账号增加两条只用于列表展示的历史记录。
-- 测试账号不存在时，这两条 INSERT SELECT 会安全地插入 0 行。
INSERT INTO tb_registration (activity_id, user_id, status, create_time, update_time)
SELECT activity.activity_id, user.user_id, 2,
       DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()
FROM tb_activity activity
JOIN tb_user user ON user.phone = @test_user_phone
WHERE activity.activity_name = 'CP_TEST_20261010_06_已取消活动';

INSERT INTO tb_wait_list (
    activity_id, user_id, status, join_time, promoted_time, create_time, update_time
)
SELECT activity.activity_id, user.user_id, 3,
       DATE_SUB(NOW(6), INTERVAL 2 DAY), NULL,
       DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()
FROM tb_activity activity
JOIN tb_user user ON user.phone = @test_user_phone
WHERE activity.activity_name = 'CP_TEST_20261010_04_报名已截止';

-- 两条历史请求：用于“我的请求”分页和单条查询测试。
INSERT INTO tb_request (
    request_id, activity_id, user_id, status, create_time, update_time,
    is_stock_changed_when_dead_letter, is_compensate
)
SELECT UUID(), activity.activity_id, user.user_id, 1,
       DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
       NULL, NULL
FROM tb_activity activity
JOIN tb_user user ON user.phone = @test_user_phone
WHERE activity.activity_name = 'CP_TEST_20261010_06_已取消活动';

INSERT INTO tb_request (
    request_id, activity_id, user_id, status, create_time, update_time,
    is_stock_changed_when_dead_letter, is_compensate
)
SELECT UUID(), activity.activity_id, user.user_id, 2,
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
       NULL, NULL
FROM tb_activity activity
JOIN tb_user user ON user.phone = @test_user_phone
WHERE activity.activity_name = 'CP_TEST_20261010_04_报名已截止';

-- 显示刚生成的数据及其真实 activity_id。
SELECT activity_id,
       activity_name,
       status,
       total_capacity,
       remaining_capacity,
       registration_cache_status,
       registration_start_time,
       registration_end_time
FROM tb_activity
WHERE activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!'
ORDER BY activity_name;

SELECT request.request_id,
       request.activity_id,
       request.user_id,
       request.status,
       request.create_time
FROM tb_request request
JOIN tb_activity activity ON activity.activity_id = request.activity_id
WHERE activity.activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!'
ORDER BY request.create_time DESC;

-- 正常情况下无需手动执行 Redis 命令：
-- Spring Boot 启动后，scheduleActivityCacheInitialization 每 30 秒扫描一次
-- status=1 且 registration_cache_status=0 的活动，并自动创建 Hash 和 TTL。

-- 若你确实需要手动初始化 Redis，先停止 Spring Boot，再执行下面查询。
-- 查询结果每一行都是一条可复制到 redis-cli 执行的命令。
SELECT CONCAT(
    'EVAL "redis.call(''HSET'',KEYS[1],''remainingCapacity'',ARGV[1],',
    '''registrationStartTime'',ARGV[2],''registrationEndTime'',ARGV[3]);',
    ' return redis.call(''EXPIREAT'',KEYS[1],ARGV[4])" 1 ',
    'registration:activity:', activity_id, ' ',
    remaining_capacity, ' ',
    TIMESTAMPDIFF(
        SECOND,
        '1970-01-01 00:00:00',
        CONVERT_TZ(registration_start_time, '+08:00', '+00:00')
    ) * 1000, ' ',
    TIMESTAMPDIFF(
        SECOND,
        '1970-01-01 00:00:00',
        CONVERT_TZ(registration_end_time, '+08:00', '+00:00')
    ) * 1000, ' ',
    TIMESTAMPDIFF(
        SECOND,
        '1970-01-01 00:00:00',
        CONVERT_TZ(registration_end_time, '+08:00', '+00:00')
    )
) AS redis_command
FROM tb_activity
WHERE activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!'
  AND status = 1
  AND registration_end_time > DATE_ADD(NOW(), INTERVAL 10 SECOND)
ORDER BY activity_name;

-- 只有手动 Redis 命令全部成功后，才执行下面这条语句，避免定时任务重复初始化：
-- UPDATE tb_activity
-- SET registration_cache_status = 1
-- WHERE activity_name LIKE 'CP!_TEST!_20261010!_%' ESCAPE '!'
--   AND status = 1
--   AND registration_end_time > DATE_ADD(NOW(), INTERVAL 10 SECOND);

-- ============================================================
-- 三、分页查询测试数据
-- 目标账号：13800001001
-- 生成 25 个自建活动、12 条报名、12 条候补，
-- 以及一个包含 12 名成功报名者的活动。
-- ============================================================

SET @target_phone = '13800001001';
SET @target_user_id = (
    SELECT user_id
    FROM tb_user
    WHERE phone = @target_phone
    LIMIT 1
);

SELECT @target_user_id AS target_user_id,
       IF(@target_user_id IS NULL,
          '未找到手机号 13800001001，请先创建该用户',
          '目标用户已找到') AS preparation_result;

-- 清理上次执行本脚本产生的活动关联数据。
DELETE request_record
FROM tb_request request_record
JOIN tb_activity activity ON activity.activity_id = request_record.activity_id
WHERE activity.activity_name LIKE 'CP!_PAGE!_TEST!_%' ESCAPE '!';

DELETE registration
FROM tb_registration registration
JOIN tb_activity activity ON activity.activity_id = registration.activity_id
WHERE activity.activity_name LIKE 'CP!_PAGE!_TEST!_%' ESCAPE '!';

DELETE wait_record
FROM tb_wait_list wait_record
JOIN tb_activity activity ON activity.activity_id = wait_record.activity_id
WHERE activity.activity_name LIKE 'CP!_PAGE!_TEST!_%' ESCAPE '!';

DELETE FROM tb_activity
WHERE activity_name LIKE 'CP!_PAGE!_TEST!_%' ESCAPE '!';

DROP TEMPORARY TABLE IF EXISTS tmp_cp_page_numbers;
CREATE TEMPORARY TABLE tmp_cp_page_numbers
(
    number_value int unsigned NOT NULL PRIMARY KEY
);

INSERT INTO tmp_cp_page_numbers (number_value)
VALUES (1), (2), (3), (4), (5), (6),
       (7), (8), (9), (10), (11), (12);

-- 创建 12 个活动，供目标账号的“我的报名记录”分页查询。
-- 报名期已经结束但活动尚未开始，避免后台任务创建无意义的 Redis 报名缓存。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
)
SELECT CONCAT('CP_PAGE_TEST_REG_', LPAD(number_value, 2, '0')),
       CONCAT('我的报名分页测试活动，第 ', number_value, ' 条。'),
       MOD(number_value, 6),
       CONCAT('报名测试场地 ', number_value),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 30 DAY), INTERVAL number_value HOUR),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL number_value HOUR),
       DATE_ADD(DATE_ADD(NOW(), INTERVAL 10 DAY), INTERVAL number_value DAY),
       DATE_ADD(
           DATE_ADD(DATE_ADD(NOW(), INTERVAL 10 DAY), INTERVAL number_value DAY),
           INTERVAL 2 HOUR
       ),
       30,
       CASE MOD(number_value - 1, 3)
           WHEN 0 THEN 29
           ELSE 30
       END,
       1,
       @target_user_id,
       1
FROM tmp_cp_page_numbers;

-- 为目标账号创建 12 条报名记录，状态依次循环：成功、已取消、已失效。
INSERT INTO tb_registration (
    activity_id, user_id, status, create_time, update_time
)
SELECT activity.activity_id,
       @target_user_id,
       MOD(number_value - 1, 3),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL number_value HOUR),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL number_value HOUR)
FROM tmp_cp_page_numbers numbers
JOIN tb_activity activity
  ON activity.activity_name = CONCAT('CP_PAGE_TEST_REG_', LPAD(numbers.number_value, 2, '0'));

-- 创建另外 12 个活动，供目标账号的“我的候补记录”分页查询。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
)
SELECT CONCAT('CP_PAGE_TEST_WAIT_', LPAD(number_value, 2, '0')),
       CONCAT('我的候补分页测试活动，第 ', number_value, ' 条。'),
       MOD(number_value + 2, 6),
       CONCAT('候补测试场地 ', number_value),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 30 DAY), INTERVAL number_value HOUR),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL number_value HOUR),
       DATE_ADD(DATE_ADD(NOW(), INTERVAL 12 DAY), INTERVAL number_value DAY),
       DATE_ADD(
           DATE_ADD(DATE_ADD(NOW(), INTERVAL 12 DAY), INTERVAL number_value DAY),
           INTERVAL 2 HOUR
       ),
       20,
       20,
       1,
       @target_user_id,
       1
FROM tmp_cp_page_numbers;

-- 为目标账号创建 12 条历史候补记录，奇数为已取消，偶数为已失效。
-- 不创建“候补中”记录，避免数据库与 Redis 候补参与标记不一致。
INSERT INTO tb_wait_list (
    activity_id, user_id, status, join_time, promoted_time, create_time, update_time
)
SELECT activity.activity_id,
       @target_user_id,
       CASE WHEN MOD(number_value, 2) = 1 THEN 2 ELSE 3 END,
       DATE_ADD(DATE_SUB(NOW(6), INTERVAL 25 DAY), INTERVAL number_value MINUTE),
       NULL,
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL number_value MINUTE),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL number_value MINUTE)
FROM tmp_cp_page_numbers numbers
JOIN tb_activity activity
  ON activity.activity_name = CONCAT('CP_PAGE_TEST_WAIT_', LPAD(numbers.number_value, 2, '0'));

-- 创建一个已结束报名但尚未开始的活动，用于验证“查看活动报名用户”的分页接口。
-- 该接口只要求活动处于已发布状态，因此无需额外创建 Redis 报名缓存。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
) VALUES (
    'CP_PAGE_TEST_PARTICIPANTS_12',
    '该活动包含 12 名成功报名用户，第一页应为 10 条，第二页应为 2 条。',
    5,
    '分页压力测试场地',
    DATE_SUB(NOW(), INTERVAL 3 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 DAY), INTERVAL 2 HOUR),
    20,
    8,
    1,
    @target_user_id,
    1
);

-- 给上面的活动加入 12 名成功报名者。
INSERT INTO tb_registration (
    activity_id, user_id, status, create_time, update_time
)
SELECT activity.activity_id,
       user.user_id,
       0,
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL numbers.number_value MINUTE),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL numbers.number_value MINUTE)
FROM tmp_cp_page_numbers numbers
JOIN tb_user user
  ON user.phone = CONCAT('139880000', LPAD(numbers.number_value, 2, '0'))
JOIN tb_activity activity
  ON activity.activity_name = 'CP_PAGE_TEST_PARTICIPANTS_12';

-- 验证生成结果。三个数量都应该是 12。
SELECT COUNT(*) AS generated_registration_count
FROM tb_registration registration
JOIN tb_activity activity ON activity.activity_id = registration.activity_id
WHERE registration.user_id = @target_user_id
  AND activity.activity_name LIKE 'CP!_PAGE!_TEST!_REG!_%' ESCAPE '!';

SELECT COUNT(*) AS generated_wait_list_count
FROM tb_wait_list wait_record
JOIN tb_activity activity ON activity.activity_id = wait_record.activity_id
WHERE wait_record.user_id = @target_user_id
  AND activity.activity_name LIKE 'CP!_PAGE!_TEST!_WAIT!_%' ESCAPE '!';

SELECT activity.activity_id AS participants_test_activity_id,
       COUNT(registration.registration_id) AS generated_participant_count
FROM tb_activity activity
LEFT JOIN tb_registration registration
       ON registration.activity_id = activity.activity_id
      AND registration.status = 0
WHERE activity.activity_name = 'CP_PAGE_TEST_PARTICIPANTS_12'
GROUP BY activity.activity_id;

-- 输出全部测试活动 ID，便于在 Apifox 中调用详情和分页接口。
SELECT activity_id,
       activity_name,
       status,
       total_capacity,
       remaining_capacity,
       registration_cache_status
FROM tb_activity
WHERE activity_name LIKE 'CP!_PAGE!_TEST!_%' ESCAPE '!'
ORDER BY activity_id;

DROP TEMPORARY TABLE IF EXISTS tmp_cp_page_numbers;


