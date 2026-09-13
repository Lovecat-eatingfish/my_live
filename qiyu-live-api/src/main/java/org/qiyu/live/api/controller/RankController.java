package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.IRankApiService;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排行榜（纯 Redis）
 */
@RestController
@RequestMapping("/rank")
public class RankController {

    @Resource
    private IRankApiService rankApiService;

    /** 主播收礼榜 period: day / week */
    @PostMapping("/anchorGift")
    public WebResponseVO anchorGiftRank(String period) {
        return WebResponseVO.success(rankApiService.anchorGiftRank(period));
    }

    /** 单房间本场贡献榜 top10 */
    @PostMapping("/roomGift")
    public WebResponseVO roomGiftRank(Integer roomId) {
        return WebResponseVO.success(rankApiService.roomGiftRank(roomId));
    }

    /** 人气榜 top10 */
    @PostMapping("/heat")
    public WebResponseVO heatRank() {
        return WebResponseVO.success(rankApiService.heatRank());
    }
}
