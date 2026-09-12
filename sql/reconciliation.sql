-- 对账差错表：T+1 定时对账（充值订单 vs 金币流水）产生的差错记录
CREATE TABLE IF NOT EXISTS `t_reconciliation_detail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `biz_date` date NOT NULL COMMENT '对账业务日期（核对该日数据）',
  `diff_type` tinyint NOT NULL COMMENT '差错类型（1订单有流水无 2流水有订单无 3金额不平）',
  `order_id` varchar(60) DEFAULT NULL COMMENT '关联订单id（可空）',
  `user_id` bigint unsigned DEFAULT NULL COMMENT '用户id',
  `product_id` int unsigned DEFAULT NULL COMMENT '产品id',
  `expect_num` int DEFAULT NULL COMMENT '订单侧应收金币',
  `actual_num` int DEFAULT NULL COMMENT '流水侧实收金币',
  `status` tinyint DEFAULT '0' COMMENT '处理状态（0未处理 1已处理）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz_date` (`biz_date`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差错明细表';
