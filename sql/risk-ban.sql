USE `qiyu-live-msg`;
-- 批次一（风控）建表脚本 2026-09-13
-- 1) 敏感词表：建在 msg-provider 的库（qiyu-live-msg），由 msg-provider 独占读写，admin-api 经 IRiskRpc 管理
--    level: 1拦截 2替换* 3仅记录；scene: 0全部 1弹幕 2昵称 3视频标题 4评论 5房间名
CREATE TABLE IF NOT EXISTS `risk_sensitive_word` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `word` varchar(64) NOT NULL COMMENT '敏感词',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '1拦截 2替换* 3仅记录',
  `scene` tinyint NOT NULL DEFAULT '0' COMMENT '0全部 1弹幕 2昵称 3视频标题 4评论 5房间名',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1有效 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word_scene` (`word`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词库';

-- 种子词（演示用，运营可在后台增删）
INSERT IGNORE INTO `risk_sensitive_word` (`word`, `level`, `scene`) VALUES
  ('赌博', 1, 0),
  ('博彩', 1, 0),
  ('外挂', 1, 0),
  ('代练', 2, 0),
  ('加微信', 2, 1),
  ('刷单', 1, 0),
  ('诈骗', 1, 0),
  ('垃圾主播', 2, 1),
  ('敏感测试词', 2, 0),
  ('拦截测试词', 1, 0);

-- 2) 账号封禁表：建在 qiyu_live_user（运行时未分表，SINGLE 规则覆盖）
--    type: 1禁言 2封号；status: 1生效中 0已解除
USE `qiyu_live_user`;
CREATE TABLE IF NOT EXISTS `t_user_ban` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `type` tinyint NOT NULL COMMENT '1禁言 2封号',
  `reason` varchar(200) DEFAULT '' COMMENT '原因',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime DEFAULT NULL COMMENT 'NULL=永久',
  `operator` varchar(60) NOT NULL DEFAULT 'admin' COMMENT '操作人',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1生效中 0已解除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号封禁/禁言记录';
