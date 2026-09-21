create database if not exists campus_pass
    default character set utf8mb4
    default collate utf8mb4_0900_ai_ci;

use campus_pass;

create table tb_user(
    user_id bigint unsigned not null auto_increment comment '用户id',
    user_name varchar(50) not null comment '用户名',
    password varchar(255) not null comment '密码哈希',
    student_id varchar(20) not null comment  '学号',
    phone varchar(20) not null comment  '手机号',

    role tinyint unsigned not null default 0 comment '角色：0普通用户，1管理员',
    status tinyint unsigned not null default 0 comment '状态： 0正常，1禁用',

    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '修改时间',

    primary key (user_id),
    unique key uk_user_name(user_name),
    unique key uk_student_id(student_id),
    unique key uk_phone (phone),

    constraint chk_user_role check(role in(0,1)),
    constraint chk_user_status check(status in (0,1))
) engine =innodb
default charset utf8mb4
comment '用户表';


create table tb_activity
(
    activity_id             bigint unsigned  not null auto_increment comment '活动id',
    activity_name           varchar(100)     not null comment '活动名',
    activity_description    text comment '活动介绍',
    activity_type           tinyint unsigned not null comment '活动类型',
    activity_location       varchar(100)     not null comment '活动地点',

    registration_start_time datetime         not null comment '报名开始时间',
    registration_end_time   datetime         not null comment '报名截止时间',

    start_time              datetime         not null comment '活动开始时间',
    end_time                datetime         not null comment '活动结束时间',

    total_capacity          int unsigned     not null comment '总名额',
    remaining_capacity      int unsigned     not null comment '剩余名额',

    status                  tinyint unsigned not null default 0 comment '状态：0草稿，1已发布，2已取消',
    creator_id              bigint unsigned  not null comment '创建人id',

    create_time             datetime                  default current_timestamp comment '创建时间',
    update_time             datetime                  default current_timestamp on update current_timestamp comment '更新时间',

    primary key (activity_id),

    key idx_status_start_time (status, start_time),
    key idx_activity_type (activity_type),
    key idx_creator_id (creator_id),

    constraint chk_activity_status check ( status in (0, 1, 2) ),

    constraint chk_activity_time
        check (
            registration_start_time < registration_end_time
                and registration_end_time <= start_time
                and start_time < end_time
            ),

    constraint chk_activity_capacity
        check (
            total_capacity > 0
                and remaining_capacity <= total_capacity
            )
)engine =innodb
default charset =utf8mb4
comment ='活动表';

create table tb_registration(
    registration_id bigint unsigned not null auto_increment comment '报名id',
    activity_id bigint unsigned not null comment '活动id',
    user_id bigint unsigned not null comment '用户id',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp
                            on update current_timestamp comment '更新时间',
    status tinyint unsigned not null default 0 comment '报名状态：0成功，1已取消',

    primary key (registration_id),
    unique key uk_activity_user (activity_id,user_id),

    key idx_user_id(user_id),
    constraint chk_registration_status check(status in (0,1))
)engine innodb
charset utf8mb4
comment '报名表';

create table tb_wait_list(
    wait_id bigint unsigned auto_increment not null comment '候补id',
    activity_id bigint unsigned not null comment '活动id',
    user_id bigint unsigned not null comment '用户id',
    status tinyint unsigned not null default 0 comment '候补状态：0候补中，1已转正，2已取消，3已失效',
    join_time datetime not null default current_timestamp comment '加入候补时间',
    promoted_time datetime  comment '候补成功时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp
                    on update current_timestamp comment '更新时间',

    primary key (wait_id),
    unique key un_activity_user (activity_id,user_id),
    key idx_wait_user_id(user_id),
    KEY idx_activity_status_join_time (
                                       activity_id,
                                       status,
                                       join_time
        ),
    constraint chk_wait_promoted
                         check (
                             (
                                 status=1
                                 and promoted_time is not null
                                 and promoted_time>=join_time
                                 )or(
                                     status in(0,2,3)
                                     and promoted_time is null
                                 )
                             ),
    constraint chk_wait_status check ( status in(0,1,2,3) )


)engine innodb
charset utf8mb4
comment'候补表';





