package org.qiyu.live.living.interfaces.dto;

import java.io.Serializable;

/**
 * 直播分区 DTO（id 即房间 type 值）
 */
public class LivingCategoryDTO implements Serializable {

    private Integer id;
    private String name;
    private String icon;
    private Integer sort;
    private Integer status;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
