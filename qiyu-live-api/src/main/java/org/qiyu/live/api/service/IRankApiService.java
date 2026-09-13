package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.resp.RankItemRespVO;

import java.util.List;

/**
 * 排行榜服务（api 层直读 Redis ZSET，纯 Redis 零新依赖）
 */
public interface IRankApiService {

    /** 主播收礼榜 period: day=日榜 week=周榜(7 日聚合) */
    List<RankItemRespVO> anchorGiftRank(String period);

    /** 单房间本场贡献榜 top10 */
    List<RankItemRespVO> roomGiftRank(Integer roomId);

    /** 人气榜 top10 */
    List<RankItemRespVO> heatRank();
}
