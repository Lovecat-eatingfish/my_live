package org.qiyu.live.api.vo;

/**
 * 直播间带货商品VO（小黄车）
 */
public class ShopSkuVO {

    private Integer skuId;
    /** 商品名 */
    private String name;
    /** 商品图 */
    private String iconUrl;
    /** 价格（抖币/分） */
    private Integer skuPrice;
    /** 描述 */
    private String remark;

    public Integer getSkuId() {
        return skuId;
    }

    public void setSkuId(Integer skuId) {
        this.skuId = skuId;
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
