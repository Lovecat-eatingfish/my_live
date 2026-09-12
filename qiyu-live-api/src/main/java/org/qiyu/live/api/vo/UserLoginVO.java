package org.qiyu.live.api.vo;

/**
 * @Author idea
 * @Date: Created in 11:02 2023/6/15
 */
public class UserLoginVO {

    private Long userId;
    private String token;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "UserLoginVO{" +
                "userId=" + userId +
                ", token='" + token + '\'' +
                '}';
    }
}
