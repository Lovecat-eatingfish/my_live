package org.qiyu.live.api.vo.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 视频详情响应 VO（播放地址 + 互动数据）
 */
@Data
public class VideoDetailRespVO implements Serializable {

    private static final long serialVersionUID = 1547230910004L;

    private VideoItemRespVO item;
}
