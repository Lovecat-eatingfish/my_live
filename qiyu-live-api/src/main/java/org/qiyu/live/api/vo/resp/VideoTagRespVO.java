package org.qiyu.live.api.vo.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 视频标签响应 VO
 */
@Data
public class VideoTagRespVO implements Serializable {

    private static final long serialVersionUID = 1547230910002L;

    private Integer id;
    private String tagName;
}
