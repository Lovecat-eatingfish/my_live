-- ============================================================
-- 视频流推送模块 SQL
-- 真实表名为 t_living_room / t_living_room_record（见 qiyu-live-living.sql）
-- 注意：qiyu-live-living.sql 中遗留的 t_living_room_record 结构是错误的
--       （缺少 room_id/record_url 等字段），本脚本会重建它，执行前请备份数据
-- 幂等性：本脚本可重复执行，已加列会自动跳过
-- ============================================================

-- 1. 直播间表扩字段
SET @dbname = DATABASE();

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'push_url');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN push_url VARCHAR(512) COMMENT ''推流地址'' AFTER good_num',
    'SELECT ''push_url already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'stream_key');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN stream_key VARCHAR(128) COMMENT ''流 Key'' AFTER push_url',
    'SELECT ''stream_key already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'stream_status');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN stream_status TINYINT DEFAULT 0 COMMENT ''0=未开播 1=推流中 2=异常'' AFTER stream_key',
    'SELECT ''stream_status already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'srs_server_id');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN srs_server_id VARCHAR(64) COMMENT ''SRS 实例标识'' AFTER stream_status',
    'SELECT ''srs_server_id already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'stream_start_time');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN stream_start_time DATETIME COMMENT ''本次推流开始时间'' AFTER srs_server_id',
    'SELECT ''stream_start_time already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 't_living_room' AND COLUMN_NAME = 'record_enabled');
SET @ddl = IF(@col = 0,
    'ALTER TABLE `t_living_room` ADD COLUMN record_enabled TINYINT DEFAULT 0 COMMENT ''是否开启录制 0=关闭 1=开启'' AFTER stream_start_time',
    'SELECT ''record_enabled already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 重建录制记录表（对齐 LivingRoomRecordPO 字段）
DROP TABLE IF EXISTS `t_living_room_record`;
CREATE TABLE `qiyu_living_room_record` (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    room_id      INT          NOT NULL COMMENT '直播间ID',
    anchor_id    BIGINT       NOT NULL COMMENT '主播ID',
    record_url   VARCHAR(512) NOT NULL COMMENT '回放地址 (MinIO URL)',
    duration     INT          NOT NULL COMMENT '时长(秒)',
    file_size    BIGINT       NULL COMMENT '文件大小(字节)',
    start_time   DATETIME     NOT NULL COMMENT '录制开始时间',
    end_time     DATETIME     NOT NULL COMMENT '录制结束时间',
    status       TINYINT DEFAULT 1 COMMENT '1=生成中 2=可用 3=失败',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_room_id  (room_id),
    INDEX idx_anchor_id (anchor_id),
    INDEX idx_status   (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT '直播间录制记录';
