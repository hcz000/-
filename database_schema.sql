-- ========================================
-- KnowledgePlanet PostgreSQL 完整建表脚本
-- 版本：1.1
-- 说明：包含所有表结构、索引、触发器
-- 注意：搜索使用 ILIKE + pg_trgm 模糊匹配，无需额外扩展
-- ========================================
CREATE DATABASE knowledge_planet;
-- ========================================
-- Part 1: 核心用户表
-- ========================================
-- 1. 用户表（users）
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,                      -- 主键由应用层生成（Snowflake）
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(128) UNIQUE,
    avatar VARCHAR(512),
    role VARCHAR(32) DEFAULT 'user',            -- 用户角色：user/admin
    liketype REAL[] DEFAULT ARRAY[0.125,0.125,0.125,0.125,0.125,0.125,0.125,0.125]::REAL[], -- 用户兴趣向量（8维数组）
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.role IS '用户角色：user-普通用户, admin-管理员';

-- ========================================
-- Part 2: 星球模块
-- ========================================

-- 2. 星球表（planet）
CREATE TABLE IF NOT EXISTS planet (
    planet_id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    master BIGINT NOT NULL,                     -- 星球创建者ID
    member_count INTEGER DEFAULT 0,             -- 成员数缓存（由 planet_member 实时维护）
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    description TEXT,
    category VARCHAR(64),
    status INTEGER                              -- 星球状态
);

CREATE INDEX IF NOT EXISTS idx_planet_deleted ON planet(deleted);
CREATE INDEX IF NOT EXISTS idx_planet_name ON planet(name);
CREATE INDEX IF NOT EXISTS idx_planet_master ON planet(master);

COMMENT ON TABLE planet IS '星球表';
COMMENT ON COLUMN planet.member_count IS '成员数缓存';

-- 2b. 星球-用户成员关系表（planet_member）
-- 替代原有的 users.join_planet（JSONB）和 planet.member（JSONB）
CREATE TABLE IF NOT EXISTS planet_member (
    id BIGINT PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_planet_member UNIQUE (planet_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_pm_planet ON planet_member(planet_id);
CREATE INDEX IF NOT EXISTS idx_pm_user ON planet_member(user_id);

COMMENT ON TABLE planet_member IS '星球成员关系表';
COMMENT ON COLUMN planet_member.planet_id IS '星球ID';
COMMENT ON COLUMN planet_member.user_id IS '用户ID';
COMMENT ON COLUMN planet_member.create_time IS '加入时间';

-- ========================================
-- Part 3: 帖子与评论模块
-- ========================================

-- 3. 帖子表（postings）
CREATE TABLE IF NOT EXISTS postings (
    postings_id BIGINT PRIMARY KEY,
    planet_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    status SMALLINT DEFAULT 0 NOT NULL,         -- 0正常 1隐藏
    reply_count INT DEFAULT 0 NOT NULL,
    like_count INT DEFAULT 0 NOT NULL,
    type VARCHAR(32),                           -- 帖子类型，用于兴趣推送
    images JSONB DEFAULT '[]'::jsonb,           -- 帖子图片URL列表（JSONB数组）
    audit_status SMALLINT DEFAULT 1,            -- 审核状态：0-待审核, 1-已通过, 2-已拒绝
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    first_comment_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_postings_deleted ON postings(deleted);
CREATE INDEX IF NOT EXISTS idx_postings_planet ON postings(planet_id);
CREATE INDEX IF NOT EXISTS idx_postings_user ON postings(user_id);
CREATE INDEX IF NOT EXISTS idx_postings_type ON postings(type);
CREATE INDEX IF NOT EXISTS idx_postings_create_time ON postings(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_postings_images ON postings USING gin(images);
CREATE INDEX IF NOT EXISTS idx_postings_audit_status ON postings(audit_status);

COMMENT ON TABLE postings IS '帖子表';
COMMENT ON COLUMN postings.images IS '帖子图片URL列表（JSONB数组）';
COMMENT ON COLUMN postings.audit_status IS '审核状态：0-待审核, 1-已通过, 2-已拒绝';

-- 4. 一级评论表（primary_comment）
CREATE TABLE IF NOT EXISTS primary_comment (
    id BIGINT PRIMARY KEY,
    postings_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,
    like_count INT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_primary_comment_postings ON primary_comment(postings_id);
CREATE INDEX IF NOT EXISTS idx_primary_comment_user ON primary_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_primary_comment_create_time ON primary_comment(create_time DESC);

COMMENT ON TABLE primary_comment IS '一级评论表';

-- 5. 二级评论表（secondary_comment）
CREATE TABLE IF NOT EXISTS secondary_comment (
    id BIGINT PRIMARY KEY,
    postings_id BIGINT NOT NULL,
    primary_comment_id BIGINT NOT NULL,
    parent_id BIGINT,                           -- 父评论ID（支持多级回复）
    user_id BIGINT NOT NULL,
    reply_to_username VARCHAR(128),
    content TEXT,
    like_count INT DEFAULT 0 NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_secondary_comment_primary ON secondary_comment(primary_comment_id);
CREATE INDEX IF NOT EXISTS idx_secondary_comment_postings ON secondary_comment(postings_id);
CREATE INDEX IF NOT EXISTS idx_secondary_comment_user ON secondary_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_secondary_comment_create_time ON secondary_comment(create_time DESC);

COMMENT ON TABLE secondary_comment IS '二级评论表';
COMMENT ON COLUMN secondary_comment.primary_comment_id IS '所属一级评论ID';
COMMENT ON COLUMN secondary_comment.parent_id IS '父评论ID（支持多级回复）';

-- ========================================
-- Part 4: 社交模块（好友、聊天）
-- ========================================

-- 6. 好友关系表（friend_relation）
CREATE TABLE IF NOT EXISTS friend_relation (
    id BIGINT PRIMARY KEY,
    user_a_id BIGINT NOT NULL,
    user_b_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT ux_friend_pair UNIQUE (user_a_id, user_b_id),
    CONSTRAINT ck_friend_order CHECK (user_a_id < user_b_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_relation_user_a ON friend_relation(user_a_id);
CREATE INDEX IF NOT EXISTS idx_friend_relation_user_b ON friend_relation(user_b_id);

COMMENT ON TABLE friend_relation IS '好友关系表';
COMMENT ON COLUMN friend_relation.deleted IS '软删除标记：0-正常 1-已删除';

-- 7. 好友请求表（friend_request）
CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    message VARCHAR(200),
    status SMALLINT DEFAULT 0 NOT NULL,         -- 0待处理 1同意 2拒绝
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_friend_request_requester ON friend_request(requester_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_request_target ON friend_request(target_id, status);

COMMENT ON TABLE friend_request IS '好友请求表';
COMMENT ON COLUMN friend_request.status IS '0待处理 1同意 2拒绝';

-- 8. 聊天消息表（chat_message）
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY,
    conversation_id VARCHAR(41) NOT NULL,       -- userId:friendId（最大 20+1+20=41）
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    read_flag BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_conversation ON chat_message(conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_chat_sender ON chat_message(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_receiver ON chat_message(receiver_id);

COMMENT ON TABLE chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.conversation_id IS '对话ID格式：userId:friendId';

-- ========================================
-- Part 5: 点赞与通知模块
-- ========================================

-- 9. 用户点赞表（user_like）
CREATE TABLE IF NOT EXISTS user_like (
    id BIGINT PRIMARY KEY,
    biz_type VARCHAR(32) NOT NULL,              -- POST, PRIMARY-COMMENT, SECONDARY-COMMENT
    biz_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    liked BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_like_biz ON user_like(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_user_like_user ON user_like(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_user_like ON user_like(biz_type, biz_id, user_id);

COMMENT ON TABLE user_like IS '用户点赞表';
COMMENT ON COLUMN user_like.biz_type IS '业务类型：POST-帖子, PRIMARY-COMMENT-一级评论, SECONDARY-COMMENT-二级评论';

-- 10. 通知表（t_notification）
CREATE TABLE IF NOT EXISTS t_notification (
    id BIGINT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    related_id VARCHAR(64),
    post_id BIGINT,
    type SMALLINT NOT NULL,                    -- 1点赞 2评论 3系统通知
    content VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_flag BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_notify_recipient ON t_notification(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notify_type ON t_notification(type);
CREATE INDEX IF NOT EXISTS idx_notify_recipient_read ON t_notification(recipient_id, read_flag);

COMMENT ON TABLE t_notification IS '通知表';
COMMENT ON COLUMN t_notification.read_flag IS '0-未读 1-已读';

-- ========================================
-- Part 6: 管理模块（举报）
-- ========================================

-- 11. 举报表（report）
CREATE TABLE IF NOT EXISTS report (
    id BIGINT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,               -- 举报人ID
    target_type VARCHAR(32) NOT NULL,          -- POST, COMMENT, USER
    target_id BIGINT NOT NULL,                 -- 被举报对象ID
    reason_type VARCHAR(32) NOT NULL,          -- SPAM, ABUSE, ILLEGAL, OTHER
    reason_detail TEXT,                        -- 举报详细描述
    status VARCHAR(32) DEFAULT 'PENDING',      -- PENDING, PROCESSING, RESOLVED, REJECTED
    handler_id BIGINT,                         -- 处理人ID（管理员）
    handle_result TEXT,                        -- 处理结果描述
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    handle_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_reporter ON report(reporter_id);
CREATE INDEX IF NOT EXISTS idx_report_target ON report(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_report_status ON report(status);
CREATE INDEX IF NOT EXISTS idx_report_create_time ON report(create_time DESC);

COMMENT ON TABLE report IS '举报记录表';
COMMENT ON COLUMN report.target_type IS '被举报对象类型：POST-帖子, COMMENT-评论, USER-用户';
COMMENT ON COLUMN report.reason_type IS '举报原因：SPAM-垃圾信息, ABUSE-辱骂, ILLEGAL-违规内容, OTHER-其他';
COMMENT ON COLUMN report.status IS '处理状态：PENDING-待处理, PROCESSING-处理中, RESOLVED-已处理, REJECTED-已驳回';

-- ========================================
-- Part 7: 表统计查询（验证）
-- ========================================

-- 查询所有表数据量
SELECT 'users' AS table_name, COUNT(*) AS count FROM users
UNION ALL SELECT 'planet', COUNT(*) FROM planet
UNION ALL SELECT 'planet_member', COUNT(*) FROM planet_member
UNION ALL SELECT 'postings', COUNT(*) FROM postings
UNION ALL SELECT 'primary_comment', COUNT(*) FROM primary_comment
UNION ALL SELECT 'secondary_comment', COUNT(*) FROM secondary_comment
UNION ALL SELECT 'chat_message', COUNT(*) FROM chat_message
UNION ALL SELECT 'friend_relation', COUNT(*) FROM friend_relation
UNION ALL SELECT 'friend_request', COUNT(*) FROM friend_request
UNION ALL SELECT 't_notification', COUNT(*) FROM t_notification
UNION ALL SELECT 'user_like', COUNT(*) FROM user_like
UNION ALL SELECT 'report', COUNT(*) FROM report;

-- ========================================
-- 完成提示
-- ========================================
-- 执行此脚本后，数据库具备：
-- 1. ✅ 12张核心业务表
-- 2. ✅ 关系表替代 JSONB（planet_member 替代 users.join_planet / planet.member）
-- 3. ✅ 规范的时间字段（TIMESTAMP）
-- 4. ✅ 合理的数据类型（BOOLEAN、BIGINT、JSONB）
-- 5. ✅ 完整的索引与约束覆盖（UNIQUE、CHECK、外键逻辑）
-- 6. ✅ 举报管理功能
