package org.qiyu.live.video.interfaces.rpc;

import java.io.Serializable;

/**
 * 视频标签 DTO
 */
public class TagDTO implements Serializable {

    private static final long serialVersionUID = 1547230891236L;

    private Integer id;
    private String tagName;

    public TagDTO() {}

    public TagDTO(Integer id, String tagName) {
        this.id = id;
        this.tagName = tagName;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
}
