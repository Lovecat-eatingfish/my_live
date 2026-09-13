package org.qiyu.live.common.interfaces.topic;

/**
 * @Author idea
 * @Date: Created in 16:14 2023/5/28
 * @Description
 */
public class UserProviderTopicNames {

    /**
     * 专门处理和用户信息相关的缓存延迟删除操作
     */
    public static final String CACHE_ASYNC_DELETE_TOPIC = "UserCacheAsyncDelete";

    /**
     * 主播开播后向粉丝在线推送（user-provider 消费 → 5567）
     */
    public static final String OPEN_LIVING_PUSH_TOPIC = "UserOpenLivingPushTopic";

    /**
     * 用户经验值变更（各动作方发送，user-provider 单点消费结算等级）
     */
    public static final String USER_EXP_CHANGE_TOPIC = "UserExpChangeTopic";
}
