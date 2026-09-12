package org.qiyu.live.gift.dto;

import java.io.Serializable;

/**
 * 购物车条目DTO
 * 购物车以直播间为维度（带货商品只属于当前直播间），redis hash 存储：hashKey=skuId，value=数量
 */
public class CartItemDTO implements Serializable {

    private Integer skuId;

    /**
     * 购买数量
     */
    private Integer num;

    private String name;

    private String iconUrl;

    /**
     * 单价（分）
     */
    private Integer skuPrice;

    private String remark;

    public Integer getSkuId() {
        return skuId;
    }

    public void setSkuId(Integer skuId) {
        this.skuId = skuId;
    }

    public Integer getNum() {
        return num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Integer getSkuPrice() {
        return skuPrice;
    }

    public void setSkuPrice(Integer skuPrice) {
        this.skuPrice = skuPrice;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
