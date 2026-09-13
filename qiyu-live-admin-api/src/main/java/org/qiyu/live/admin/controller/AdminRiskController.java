package org.qiyu.live.admin.controller;

import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.dto.RiskWordDTO;
import org.qiyu.live.common.interfaces.rpc.IRiskRpc;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感词管理（词库经 IRiskRpc 落在 msg-provider，改动后自动热更新）
 */
@RestController
@RequestMapping("/riskWord")
public class AdminRiskController {

    @DubboReference(check = false)
    private IRiskRpc riskRpc;

    @PostMapping("/list")
    public WebResponseVO list() {
        return WebResponseVO.success(riskRpc.listWords());
    }

    /**
     * level: 1拦截 2替换* 3仅记录；scene: 0全部 1弹幕 2昵称 3视频标题 4评论 5房间名
     */
    @PostMapping("/add")
    public WebResponseVO add(String word, Integer level, Integer scene) {
        if (!StringUtils.hasText(word)) {
            return WebResponseVO.bizError("词不能为空");
        }
        RiskWordDTO dto = new RiskWordDTO();
        dto.setWord(word.trim());
        dto.setLevel(level == null ? 1 : level);
        dto.setScene(scene == null ? 0 : scene);
        boolean ok = riskRpc.addWord(dto);
        return ok ? WebResponseVO.success(true) : WebResponseVO.bizError("新增失败（可能已存在）");
    }

    @PostMapping("/delete")
    public WebResponseVO delete(Long id) {
        boolean ok = riskRpc.deleteWord(id);
        return ok ? WebResponseVO.success(true) : WebResponseVO.bizError("删除失败");
    }
}
