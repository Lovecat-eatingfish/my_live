package org.qiyu.live.common.interfaces.rpc;

import org.qiyu.live.common.interfaces.dto.RiskCheckReqDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.common.interfaces.dto.RiskWordDTO;

import java.util.List;

/**
 * 内容风控 RPC（当前实现挂在 msg-provider 内，接口按独立 RPC 形状写，后续拆包零改动）
 */
public interface IRiskRpc {

    /**
     * 敏感词检测：pass 放行 / blocked 拦截 / replacedText 替换后放行
     */
    RiskCheckRespDTO checkText(RiskCheckReqDTO reqDTO);

    /**
     * 弹幕频率风控：true=放行，false=窗口内超限应丢弃
     */
    boolean checkDanmuFreq(Long userId);

    /** 新增敏感词（词+场景唯一） */
    boolean addWord(RiskWordDTO wordDTO);

    /** 删除敏感词 */
    boolean deleteWord(Long id);

    /** 全量词列表（后台管理） */
    List<RiskWordDTO> listWords();
}
