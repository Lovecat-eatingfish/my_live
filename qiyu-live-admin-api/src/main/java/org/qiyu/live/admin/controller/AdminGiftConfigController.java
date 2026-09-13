package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.gift.dto.GiftConfigDTO;
import org.qiyu.live.gift.interfaces.IGiftConfigRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 礼物配置管理（礼物表 + Redis 缓存失效均由 gift-provider 内部处理）
 */
@RestController
@RequestMapping("/giftConfig")
public class AdminGiftConfigController {

    @DubboReference(check = false)
    private IGiftConfigRpc giftConfigRpc;

    @PostMapping("/list")
    public WebResponseVO list() {
        return WebResponseVO.success(giftConfigRpc.queryGiftList());
    }

    /** 修改礼物（价格/名称/上下架） */
    @PostMapping("/update")
    public WebResponseVO update(Integer giftId, Integer price, String giftName, Integer status) {
        ErrorAssert.isNotNull(giftId, BizBaseErrorEnum.PARAM_ERROR);
        GiftConfigDTO dto = giftConfigRpc.getByGiftId(giftId);
        ErrorAssert.isNotNull(dto, BizBaseErrorEnum.PARAM_ERROR);
        if (price != null) {
            dto.setPrice(price);
        }
        if (giftName != null && !giftName.isBlank()) {
            dto.setGiftName(giftName);
        }
        if (status != null) {
            dto.setStatus(status);
        }
        giftConfigRpc.updateOne(dto);
        return WebResponseVO.success(true);
    }
}
