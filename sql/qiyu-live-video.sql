-- 视频模块建库建表（2026-09-12 视频微服务迭代）
CREATE DATABASE IF NOT EXISTS `qiyu_live_video` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `qiyu_live_video`;

-- 视频信息表
CREATE TABLE IF NOT EXISTS `t_video_info` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '视频id',
  `user_id` bigint unsigned NOT NULL COMMENT '作者用户id',
  `title` varchar(80) NOT NULL DEFAULT '' COMMENT '标题',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '简介',
  `video_url` varchar(500) NOT NULL COMMENT '视频播放地址（MinIO 公开 URL）',
  `cover_url` varchar(500) NOT NULL DEFAULT '' COMMENT '封面地址',
  `tag_id` int unsigned NOT NULL DEFAULT 0 COMMENT '标签id（0=无标签/其他）',
  `duration` int NOT NULL DEFAULT 0 COMMENT '时长（秒）',
  `size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `play_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT '播放量',
  `like_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT '点赞数',
  `favorite_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT '收藏数',
  `share_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT '分享数',
  `comment_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT '评论数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（0下架 1上线）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tag_status` (`tag_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频信息表';

-- 视频标签字典
CREATE TABLE IF NOT EXISTS `t_video_tag` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(30) NOT NULL COMMENT '标签名',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（0禁用 1启用）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频标签字典';

-- 用户行为表（点赞/收藏，一人一视频一条）
CREATE TABLE IF NOT EXISTS `t_video_user_action` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `video_id` bigint unsigned NOT NULL,
  `action_type` tinyint NOT NULL COMMENT '1=点赞 2=收藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_video_action` (`user_id`, `video_id`, `action_type`),
  KEY `idx_video_action` (`video_id`, `action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频用户行为表';

-- 评论表
CREATE TABLE IF NOT EXISTS `t_video_comment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `video_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `content` varchar(300) NOT NULL COMMENT '评论内容',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（0删除 1正常）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_video_status` (`video_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频评论表';

-- 种子标签
INSERT INTO t_video_tag(tag_name, sort, status) VALUES
('全部', 0, 1), ('游戏', 1, 1), ('音乐', 2, 1), ('生活', 3, 1),
('知识', 4, 1), ('美食', 5, 1), ('影视', 6, 1), ('搞笑', 7, 1)
ON DUPLICATE KEY UPDATE sort=VALUES(sort);
