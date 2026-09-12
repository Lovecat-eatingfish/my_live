package org.qiyu.live.video.provider.dao.maper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.qiyu.live.video.provider.dao.po.VideoCommentPO;

@Mapper
public interface IVideoCommentMapper extends BaseMapper<VideoCommentPO> {
}
