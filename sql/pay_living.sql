-- 批次八：付费直播间（门票）
USE qiyu_live_living;

ALTER TABLE `t_living_room`
  ADD COLUMN `pay_type` tinyint NOT NULL DEFAULT 0 COMMENT '付费类型（0免费 1门票）',
  ADD COLUMN `ticket_price` int NOT NULL DEFAULT 0 COMMENT '门票价格（金币）';
