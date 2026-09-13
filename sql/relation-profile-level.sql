-- 批次二：关系链 + 个人主页扩展 + 站内通知
-- 库：qiyu_live_user（运行时为普通单库，未启用分表，与 t_user_ban 同策略）
USE qiyu_live_user;

-- 1. 关注关系表
CREATE TABLE IF NOT EXISTS t_user_relation (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '关注发起人',
  follow_user_id BIGINT NOT NULL COMMENT '被关注人',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1关注中 0已取关',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_follow (user_id, follow_user_id),
  KEY idx_follow_user (follow_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

-- 2. 用户主页扩展（计数 + 等级经验）
CREATE TABLE IF NOT EXISTS t_user_profile_ext (
  user_id BIGINT PRIMARY KEY,
  follow_cnt INT NOT NULL DEFAULT 0 COMMENT '关注数',
  fans_cnt INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  like_received_cnt INT NOT NULL DEFAULT 0 COMMENT '获赞数(预留)',
  level INT NOT NULL DEFAULT 1 COMMENT '等级 1~12',
  exp BIGINT NOT NULL DEFAULT 0 COMMENT '累计经验',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主页扩展表';

-- 3. 站内通知表（本批只写不读，通知中心 UI/接口批次三做）
CREATE TABLE IF NOT EXISTS t_user_notify (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '接收人',
  type TINYINT NOT NULL DEFAULT 1 COMMENT '1系统 2互动 3私信 4开播通知',
  title VARCHAR(64) NOT NULL DEFAULT '',
  content VARCHAR(255) NOT NULL DEFAULT '',
  jump_url VARCHAR(255) DEFAULT NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';
