create table tb_activity
(
    activity_id             bigint unsigned auto_increment comment '活动id'
        primary key,
    activity_name           varchar(100)                               not null comment '活动名',
    activity_description    text                                       null comment '活动介绍',
    activity_type           tinyint unsigned                           not null comment '活动类型: 0 = 讲座 / 宣讲
    ,1 = 竞赛 / 比赛,2 = 体育活动,3 = 志愿服务,4 = 社团 / 文娱活动,5 = 其他',
    activity_location       varchar(100)                               not null comment '活动地点',
    registration_start_time datetime                                   not null comment '报名开始时间',
    registration_end_time   datetime                                   not null comment '报名截止时间',
    start_time              datetime                                   not null comment '活动开始时间',
    end_time                datetime                                   not null comment '活动结束时间',
    total_capacity          int unsigned                               not null comment '总名额',
    remaining_capacity      int unsigned                               not null comment '剩余名额',
    status                  tinyint unsigned default '0'               not null comment '状态：0草稿，1已发布，2已取消',
    creator_id              bigint unsigned                            not null comment '创建人id',
    create_time             datetime         default CURRENT_TIMESTAMP null comment '创建时间',
    update_time             datetime         default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint chk_activity_capacity
        check ((`total_capacity` > 0) and (`remaining_capacity` <= `total_capacity`)),
    constraint chk_activity_status
        check (`status` in (0, 1, 2)),
    constraint chk_activity_time
        check ((`registration_start_time` < `registration_end_time`) and (`registration_end_time` < `start_time`) and
               (`start_time` < `end_time`)),
    constraint chk_activity_type
        check (`activity_type` in (0, 1, 2, 3, 4, 5))
)
    comment '活动表';

create index idx_activity_type
    on tb_activity (activity_type);

create index idx_creator_id
    on tb_activity (creator_id);

create index idx_creator_id_activity_id
    on tb_activity (creator_id, activity_id);

create index idx_status_start_time
    on tb_activity (status, start_time);

create table tb_registration
(
    registration_id bigint unsigned auto_increment comment '报名id'
        primary key,
    activity_id     bigint unsigned                            not null comment '活动id',
    user_id         bigint unsigned                            not null comment '用户id',
    create_time     datetime         default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime         default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    status          tinyint unsigned default '0'               not null comment '报名状态：0成功，1已取消，2已失效；候补仅记录在候补表',
    constraint uk_activity_user
        unique (activity_id, user_id),
    constraint chk_registration_status
        check (`status` in (0, 1, 2))
)
    comment '报名表';

create index idx_user_id
    on tb_registration (user_id);

create table tb_user
(
    user_id     bigint unsigned auto_increment comment '用户id'
        primary key,
    user_name   varchar(50)                                not null comment '用户名',
    password    varchar(255)                               not null comment '密码哈希',
    student_id  varchar(20)                                not null comment '学号',
    phone       varchar(20)                                not null comment '手机号',
    role        tinyint unsigned default '0'               not null comment '角色：0普通用户，1管理员',
    status      tinyint unsigned default '0'               not null comment '状态： 0正常，1禁用',
    create_time datetime         default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime         default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '修改时间',
    constraint uk_phone
        unique (phone),
    constraint uk_student_id
        unique (student_id),
    constraint uk_user_name
        unique (user_name),
    constraint chk_user_role
        check (`role` in (0, 1)),
    constraint chk_user_status
        check (`status` in (0, 1))
)
    comment '用户表';

create table tb_wait_list
(
    wait_id       bigint unsigned auto_increment comment '候补id'
        primary key,
    activity_id   bigint unsigned                            not null comment '活动id',
    user_id       bigint unsigned                            not null comment '用户id',
    status        tinyint unsigned default '0'               not null comment '候补状态：0候补中，1已转正，2已取消，3已失效',
    join_time     datetime(6)      default CURRENT_TIMESTAMP(6) not null comment '本次加入候补时间，重新加入时刷新',
    promoted_time datetime(6)                                   null comment '候补成功时间',
    create_time   datetime         default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime         default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint un_activity_user
        unique (activity_id, user_id),
    constraint chk_wait_promoted
        check (((`status` = 1) and (`promoted_time` is not null) and (`promoted_time` >= `join_time`)) or
               ((`status` in (0, 2, 3)) and (`promoted_time` is null))),
    constraint chk_wait_status
        check (`status` in (0, 1, 2, 3))
)
    comment '候补表';

create index idx_activity_status_join_time
    on tb_wait_list (activity_id, status, join_time, wait_id);

create index idx_wait_user_id
    on tb_wait_list (user_id);

