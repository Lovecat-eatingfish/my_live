package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.dto.DmConversationDTO;
import org.qiyu.live.common.interfaces.dto.DmMessageDTO;
import org.qiyu.live.common.interfaces.rpc.IDmRpc;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信（会话列表/历史消息/已读上报），消息上下行走 IM 5568/5569
 */
@RestController
@RequestMapping("/dm")
public class DmController {

    @DubboReference(check = false)
    private IDmRpc dmRpc;
    @DubboReference(check = false)
    private IUserRpc userRpc;

    /** 会话列表（富化对方昵称/头像） */
    @GetMapping("/conversations")
    public WebResponseVO conversations() {
        Long userId = QiyuRequestContext.getUserId();
        List<DmConversationDTO> list = dmRpc.listConversations(userId);
        if (list.isEmpty()) {
            return WebResponseVO.success(Collections.emptyList());
        }
        List<Long> peerIds = new ArrayList<>();
        list.forEach(c -> peerIds.add(c.getPeerUid()));
        Map<Long, UserDTO> userMap = userRpc.batchQueryUserInfo(peerIds);
        List<Map<String, Object>> resp = new ArrayList<>();
        for (DmConversationDTO c : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("peerUid", c.getPeerUid());
            item.put("lastMsg", c.getLastMsg());
            item.put("unreadCnt", c.getUnreadCnt());
            item.put("updateTime", c.getUpdateTime());
            UserDTO peer = userMap.get(c.getPeerUid());
            item.put("peerNick", peer == null ? "用户" + c.getPeerUid() : peer.getNickName());
            item.put("peerAvatar", peer == null ? "" : peer.getAvatar());
            resp.add(item);
        }
        return WebResponseVO.success(resp);
    }

    /** 与某人的历史消息（最新 size 条，正序） */
    @GetMapping("/history")
    public WebResponseVO history(Long peerUid, Integer size) {
        ErrorAssert.isNotNull(peerUid, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(dmRpc.listHistory(QiyuRequestContext.getUserId(), peerUid,
                size == null ? 50 : size));
    }

    /** 打开会话：清零未读 */
    @PostMapping("/markRead")
    public WebResponseVO markRead(Long peerUid) {
        ErrorAssert.isNotNull(peerUid, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(dmRpc.markRead(QiyuRequestContext.getUserId(), peerUid));
    }

    /** 全部会话未读总数（导航角标轮询用） */
    @GetMapping("/unreadTotal")
    public WebResponseVO unreadTotal() {
        return WebResponseVO.success(dmRpc.unreadTotal(QiyuRequestContext.getUserId()));
    }
}
