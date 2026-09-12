package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.RedPacketConfigDTO;
import org.qiyu.live.gift.interfaces.IRedPacketRpc;
import org.qiyu.live.gift.provider.service.IRedPacketService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 红包雨RPC实现
 */
@DubboService
@Component
public class RedPacketRpcImpl implements IRedPacketRpc {

    @Resource
    private IRedPacketService redPacketService;

    @Override
    public RedPacketConfigDTO getByAnchorId(Long anchorId) {
        return redPacketService.getByAnchorId(anchorId);
    }

    @Override
    public RedPacketConfigDTO getByConfigCode(String configCode) {
        return redPacketService.getByConfigCode(configCode);
    }

    @Override
    public void create(RedPacketConfigDTO redPacketConfigDTO) {
        redPacketService.create(redPacketConfigDTO);
    }

    @Override
    public void prepare(Integer id) {
        redPacketService.prepare(id);
    }

    @Override
    public void send(Integer id) {
        redPacketService.send(id);
    }

    @Override
    public Integer receive(Integer id, Long userId, Integer roomId) {
        return redPacketService.receive(id, userId, roomId);
    }

    @Override
    public void settle(Integer id) {
        redPacketService.settle(id);
    }
}
