package org.qiyu.live.admin.controller;

import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.bank.dto.PayProductDTO;
import org.qiyu.live.bank.interfaces.IPayProductRpc;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 充值档位管理
 */
@RestController
@RequestMapping("/payProduct")
public class AdminPayProductController {

    @DubboReference(check = false)
    private IPayProductRpc payProductRpc;

    @PostMapping("/list")
    public WebResponseVO list() {
        List<PayProductDTO> list = payProductRpc.products(0);
        return WebResponseVO.success(list);
    }

    /** 修改档位（价格分/上下架） */
    @PostMapping("/update")
    public WebResponseVO update(Integer productId, Integer price, Integer validStatus) {
        ErrorAssert.isNotNull(productId, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(payProductRpc.adminUpdateProduct(productId, price, validStatus));
    }
}
