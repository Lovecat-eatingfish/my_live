package org.qiyu.live.stream.provider.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;

/**
 * 直播间 Mapper（流媒体扩展字段）
 */
@Mapper
public interface LivingRoomMapper extends BaseMapper<LivingRoomPO> {

    /** 更新流状态 */
    @Update("UPDATE t_living_room SET stream_status=#{status}, stream_start_time=#{streamStartTime} WHERE id=#{roomId}")
    int updateStreamStatus(@Param("roomId") Integer roomId,
                           @Param("status") Integer status,
                           @Param("streamStartTime") java.util.Date streamStartTime);

    /** 更新 streamKey */
    @Update("UPDATE t_living_room SET stream_key=#{streamKey}, push_url=#{pushUrl} WHERE id=#{roomId}")
    int updateStreamKey(@Param("roomId") Integer roomId,
                        @Param("streamKey") String streamKey,
                        @Param("pushUrl") String pushUrl);

    /** 重置流状态（关播时） */
    @Update("UPDATE t_living_room SET stream_status=0, stream_key=null, push_url=null WHERE id=#{roomId}")
    int resetStream(@Param("roomId") Integer roomId);
}
