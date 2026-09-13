package org.idea.qiyu.live.framework.redis.starter.key;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @Author idea
 * @Date: Created in 15:58 2023/5/17
 * @Description
 */
@Configuration
@Conditional(RedisKeyLoadMatch.class)
public class MsgProviderCacheKeyBuilder extends RedisKeyBuilder {

    private static String SMS_LOGIN_CODE_KEY = "smsLoginCode";
    private static String DANMU_FREQ_KEY = "danmuFreq";
    private static String RISK_WORD_VERSION_KEY = "riskWordVersion";
    private static String DANMU_EXP_DAILY_KEY = "danmuExpDaily";

    public String buildSmsLoginCodeKey(String phone) {
        return super.getPrefix() + SMS_LOGIN_CODE_KEY + super.getSplitItem() + phone;
    }

    public String buildDanmuFreqKey(Long userId) {
        return super.getPrefix() + DANMU_FREQ_KEY + super.getSplitItem() + userId;
    }

    public String buildRiskWordVersionKey() {
        return super.getPrefix() + RISK_WORD_VERSION_KEY;
    }

    public String buildDanmuExpDailyKey(Long userId, String yyyyMMdd) {
        return super.getPrefix() + DANMU_EXP_DAILY_KEY + super.getSplitItem() + userId + super.getSplitItem() + yyyyMMdd;
    }

}
