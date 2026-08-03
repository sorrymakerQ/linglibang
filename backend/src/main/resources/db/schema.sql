-- ==========================================
-- 邻里帮 - 社区互助平台 数据库建表脚本
-- ==========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS linlibang
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE linlibang;

-- ==========================================
-- 用户表
-- ==========================================
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone` VARCHAR(11) NOT NULL COMMENT '手机号',
    `password` VARCHAR(128) NOT NULL COMMENT '密码（加密存储）',
    `nickname` VARCHAR(32) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `community` VARCHAR(128) DEFAULT NULL COMMENT '所在小区',
    `lng` DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `lat` DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `credit` INT DEFAULT 100 COMMENT '信用分，默认100',
    `help_count` INT DEFAULT 0 COMMENT '累计帮助次数',
    `intro` VARCHAR(256) DEFAULT NULL COMMENT '个人简介',
    `role` TINYINT DEFAULT 1 COMMENT '角色 1普通用户 2管理员',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常',
    `permissions` VARCHAR(256) DEFAULT 'help:publish,order:accept,message:send' COMMENT '用户权限码列表，逗号分隔',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_community` (`community`),
    KEY `idx_lng_lat` (`lng`, `lat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==========================================
-- 求助分类表
-- ==========================================
DROP TABLE IF EXISTS `tb_category`;
CREATE TABLE `tb_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(32) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(256) DEFAULT NULL COMMENT '分类图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求助分类表';

-- 初始分类数据
INSERT INTO `tb_category` (`name`, `icon`, `sort`) VALUES
('家电维修', '🔧', 1),
('管道疏通', '🚰', 2),
('搬家搬运', '📦', 3),
('代取快递', '📬', 4),
('宠物照看', '🐱', 5),
('家教辅导', '📚', 6),
('电脑维修', '💻', 7),
('家政保洁', '🧹', 8),
('老人陪护', '👴', 9),
('其他帮助', '🤝', 10);

-- ==========================================
-- 求助信息表
-- ==========================================
DROP TABLE IF EXISTS `tb_help_request`;
CREATE TABLE `tb_help_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '求助ID',
    `user_id` BIGINT NOT NULL COMMENT '发布者ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `title` VARCHAR(128) NOT NULL COMMENT '求助标题',
    `description` TEXT COMMENT '详细描述',
    `images` VARCHAR(1024) DEFAULT NULL COMMENT '图片URL列表，逗号分隔',
    `reward` DECIMAL(10,2) DEFAULT 0 COMMENT '酬劳（元）',
    `address` VARCHAR(256) NOT NULL COMMENT '地址',
    `lng` DECIMAL(10,6) NOT NULL COMMENT '经度',
    `lat` DECIMAL(10,6) NOT NULL COMMENT '纬度',
    `status` TINYINT DEFAULT 1 COMMENT '状态 1待接单 2进行中 3已完成 4已取消',
    `urgent` TINYINT DEFAULT 0 COMMENT '是否紧急 0否 1是',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_lng_lat` (`lng`, `lat`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求助信息表';

-- ==========================================
-- 订单表（接单记录）
-- ==========================================
DROP TABLE IF EXISTS `tb_order`;
CREATE TABLE `tb_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `help_id` BIGINT NOT NULL COMMENT '求助ID',
    `publisher_id` BIGINT NOT NULL COMMENT '发布者ID',
    `helper_id` BIGINT NOT NULL COMMENT '接单者ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态 1已接单 2进行中 3已完成 4已取消 5已评价',
    `cancel_reason` VARCHAR(256) DEFAULT NULL COMMENT '取消原因',
    `accept_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '接单时间',
    `finish_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `publisher_score` TINYINT DEFAULT NULL COMMENT '发布者评分 1-5',
    `helper_score` TINYINT DEFAULT NULL COMMENT '接单者评分 1-5',
    `publisher_comment` VARCHAR(512) DEFAULT NULL COMMENT '发布者评价',
    `helper_comment` VARCHAR(512) DEFAULT NULL COMMENT '接单者评价',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_help_id` (`help_id`),
    KEY `idx_publisher_id` (`publisher_id`),
    KEY `idx_helper_id` (`helper_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ==========================================
-- 消息通知表
-- ==========================================
DROP TABLE IF EXISTS `tb_notification`;
CREATE TABLE `tb_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id` BIGINT NOT NULL COMMENT '接收者ID',
    `title` VARCHAR(128) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(512) DEFAULT NULL COMMENT '通知内容',
    `type` TINYINT DEFAULT 1 COMMENT '类型 1系统通知 2订单通知 3评价通知',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读 0未读 1已读',
    `related_id` BIGINT DEFAULT NULL COMMENT '关联业务ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ==========================================
-- 聊天消息表
-- ==========================================
DROP TABLE IF EXISTS `tb_chat_message`;
CREATE TABLE `tb_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `help_id` BIGINT DEFAULT NULL COMMENT '关联求助ID（求助私信场景，订单聊天时为NULL）',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联订单ID（求助私信场景为NULL）',
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `receiver_id` BIGINT NOT NULL COMMENT '接收者ID',
    `content` VARCHAR(500) NOT NULL COMMENT '消息内容',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读 0未读 1已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_help_id` (`help_id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_receiver_id` (`receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';
