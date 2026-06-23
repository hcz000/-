CREATE DATABASE IF NOT EXISTS knowledgeplanet
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE knowledgeplanet;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(128) UNIQUE,
    avatar VARCHAR(512),
    role VARCHAR(32) DEFAULT 'user',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_role (role),
    FULLTEXT INDEX ft_users_username_email (username, email) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS planet (
    planet_id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    master BIGINT NOT NULL,
    member_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    description TEXT,
    category VARCHAR(64),
    status INT,
    INDEX idx_planet_deleted (deleted),
    INDEX idx_planet_name (name),
    INDEX idx_planet_master (master),
    FULLTEXT INDEX ft_planet_search (name, description, category) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星球表';

CREATE TABLE IF NOT EXISTS planet_member (
    id BIGINT PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_planet_member (planet_id, user_id),
    INDEX idx_pm_planet (planet_id),
    INDEX idx_pm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星球成员关系表';

CREATE TABLE IF NOT EXISTS postings (
    postings_id BIGINT PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    status SMALLINT DEFAULT 0 NOT NULL,
    reply_count INT DEFAULT 0 NOT NULL,
    like_count INT DEFAULT 0 NOT NULL,
    type VARCHAR(32),
    images JSON DEFAULT (JSON_ARRAY()),
    audit_status SMALLINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    first_comment_id BIGINT,
    INDEX idx_postings_deleted (deleted),
    INDEX idx_postings_planet (planet_id),
    INDEX idx_postings_user (user_id),
    INDEX idx_postings_type (type),
    INDEX idx_postings_create_time (create_time DESC),
    INDEX idx_postings_audit_status (audit_status),
    INDEX idx_postings_push_base (deleted, status, audit_status, create_time DESC),
    INDEX idx_postings_push_type (type, deleted, status, audit_status, create_time DESC),
    INDEX idx_postings_planet_live_ctime (planet_id, deleted, status, audit_status, create_time DESC),
    INDEX idx_postings_user_ctime (user_id, create_time DESC),
    FULLTEXT INDEX ft_postings_title_content (title, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子表';

CREATE TABLE IF NOT EXISTS primary_comment (
    id BIGINT PRIMARY KEY,
    postings_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,
    like_count INT DEFAULT 0 NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_primary_comment_postings (postings_id),
    INDEX idx_primary_comment_user (user_id),
    INDEX idx_primary_comment_create_time (create_time DESC),
    INDEX idx_primary_comment_postings_live_ctime (postings_id, deleted, create_time DESC),
    INDEX idx_primary_comment_user_live_ctime (user_id, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级评论表';

CREATE TABLE IF NOT EXISTS secondary_comment (
    id BIGINT PRIMARY KEY,
    postings_id BIGINT NOT NULL,
    primary_comment_id BIGINT NOT NULL,
    parent_id BIGINT,
    user_id BIGINT NOT NULL,
    reply_to_username VARCHAR(128),
    content TEXT,
    like_count INT DEFAULT 0 NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_secondary_comment_primary (primary_comment_id),
    INDEX idx_secondary_comment_postings (postings_id),
    INDEX idx_secondary_comment_user (user_id),
    INDEX idx_secondary_comment_create_time (create_time DESC),
    INDEX idx_secondary_comment_primary_live_ctime (primary_comment_id, deleted, create_time DESC),
    INDEX idx_secondary_comment_postings_live_ctime (postings_id, deleted, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二级评论表';

CREATE TABLE IF NOT EXISTS friend_relation (
    id BIGINT PRIMARY KEY,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    UNIQUE KEY ux_friend_pair (user_a_id, user_b_id),
    CONSTRAINT ck_friend_order CHECK (user_a_id < user_b_id),
    INDEX idx_friend_relation_user_a (user_a_id),
    INDEX idx_friend_relation_user_b (user_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友关系表';

CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    message VARCHAR(200),
    status SMALLINT DEFAULT 0 NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_friend_request_requester (requester_id, status),
    INDEX idx_friend_request_target (target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友请求表';

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY,
    conversation_id VARCHAR(41) NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    read_flag BOOLEAN DEFAULT FALSE,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_chat_conversation (conversation_id, create_time),
    INDEX idx_chat_sender (sender_id),
    INDEX idx_chat_receiver (receiver_id),
    INDEX idx_chat_conversation_ctime_desc (conversation_id, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';

CREATE TABLE IF NOT EXISTS user_like (
    id BIGINT PRIMARY KEY,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    liked BOOLEAN DEFAULT TRUE,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_like_biz (biz_type, biz_id),
    INDEX idx_user_like_user (user_id),
    UNIQUE KEY idx_uq_user_like (biz_type, biz_id, user_id),
    INDEX idx_user_like_user_type_liked (user_id, biz_type, liked, update_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户点赞表';

CREATE TABLE IF NOT EXISTS t_notification (
    id BIGINT PRIMARY KEY,
    sender_id BIGINT,
    recipient_id BIGINT NOT NULL,
    related_id VARCHAR(64),
    post_id BIGINT,
    type SMALLINT NOT NULL,
    content VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    read_flag BOOLEAN DEFAULT FALSE,
    INDEX idx_notify_recipient (recipient_id),
    INDEX idx_notify_type (type),
    INDEX idx_notify_recipient_read (recipient_id, read_flag),
    INDEX idx_notify_recipient_ctime (recipient_id, create_time DESC),
    INDEX idx_notify_recipient_read_ctime (recipient_id, read_flag, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';

CREATE TABLE IF NOT EXISTS report (
    id BIGINT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    reason_type VARCHAR(32) NOT NULL,
    reason_detail TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    handler_id BIGINT,
    handle_result TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    handle_time DATETIME,
    INDEX idx_report_reporter (reporter_id),
    INDEX idx_report_target (target_type, target_id),
    INDEX idx_report_status (status),
    INDEX idx_report_create_time (create_time DESC),
    INDEX idx_report_reporter_ctime (reporter_id, create_time DESC),
    INDEX idx_report_status_ctime (status, create_time DESC),
    INDEX idx_report_dup_pending (reporter_id, target_type, target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报记录表';

CREATE TABLE IF NOT EXISTS user_interest_model (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interest_key VARCHAR(64) NOT NULL,
    weight FLOAT NOT NULL DEFAULT 0,
    event_count INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_interest (user_id, interest_key),
    INDEX idx_user_interest_user (user_id),
    INDEX idx_user_interest_key (interest_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户兴趣模型持久快照';

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value VARCHAR(1024) NOT NULL,
    description VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS admin_operation_log (
    id BIGINT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT,
    detail VARCHAR(1024),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_log_admin (admin_id, create_time DESC),
    INDEX idx_admin_log_target (target_type, target_id),
    INDEX idx_admin_log_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员操作日志';

INSERT INTO system_config (id, config_key, config_value, description, create_time, update_time)
VALUES
  (900000000000000001, 'hot.like.weight', '3.0', '热榜点赞权重', NOW(), NOW()),
  (900000000000000002, 'hot.reply.weight', '5.0', '热榜评论权重', NOW(), NOW()),
  (900000000000000003, 'hot.half_life.hours', '24', '热榜半衰期小时数', NOW(), NOW()),
  (900000000000000004, 'audit.enabled', 'true', '是否开启内容审核', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  description = VALUES(description),
  update_time = NOW();
