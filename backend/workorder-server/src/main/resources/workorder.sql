create database wos;
use wos;
drop table if exists work_order;
create table work_order(
    id bigint primary key NOT NULL auto_increment comment 'id',
    code varchar(64) NOT NULL comment '编号',
    type tinyint not null  comment '类型',
    title varchar(128) comment '工单标题',
    priority_level tinyint comment '优先级',
    status int not null comment '状态',
    flow_id bigint not null comment '流程id',
    create_time datetime not null default CURRENT_TIMESTAMP comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '更新时间',
    cancel_time datetime comment '取消时间',
    delete_time datetime comment '删除时间',
    deadline_time datetime comment '截止时间',
    content text comment '详情',
    accessory_url varchar(128) comment '附件url',
    accessory_name varchar(128) comment '附件名称',
    deleted tinyint not null DEFAULT 0 comment '删除位'
)comment '工单';

drop table if exists handle_log;

drop table if exists message;
create table message(
   id bigint primary key NOT NULL auto_increment comment 'id',
   receiver_id bigint NOT NULL comment '接收人id',
   sender_id bigint NOT NULL comment '发送人id',
   type int NOT NULL comment '类型，',
   content text not null comment '内容',
   send_time datetime comment '发送时间',
   deleted tinyint not null DEFAULT 0 comment '删除位'
) comment '消息';

Drop table if exists handle_user_info;
create table handle_user_info(
    id bigint primary key NOT NULL auto_increment comment 'id',
    order_id bigint NOT NULL comment '工单id',
    user_id bigint NOT NULL COMMENT '用户id',
    user_name varchar(64) NOT NULL COMMENT '用户名',
    handle_type tinyint NOT NULL COMMENT '用户类型（1：提交，2：审核，3：派单，4：处理，5：确认）',
    finished tinyint NOT NULL comment '已完成操作，0未完成，1已完成',
    company_code varchar(64) NOT NULL COMMENT '公司id',
    company_name varchar(128) NOT NULL COMMENT '公司名',
    department_code varchar(64)  NOT NULL COMMENT '部门id',
    department_name varchar(128) NOT NULL COMMENT '部门名',
    handle_time datetime comment '处理时间',
    create_time datetime NOT NULL default CURRENT_TIMESTAMP comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '修改时间',
    remark varchar(255) comment '备注',
    deleted tinyint not null DEFAULT 0 comment '删除位'
)comment '工单操作信息';

drop table if exists company;
CREATE TABLE company (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '公司ID',
                         name VARCHAR(100) NOT NULL UNIQUE COMMENT '公司名称',
                         code VARCHAR(50) UNIQUE COMMENT '公司编码',
                         parent_company_code VARCHAR(50) DEFAULT NULL COMMENT '上级公司编码',
                         level TINYINT DEFAULT 1 COMMENT '公司层级（1：总部，2：省公司，3：市公司）',
                         create_time datetime default CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) COMMENT='公司信息表';

drop table if exists department;
CREATE TABLE department (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '部门ID',
                            name VARCHAR(100) NOT NULL COMMENT '部门名称',
                            code VARCHAR(50) UNIQUE COMMENT '部门编码',
                            parent_department_code VARCHAR(50) DEFAULT NULL COMMENT '上级部门ID(null表示顶级）',
                            company_code VARCHAR(50) NOT NULL COMMENT '所属公司编码',
                            leader_number VARCHAR(50) DEFAULT NULL COMMENT '部门主管员工工号',
                            create_time datetime default CURRENT_TIMESTAMP COMMENT '创建时间',
                            update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) COMMENT='部门信息表';

drop table if exists flow;
CREATE TABLE flow (
                       id bigint primary key NOT NULL AUTO_INCREMENT comment 'id',
                       flow_id bigint NOT NULL COMMENT '流程id',
                       flow_name varchar(128) NOT NULL COMMENT '流程名',
                       node_type tinyint NOT NULL COMMENT '节点类型:审批2，指派3，验收5',
                       handler_id bigint NOT NULL COMMENT '处理人id',
                       handler_name varchar(128) NOT NULL COMMENT '处理人名',
                       is_parallel tinyint DEFAULT '0' COMMENT '是否并行处理',
                       head_flow_id bigint comment '头节点id(只有审核有)',
                       next_flow_id bigint comment '下一节点id(只有审核有)',
                       is_last_node tinyint DEFAULT '0' COMMENT '是否为该流程终止节点',
                       create_time datetime NOT NULL default CURRENT_TIMESTAMP COMMENT '创建时间',
                       update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='工单流程定义表';

drop table if exists staff;
CREATE TABLE staff (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                       staff_number VARCHAR(128) NOT NULL COMMENT '工号',
                       password VARCHAR(100) NOT NULL COMMENT '密码（加密）',
                       name VARCHAR(100) NOT NULL COMMENT '姓名',
                       company_code VARCHAR(50) NOT NULL COMMENT '所属公司编号',
                       company VARCHAR(100) COMMENT '所属公司',
                       department_code VARCHAR(50) COMMENT '部门编号',
                       department VARCHAR(100) COMMENT '部门',
                       position VARCHAR(100) COMMENT '职位',
                       status TINYINT DEFAULT 0 COMMENT '状态：0 正常，1 休假，2 停职，3 离职',
                       manager_number VARCHAR(50) COMMENT '直属领导工号',
                       manager_name VARCHAR(100) COMMENT '直属领导姓名',
                       phone VARCHAR(20) UNIQUE  COMMENT '手机号码',
                       email VARCHAR(100)  UNIQUE COMMENT '邮箱',
                       create_time datetime default CURRENT_TIMESTAMP COMMENT '创建时间',
                       update_time datetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) COMMENT='职工表';
ALTER TABLE staff ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user';


