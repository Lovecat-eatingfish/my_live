-- 视频观看历史：重复观看只刷时间（唯一键）
CREATE TABLE IF NOT EXISTS `t_video_watch_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '观众id',
  `video_id` bigint unsigned NOT NULL COMMENT '视频id',
  `watch_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近观看时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
  KEY `idx_user_time` (`user_id`, `watch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频观看历史';
