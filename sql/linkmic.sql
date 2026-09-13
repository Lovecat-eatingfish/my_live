-- 批次七：连麦
USE qiyu_live_living;

CREATE TABLE IF NOT EXISTS `t_living_linkmic` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `room_id` int NOT NULL COMMENT '直播间id',
  `guest_user_id` bigint NOT NULL COMMENT '被连麦观众',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0邀请中 1连麦中 2已结束',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_status` (`room_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间连麦记录';
