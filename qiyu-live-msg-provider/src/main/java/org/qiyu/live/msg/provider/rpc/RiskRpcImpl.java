package org.qiyu.live.msg.provider.rpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.RiskCheckReqDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.common.interfaces.dto.RiskWordDTO;
import org.qiyu.live.common.interfaces.rpc.IRiskRpc;
import org.qiyu.live.msg.provider.dao.mapper.RiskSensitiveWordMapper;
import org.qiyu.live.msg.provider.dao.po.RiskSensitiveWordPO;
import org.qiyu.live.msg.provider.service.risk.RiskCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内容风控 RPC 实现（挂在 msg-provider，接口形状独立，后续拆包零改动）
 */
@DubboService
public class RiskRpcImpl implements IRiskRpc {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskRpcImpl.class);

    @Resource
    private RiskCheckService riskCheckService;
    @Resource
    private RiskSensitiveWordMapper riskSensitiveWordMapper;

    @Override
    public RiskCheckRespDTO checkText(RiskCheckReqDTO reqDTO) {
        if (reqDTO == null) {
            return RiskCheckRespDTO.pass();
        }
        return riskCheckService.checkText(reqDTO.getText(), reqDTO.getScene());
    }

    @Override
    public boolean checkDanmuFreq(Long userId) {
        return riskCheckService.checkDanmuFreq(userId);
    }

    @Override
    public boolean addWord(RiskWordDTO wordDTO) {
        if (wordDTO == null || !StringUtils.hasText(wordDTO.getWord())) {
            return false;
        }
        RiskSensitiveWordPO po = new RiskSensitiveWordPO();
        po.setWord(wordDTO.getWord().trim());
        po.setLevel(wordDTO.getLevel() == null ? 1 : wordDTO.getLevel());
        po.setScene(wordDTO.getScene() == null ? 0 : wordDTO.getScene());
        po.setStatus(1);
        try {
            riskSensitiveWordMapper.insert(po);
        } catch (DuplicateKeyException e) {
            LOGGER.info("[addWord] word {} scene {} already exists", po.getWord(), po.getScene());
            return false;
        }
        riskCheckService.bumpVersion();
        return true;
    }

    @Override
    public boolean deleteWord(Long id) {
        if (id == null) {
            return false;
        }
        int rows = riskSensitiveWordMapper.deleteById(id);
        if (rows > 0) {
            riskCheckService.bumpVersion();
        }
        return rows > 0;
    }

    @Override
    public List<RiskWordDTO> listWords() {
        LambdaQueryWrapper<RiskSensitiveWordPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RiskSensitiveWordPO::getId);
        List<RiskSensitiveWordPO> list = riskSensitiveWordMapper.selectList(wrapper);
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(po -> {
            RiskWordDTO dto = new RiskWordDTO();
            dto.setId(po.getId());
            dto.setWord(po.getWord());
            dto.setLevel(po.getLevel());
            dto.setScene(po.getScene());
            dto.setStatus(po.getStatus());
            return dto;
        }).collect(Collectors.toList());
    }
}
