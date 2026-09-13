-- 批次十四：直播间公告 + 房间管理员
USE `qiyu_live_living`;

-- 公告（主播随时可改）
ALTER TABLE `t_living_room`
  ADD COLUMN `announcement` varchar(200) NOT NULL DEFAULT '' COMMENT '直播间公告';

-- 房间管理员（主播任命，可禁言观众）
CREATE TABLE IF NOT EXISTS `t_living_room_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` int NOT NULL,
  `admin_user_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_user` (`room_id`,`admin_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间管理员';
