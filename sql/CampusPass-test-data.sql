-- CampusPass 容器演示环境初始化数据
-- 前置条件：已在 campus_pass 数据库执行 CampusPass.sql。
-- 仅用于独立开发/演示环境，所有账号的公开演示密码均为 password。
-- 请勿在公网生产环境使用这些账号和密码。
-- 一次性种子脚本：不要重复执行，不使用 mysql --force 忽略错误。
-- 重复执行会因账号唯一约束报错；遇到错误应 ROLLBACK，不要继续执行 COMMIT。
-- 不删除、不更新现有业务数据；不依赖旧综合测试脚本中的账号。
-- Docker 首次初始化：保存为 /docker-entrypoint-initdb.d/02-demo-data.sql。
-- 已初始化的数据卷不会自动重跑此文件，需要手动导入。
-- 日期以导入时的北京时间为基准。仅预置用户和活动，报名/候补通过接口实际产生。

SET NAMES utf8mb4;
USE campus_pass;
SET SESSION time_zone = '+08:00';
SET @demo_now = NOW();
SET @demo_password_hash = '$2a$10$sY/2hnZETecnblXawXsOpuqvBDCdE7CR1XGwRh4xO0v7v99.PqBKC';

START TRANSACTION;

-- 手机号为演示占位号码，不用于发送真实短信。
-- 13800000000 管理员；13800001001 发布者；13800001002～13800001005 普通用户。
INSERT INTO tb_user
(user_name, password, student_id, phone, role, status, auth_version)
VALUES
    ('演示管理员', @demo_password_hash, 'DEMO_ADMIN', '13800000000', 1, 0, 0),
    ('演示活动发布者', @demo_password_hash, 'DEMO_HOST', '13800001001', 0, 0, 0),
    ('演示用户小林', @demo_password_hash, 'DEMO_0002', '13800001002', 0, 0, 0),
    ('演示用户小陈', @demo_password_hash, 'DEMO_0003', '13800001003', 0, 0, 0),
    ('演示用户小周', @demo_password_hash, 'DEMO_0004', '13800001004', 0, 0, 0),
    ('演示用户小李', @demo_password_hash, 'DEMO_0005', '13800001005', 0, 0, 0);

SET @demo_creator_id = (
    SELECT user_id FROM tb_user WHERE phone = '13800001001'
);

-- status: 0草稿、1已发布；缓存状态设为0，交由后端定时任务初始化。
-- 不伪造满员活动：所有剩余名额等于总名额，报名记录和候补记录初始为空。
INSERT INTO tb_activity (
    activity_name, activity_description, activity_type, activity_location,
    registration_start_time, registration_end_time, start_time, end_time,
    total_capacity, remaining_capacity, status, creator_id, registration_cache_status
)
VALUES
    ('Java 后端技术交流会', '基础报名演示：导入前一天开始报名，导入后30天截止，名额充足。', 0, '信息楼报告厅',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 30 DAY),
     DATE_ADD(@demo_now, INTERVAL 31 DAY), DATE_ADD(@demo_now, INTERVAL 32 DAY),
     100, 100, 1, @demo_creator_id, 0),
    ('校园算法挑战赛', '竞赛类型筛选与报名演示，报名窗口保留90天。', 1, '计算机实验中心',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 90 DAY),
     DATE_ADD(@demo_now, INTERVAL 91 DAY), DATE_ADD(@demo_now, INTERVAL 92 DAY),
     50, 50, 1, @demo_creator_id, 0),
    ('校园趣味运动会', '体育活动演示，报名窗口保留60天。', 2, '学校田径场',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 60 DAY),
     DATE_ADD(@demo_now, INTERVAL 61 DAY), DATE_ADD(@demo_now, INTERVAL 62 DAY),
     80, 80, 1, @demo_creator_id, 0),
    ('图书馆志愿服务', '志愿服务演示，报名窗口保留180天。', 3, '图书馆服务大厅',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 180 DAY),
     DATE_ADD(@demo_now, INTERVAL 181 DAY), DATE_ADD(@demo_now, INTERVAL 182 DAY),
     20, 20, 1, @demo_creator_id, 0),
    ('摄影社团开放日', '社团活动演示，报名窗口保留90天。', 4, '大学生活动中心',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 90 DAY),
     DATE_ADD(@demo_now, INTERVAL 91 DAY), DATE_ADD(@demo_now, INTERVAL 92 DAY),
     30, 30, 1, @demo_creator_id, 0),
    ('长期开放体验活动', '长期演示入口：导入后365天报名截止，366天后活动开始。', 5, '校园体验中心',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 365 DAY),
     DATE_ADD(@demo_now, INTERVAL 366 DAY), DATE_ADD(@demo_now, INTERVAL 367 DAY),
     200, 200, 1, @demo_creator_id, 0),
    ('单名额报名与候补体验', '仅1个名额。两个非发布者账号依次报名，验证正式报名与候补；取消正式报名后验证递补。报名窗口保留90天。', 0, '研讨室A',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 90 DAY),
     DATE_ADD(@demo_now, INTERVAL 91 DAY), DATE_ADD(@demo_now, INTERVAL 92 DAY),
     1, 1, 1, @demo_creator_id, 0),
    ('尚未开放的技术沙龙', '边界样例：导入后30天才开放报名，180天后截止。', 0, '研讨室B',
     DATE_ADD(@demo_now, INTERVAL 30 DAY), DATE_ADD(@demo_now, INTERVAL 180 DAY),
     DATE_ADD(@demo_now, INTERVAL 181 DAY), DATE_ADD(@demo_now, INTERVAL 182 DAY),
     20, 20, 1, @demo_creator_id, 0),
    ('待发布的读书分享会', '草稿样例：使用演示活动发布者账号，在我的活动中查看、编辑并发布。', 4, '图书馆讨论室',
     DATE_ADD(@demo_now, INTERVAL -1 DAY), DATE_ADD(@demo_now, INTERVAL 90 DAY),
     DATE_ADD(@demo_now, INTERVAL 91 DAY), DATE_ADD(@demo_now, INTERVAL 92 DAY),
     20, 20, 0, @demo_creator_id, 0);

COMMIT;

-- 验证导入：应显示6个演示账号、9个活动。
SELECT user_id, user_name, phone, role, status
FROM tb_user
WHERE student_id IN ('DEMO_ADMIN', 'DEMO_HOST', 'DEMO_0002', 'DEMO_0003', 'DEMO_0004', 'DEMO_0005')
ORDER BY user_id;

SELECT activity_id, activity_name, status, total_capacity, remaining_capacity,
       registration_start_time, registration_end_time, start_time, end_time
FROM tb_activity
WHERE creator_id = @demo_creator_id
ORDER BY activity_id;

