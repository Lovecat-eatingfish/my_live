-- 批次五：admin 补全（截帧审阅）
USE qiyu_live_common;

-- 直播间巡查截帧（stream-provider 定时写入，admin-api 读取处置）
CREATE TABLE IF NOT EXISTS `risk_room_snapshot` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `room_id` int NOT NULL COMMENT '直播间id',
  `anchor_id` bigint NOT NULL COMMENT '主播id',
  `img_url` varchar(500) NOT NULL COMMENT '截帧图片地址（MinIO）',
  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1正常 2异常已处置',
  `handle_action` varchar(20) DEFAULT NULL COMMENT '处置动作 warn/close/ban',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_time` (`room_id`, `create_time`),
  KEY `idx_status` (`audit_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间巡查截帧';
