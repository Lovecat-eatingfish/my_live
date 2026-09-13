-- 批次四：视频转码 + 完播上报
USE qiyu_live_video;

-- 1. 转码状态列（0处理中 1完成 2失败；NULL/2 回退播原 video_url）
ALTER TABLE `t_video_info`
  ADD COLUMN `transcode_status` tinyint NOT NULL DEFAULT 2 COMMENT '转码状态（0处理中 1完成 2失败/未转码）' AFTER `status`;

-- 存量数据视为"未转码"，列表可见（回退播原文件）
-- (默认值 2 已覆盖存量行)

-- 2. 播放完播上报日志
CREATE TABLE IF NOT EXISTS `t_video_play_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL COMMENT '视频id',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '观看人（未登录为空）',
  `watched_seconds` int NOT NULL DEFAULT 0 COMMENT '观看秒数',
  `duration` int NOT NULL DEFAULT 0 COMMENT '视频时长（秒）',
  `is_complete` tinyint NOT NULL DEFAULT 0 COMMENT '是否完播（观看≥90%）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_video` (`video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频播放完播上报';
