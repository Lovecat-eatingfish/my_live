package org.qiyu.live.video.provider.dao.maper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO;

@Mapper
public interface IVideoWatchHistoryMapper extends BaseMapper<VideoWatchHistoryPO> {
}
