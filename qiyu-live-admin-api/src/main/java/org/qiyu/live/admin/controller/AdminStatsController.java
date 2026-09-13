package org.qiyu.live.admin.controller;

import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 运营仪表盘：同实例跨库 4 条 COUNT（今日新增用户/开播场次/充值额/交易流水）
 */
@RestController
@RequestMapping("/stats")
public class AdminStatsController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/dashboard")
    public WebResponseVO dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("newUsers", count(
                "SELECT COUNT(*) FROM qiyu_live_user.t_user WHERE create_time >= CURDATE()"));
        data.put("openRooms", count(
                "SELECT COUNT(*) FROM qiyu_live_living.t_living_room WHERE start_time >= CURDATE()"));
        // 今日充值额（分 → 元，只算支付成功的订单）
        data.put("rechargeYuan", count(
                "SELECT IFNULL(SUM(p.price), 0) FROM qiyu_live_bank.t_pay_order o" +
                        " JOIN qiyu_live_bank.t_pay_product p ON o.product_id = p.id" +
                        " WHERE o.status = 2 AND o.pay_time >= CURDATE()") / 100.0);
        // 今日交易流水笔数（金币账务流水）
        data.put("tradeCount", count(
                "SELECT COUNT(*) FROM qiyu_live_bank.t_qiyu_currency_trade WHERE create_time >= CURDATE()"));
        return WebResponseVO.success(data);
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }
}
