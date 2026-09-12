package org.qiyu.live.bank.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 对账差错明细DTO
 */
public class ReconciliationDetailDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 6535402279954104883L;
    private Long id;
    private String bizDate;
    private Integer diffType;
    private String orderId;
    private Long userId;
    private Integer productId;
    private Integer expectNum;
    private Integer actualNum;
    private Integer status;
    private String remark;
    private String createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizDate() {
        return bizDate;
    }

    public void setBizDate(String bizDate) {
        this.bizDate = bizDate;
    }

    public Integer getDiffType() {
        return diffType;
    }

    public void setDiffType(Integer diffType) {
        this.diffType = diffType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getExpectNum() {
        return expectNum;
    }

    public void setExpectNum(Integer expectNum) {
        this.expectNum = expectNum;
    }

    public Integer getActualNum() {
        return actualNum;
    }

    public void setActualNum(Integer actualNum) {
        this.actualNum = actualNum;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "ReconciliationDetailDTO{" +
                "id=" + id +
                ", bizDate='" + bizDate + '\'' +
                ", diffType=" + diffType +
                ", orderId='" + orderId + '\'' +
                ", userId=" + userId +
                ", expectNum=" + expectNum +
                ", actualNum=" + actualNum +
                '}';
    }
}
