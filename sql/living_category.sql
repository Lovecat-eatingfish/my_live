-- 批次十一：直播分区体系（qiyu_live_living 库）
-- id 即房间 type 值；初始 4 行对齐存量类型 娱乐1/游戏2/赛事3/带货4
USE `qiyu_live_living`;

CREATE TABLE IF NOT EXISTS `t_living_category` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(20) NOT NULL,
  `icon` varchar(200) NOT NULL DEFAULT '' COMMENT '分区图标URL/emoji',
  `sort` int NOT NULL DEFAULT '0' COMMENT '越小越靠前',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播分区（运营可配置）';

INSERT IGNORE INTO `t_living_category` (`id`, `name`, `icon`, `sort`) VALUES
(1, '娱乐', '🎭', 1),
(2, '游戏', '🎮', 2),
(3, '赛事', '🏆', 3),
(4, '带货', '🛒', 4);
