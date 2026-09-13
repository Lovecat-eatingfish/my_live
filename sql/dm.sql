-- 批次十：私信 DM（表建在 qiyu-live-msg 库，msg-provider 独占读写，api 经 IDmRpc 查询）
USE `qiyu-live-msg`;

CREATE TABLE IF NOT EXISTS `t_user_dm_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_uid` bigint NOT NULL,
  `to_uid` bigint NOT NULL,
  `content` varchar(500) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0撤回/删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_from_to_id` (`from_uid`,`to_uid`,`id`),
  KEY `idx_to_from_id` (`to_uid`,`from_uid`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';

CREATE TABLE IF NOT EXISTS `t_user_dm_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_uid` bigint NOT NULL,
  `peer_uid` bigint NOT NULL,
  `last_msg` varchar(500) NOT NULL DEFAULT '',
  `unread_cnt` int NOT NULL DEFAULT '0',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_peer` (`owner_uid`,`peer_uid`),
  KEY `idx_owner_update` (`owner_uid`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信会话表（双方各一行）';
