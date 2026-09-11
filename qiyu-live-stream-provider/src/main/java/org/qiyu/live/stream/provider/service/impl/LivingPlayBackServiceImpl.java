package org.qiyu.live.stream.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.stream.interfaces.dto.LivingRoomRecordDTO;
import org.qiyu.live.stream.interfaces.dto.PlayBackDTO;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomRecordMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.qiyu.live.stream.provider.dao.po.LivingRoomRecordPO;
import org.qiyu.live.stream.provider.service.ILivingPlayBackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 直播播放服务实现
 */
@Service
public class LivingPlayBackServiceImpl implements ILivingPlayBackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingPlayBackServiceImpl.class);

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private LivingRoomRecordMapper livingRoomRecordMapper;
    @Resource
    private SrsConfig srsConfig;

    @Override
    public PlayBackDTO getPlayUrl(Integer roomId, String networkType) {
        PlayBackDTO dto = new PlayBackDTO();
        dto.setRoomId(roomId);

        LivingRoomPO room = livingRoomMapper.selectById(roomId);
        if (room == null || !isStreaming(room)) {
            dto.setIsLiving(false);
            dto.setHlsUrl("");
            return dto;
        }

        dto.setIsLiving(true);
        // HLS 地址: http://host:8080/live/{streamKey}.m3u8
        String hlsUrl = srsConfig.getHlsBaseUrl() + "/" + room.getStreamKey() + ".m3u8";
        dto.setHlsUrl(hlsUrl);

        LOGGER.info("[getPlayUrl] roomId={}, hlsUrl={}", roomId, hlsUrl);
        return dto;
    }

    @Override
    public List<LivingRoomRecordDTO> getRecordList(Integer roomId) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<LivingRoomRecordPO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LivingRoomRecordPO::getRoomId, roomId);
        queryWrapper.eq(LivingRoomRecordPO::getStatus, 2); // 2=可用
        queryWrapper.orderByDesc(LivingRoomRecordPO::getCreateTime);
        List<LivingRoomRecordPO> poList = livingRoomRecordMapper.selectList(queryWrapper);
        return ConvertBeanUtils.convertList(poList, LivingRoomRecordDTO.class);
    }

    private boolean isStreaming(LivingRoomPO room) {
        Integer streamStatus = room.getStreamStatus();
        return streamStatus != null && streamStatus == 1 && StringUtils.hasText(room.getStreamKey());
    }
}
