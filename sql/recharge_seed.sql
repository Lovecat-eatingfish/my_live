-- 充值中心数据修复与档位种子数据（2026-09-12 迭代）
-- 背景：t_pay_product.extra 为空导致入账 NPE、type=1 不被 QIYU_COIN(0) 认领、t_pay_topic 为空导致 payNotify 直接失败
USE qiyu_live_bank;

-- 1. 重整金币充值档位（price 单位分，extra.coin 为到账金币数；type 必须=0 即 QIYU_COIN）
UPDATE t_pay_product SET name='600金币',   price=600,   extra='{"coin":600}',   type=0, valid_status=1 WHERE id=1;
UPDATE t_pay_product SET name='1000金币', price=1000,  extra='{"coin":1000}',  type=0, valid_status=1 WHERE id=2;
UPDATE t_pay_product SET name='3080金币', price=3000,  extra='{"coin":3080}',  type=0, valid_status=1 WHERE id=3;
INSERT INTO t_pay_product(name, price, extra, type, valid_status) VALUES
('10800金币', 9800,  '{"coin":10800}', 0, 1),
('22800金币', 19800, '{"coin":22800}', 0, 1),
('38800金币', 32800, '{"coin":38800}', 0, 1);

-- 2. 支付回调主题配置（bizCode 10001 与 api 层 payProduct 发起的回调一致）
INSERT INTO t_pay_topic(topic, status, biz_code, remark)
SELECT 'qiyu-pay-notify-topic', 1, 10001, '支付成功回调通知主题'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_pay_topic WHERE biz_code=10001);
