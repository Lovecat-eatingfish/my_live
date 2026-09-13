# 旗鱼直播平台 数据库表设计

## 一、模块概览

| 模块 | 表 | 说明 |
|------|-----|------|
| 用户 | t_user, t_user_phone, t_user_tag | 用户基础信息 |
| 账户 | t_qiyu_currency_account, t_qiyu_currency_trade, t_pay_order, t_pay_product, t_pay_topic | 支付、账户、订单 |
| 礼物 | t_gift_config, t_gift_record | 礼物配置与记录 |
| 直播间 | t_living_room, t_living_room_record | 直播间信息与历史 |
| 消息 | t_sms | 短信验证码 |
| ID生成 | t_id_generate_config | 分布式ID配置 |
| 电商(预留) | t_category_info, t_sku_info, t_sku_order_info, t_sku_stock_info | 商品、类目、库存 |
| 电商(预留) | t_anchor_shop_info, t_red_packet_config | 主播带货、红包雨 |

---

## 二、ER 关系图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                    t_user                                       │
│  (user_id PK, nick_name, avatar, sex, born_date, work_city, born_city)        │
└──────────────────────┬──────────────────────────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬──────────────┬──────────────┐
        │              │              │              │              │
        ▼              ▼              ▼              ▼              ▼
┌───────────────┐ ┌───────────────┐ ┌─────────────────┐ ┌─────────────────────┐
│ t_user_phone  │ │  t_user_tag   │ │t_qiyu_currency  │ │  t_pay_order        │
│(id PK, phone) │ │ (user_id PK)  │ │   _account      │ │(order_id UK, user)  │
│               │ │               │ │  (user_id PK)    │ │                     │
└───────────────┘ └───────────────┘ └─────────────────┘ └─────────┬───────────┘
                                                                    │
                                                            ┌───────▼────────┐
                                                            │ t_pay_product  │
                                                            │ (id PK)        │
                                                            └────────┬───────┘
                                                                    │
┌──────────────────────────────────────────────────────────────────┼───────────────┐
│                                                                  │               │
▼                                                                  ▼               ▼
┌───────────────────┐                                    ┌─────────────────┐ ┌───────────────────┐
│ t_living_room     │                                    │t_qiyu_currency  │ │   t_gift_record   │
│(anchor_id→user)   │                                    │  _trade         │ │ (user_id→user)    │
└───────────────────┘                                    │ (user_id→user)  │ │ (gift_id→gift)    │
                                                       └─────────────────┘ └───────────────────┘
        │                                                                          │
        │                                                                          ▼
        │                                                          ┌───────────────────┐
        │                                                          │  t_gift_config    │
        │                                                          │  (gift_id PK)     │
        │                                                          └───────────────────┘
        │
        ▼
┌───────────────────────────┐
│ t_living_room_record      │
│ (anchor_id→user)          │
└───────────────────────────┘
```

---

## 三、详细表结构

### 3.1 用户模块

#### t_user - 用户基础信息表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| user_id | bigint | PK | 用户id |
| nick_name | varchar(35) | | 昵称 |
| avatar | varchar(255) | | 头像 |
| true_name | varchar(20) | | 真实姓名 |
| sex | tinyint(1) | | 性别 0男，1女 |
| born_date | datetime | | 出生时间 |
| work_city | int | | 工作地 |
| born_city | int | | 出生地 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_user_phone - 用户手机表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| phone | varchar(200) | UNIQUE | 手机号 |
| user_id | bigint | FK→t_user.user_id | 用户id |
| status | tinyint | | 状态(0无效，1有效) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_user_tag - 用户标签记录表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| user_id | bigint | PK FK→t_user.user_id | 用户id |
| tag_info_01 | bigint | | 标签记录字段1 |
| tag_info_02 | bigint | | 标签记录字段2 |
| tag_info_03 | bigint | | 标签记录字段3 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

---

### 3.2 账户/支付模块

#### t_qiyu_currency_account - 账户余额表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| user_id | bigint unsigned | PK FK→t_user.user_id | 用户id |
| current_balance | int | | 当前余额(分) |
| total_charged | int | | 累计充值(分) |
| status | tinyint | | 账户状态(0无效 1有效 2冻结) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_qiyu_currency_trade - 流水记录表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| user_id | bigint | FK→t_user.user_id | 用户id |
| num | int | | 流水金额(分) |
| type | tinyint | | 流水类型 |
| status | tinyint | | 状态(0无效 1有效) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_pay_order - 支付订单表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| order_id | varchar(60) | UNIQUE | 订单id |
| product_id | int unsigned | FK→t_pay_product.id | 产品id |
| status | tinyint unsigned | | 状态(0待支付 1支付中 2已支付 3撤销 4无效) |
| user_id | bigint unsigned | FK→t_user.user_id | 用户id |
| pay_channel | tinyint unsigned | | 支付渠道(0支付宝 1微信 2银联 3收银台) |
| source | tinyint unsigned | | 来源 |
| pay_time | datetime | | 支付成功时间 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_pay_product - 付费产品表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| name | varchar(60) | | 产品名称 |
| price | int | | 产品价格(分) |
| extra | varchar(500) | | 扩展字段 |
| type | tinyint | | 类型(0旗鱼直播间产品) |
| valid_status | tinyint | | 状态(0无效 1有效) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_pay_topic - 支付主题配置表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| topic | varchar(200) | | MQ主题 |
| status | tinyint(1) | | 是否有效 |
| biz_code | int | | 业务code |
| remark | varchar(200) | | 描述 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

---

### 3.3 礼物模块

#### t_gift_config - 礼物配置表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| gift_id | int unsigned | PK AUTO_INCREMENT | 礼物id |
| price | int unsigned | | 虚拟货币价格 |
| gift_name | varchar(100) | | 礼物名称 |
| status | tinyint unsigned | | 状态(0无效 1有效) |
| cover_img_url | varchar(500) | | 礼物封面地址 |
| svga_url | varchar(500) | | SVGA资源地址 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_gift_record - 送礼记录表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| user_id | bigint | FK→t_user.user_id | 发送人 |
| object_id | bigint | | 收礼人 |
| gift_id | int | FK→t_gift_config.gift_id | 礼物id |
| price | int | | 送礼金额 |
| price_unit | tinyint | | 金额单位 |
| source | tinyint | | 礼物来源 |
| send_time | datetime | | 发送时间 |

---

### 3.4 直播间模块

#### t_living_room - 直播间表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 直播间id |
| anchor_id | bigint | FK→t_user.user_id | 主播id |
| type | tinyint | | 直播间类型(1普通 2pk) |
| status | tinyint | | 状态(0无效 1有效) |
| room_name | varchar(60) | | 直播间名称 |
| covert_img | varchar(255) | | 直播间封面 |
| watch_num | int | | 观看数量 |
| good_num | int | | 点赞数量 |
| start_time | datetime | | 开播时间 |
| end_time | datetime | | 关播时间 |
| update_time | datetime | | 更新时间 |

#### t_living_room_record - 直播间记录表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| anchor_id | bigint | FK→t_user.user_id | 主播id |
| type | tinyint | | 直播间类型 |
| status | tinyint | | 状态 |
| room_name | varchar(60) | | 直播间名称 |
| covert_img | varchar(255) | | 直播间封面 |
| watch_num | int | | 观看数量 |
| good_num | int | | 点赞数量 |
| start_time | datetime | | 开播时间 |
| end_time | datetime | | 关播时间 |
| update_time | datetime | | 更新时间 |

---

### 3.5 消息模块

#### t_sms - 短信发送记录表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | bigint unsigned | PK AUTO_INCREMENT | 主键id |
| code | int unsigned | | 验证码 |
| phone | varchar(200) | | 手机号 |
| send_time | datetime | | 发送时间 |
| update_time | datetime | | 更新时间 |

---

### 3.6 ID生成模块

#### t_id_generate_config - ID生成配置表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int | PK AUTO_INCREMENT | 主键id |
| remark | varchar(255) | | 描述 |
| next_threshold | bigint | | 当前id所在阶段的阈值 |
| init_num | bigint | | 初始化值 |
| current_start | bigint | | 当前id所在阶段的开始值 |
| step | int | | id递增区间 |
| is_seq | tinyint | | 是否有序(0无序 1有序) |
| id_prefix | varchar(60) | | 业务前缀码 |
| version | int | | 乐观锁版本号 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

---

### 3.7 电商模块（预留）

#### t_category_info - 类目表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| level | int unsigned | | 类目级别 |
| parent_id | int unsigned | FK(self) | 父类目id |
| category_name | varchar(250) | | 类目名称 |
| status | tinyint unsigned | | 状态(0无效 1有效) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_sku_info - 商品SKU信息表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| sku_id | int unsigned | | SKU ID |
| sku_price | int unsigned | | SKU价格(分) |
| sku_code | varchar(250) | | SKU编码 |
| name | varchar(250) | | 商品名称 |
| icon_url | varchar(500) | | 缩略图 |
| original_icon_url | varchar(500) | | 原图 |
| remark | varchar(500) | | 商品描述 |
| status | tinyint unsigned | | 状态(0下架 1上架) |
| category_id | int | FK→t_category_info.id | 类目id |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_sku_order_info - 商品订单表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| sku_id_list | varchar(500) | | SKU ID列表(JSON) |
| user_id | bigint unsigned | FK→t_user.user_id | 用户id |
| room_id | int unsigned | | 直播id |
| status | int unsigned | | 状态 |
| extra | varchar(250) | | 备注 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_sku_stock_info - SKU库存表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| sku_id | int unsigned | FK→t_sku_info.sku_id | SKU ID |
| stock_num | int unsigned | | SKU库存 |
| status | tinyint unsigned | | 状态(0无效 1有效) |
| version | int unsigned | | 乐观锁版本号 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_anchor_shop_info - 带货主播权限配置表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| anchor_id | int unsigned | FK→t_user.user_id | 主播id |
| sku_id | int unsigned | FK→t_sku_info.sku_id | 商品SKU ID |
| status | tinyint unsigned | | 有效(0无效 1有效) |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

#### t_red_packet_config - 直播间红包雨配置表
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | int unsigned | PK AUTO_INCREMENT | 主键id |
| anchor_id | int | FK→t_user.user_id | 主播id |
| start_time | datetime | | 红包雨活动开始时间 |
| total_get | int | | 已领取数量 |
| total_get_price | int | | 已领取金额(分) |
| max_get_price | int | | 最大领取金额(分) |
| status | tinyint | | 状态(1待准备 2已准备 3已发送) |
| total_price | int | | 红包雨总金额(分) |
| total_count | int unsigned | | 红包雨总红包数 |
| config_code | varchar(250) | | 唯一code |
| remark | varchar(250) | | 备注 |
| create_time | datetime | | 创建时间 |
| update_time | datetime | | 更新时间 |

---

## 四、表关系总结

### 4.1 核心外键关系

| 从表 | 字段 | 主表 | 字段 | 关系 |
|------|------|------|------|------|
| t_user_phone | user_id | t_user | user_id | N:1 |
| t_user_tag | user_id | t_user | user_id | 1:1 |
| t_qiyu_currency_account | user_id | t_user | user_id | 1:1 |
| t_qiyu_currency_trade | user_id | t_user | user_id | N:1 |
| t_pay_order | user_id | t_user | user_id | N:1 |
| t_pay_order | product_id | t_pay_product | id | N:1 |
| t_gift_record | user_id | t_user | user_id | N:1 |
| t_gift_record | gift_id | t_gift_config | gift_id | N:1 |
| t_living_room | anchor_id | t_user | user_id | N:1 |
| t_living_room_record | anchor_id | t_user | user_id | N:1 |
| t_sku_info | category_id | t_category_info | id | N:1 |
| t_sku_order_info | user_id | t_user | user_id | N:1 |
| t_sku_stock_info | sku_id | t_sku_info | sku_id | N:1 |
| t_anchor_shop_info | anchor_id | t_user | user_id | N:1 |
| t_anchor_shop_info | sku_id | t_sku_info | sku_id | N:1 |
| t_category_info | parent_id | t_category_info | id | 1:N(self) |

---

## 五、设计问题分析

### 5.1 需要关注的问题

#### 问题1：t_anchor_shop_info 的 sku_id 字段类型不一致
- **现状**：`anchor_id int unsigned` 和 `sku_id int unsigned`
- **建议**：anchor_id 应改为 `bigint unsigned` 与 `t_user.user_id` 类型保持一致

#### 问题2：t_living_room.anchor_id 类型不一致
- **现状**：`anchor_id bigint`（有符号）
- **建议**：改为 `anchor_id bigint unsigned` 与 `t_user.user_id` 保持一致

#### 问题3：t_sku_order_info.sku_id_list 设计不规范
- **现状**：`sku_id_list varchar(500)` 存储 SKU ID 列表（JSON 或逗号分隔）
- **问题**：无法建立外键约束，查询效率低，数据完整性无法保证
- **建议**：
  - 方案A：拆分为 `t_sku_order_sku` 中间表
  - 方案B：如果订单只允许单SKU，直接用 `sku_id` 字段

#### 问题4：t_gift_record.object_id 缺少外键
- **现状**：`object_id bigint` 无外键约束
- **建议**：如果收礼人是主播，添加 FK 指向 `t_user.user_id`；如果是直播间，可能需要指向 `t_living_room.id`

#### 问题5：t_sku_info.sku_id 和 id 的关系
- **现状**：`t_sku_info` 有自增主键 `id`，同时有业务字段 `sku_id`
- **问题**：存在两个唯一标识，可能造成混淆
- **建议**：确认 `sku_id` 是否是业务主键，如果是可考虑用 `sku_id` 作为主键，去掉自增 `id`

#### 问题6：t_red_packet_config.anchor_id 类型不一致
- **现状**：`anchor_id int`（有符号）
- **建议**：改为 `anchor_id bigint unsigned`

### 5.2 优化建议

1. **统一 user_id 类型**：所有涉及用户的外键都应使用 `bigint unsigned`
2. **统一 anchor_id 类型**：所有涉及主播的外键都应使用 `bigint unsigned`
3. **补充索引**：
   - `t_gift_record` 建议在 `user_id`、`object_id`、`gift_id` 上建索引
   - `t_living_room` 建议在 `anchor_id`、`status` 上建索引
4. **电商订单表拆分**：将 `sku_id_list` 拆分为中间表更符合数据库设计范式

---

## 六、总结

整体表设计较为合理，模块划分清晰，外键关系明确。主要问题是：

1. **类型统一性**：user_id 和 anchor_id 的有符号/无符号问题需要统一
2. **预留表完整性**：电商相关表结构已设计但代码未实现
3. **扩展性**：红包雨、直播PK等功能的表结构已有基础

建议优先统一数据类型问题，预留表可根据后续业务开发进度逐步完善代码实现。
