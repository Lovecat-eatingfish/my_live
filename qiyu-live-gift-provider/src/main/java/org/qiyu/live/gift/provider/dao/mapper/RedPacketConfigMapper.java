package org.qiyu.live.gift.provider.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.qiyu.live.gift.provider.dao.po.RedPacketConfigPO;

/**
 * 红包雨配置Mapper
 */
@Mapper
public interface RedPacketConfigMapper extends BaseMapper<RedPacketConfigPO> {

    /**
     * 扣减库存
     */
    @Update("UPDATE t_red_packet_config SET total_get = total_get + 1, total_get_price = total_get_price + #{price} WHERE id = #{id} AND total_count > total_get")
    int decrementStock(@Param("id") Integer id, @Param("price") Integer price);

    /**
     * 领取统计异步同步（红包池领取后由MQ消费者调用）
     */
    @Update("UPDATE t_red_packet_config SET total_get = total_get + 1, total_get_price = total_get_price + #{price} WHERE id = #{id}")
    int incrReceiveStat(@Param("id") Integer id, @Param("price") int price);
}
