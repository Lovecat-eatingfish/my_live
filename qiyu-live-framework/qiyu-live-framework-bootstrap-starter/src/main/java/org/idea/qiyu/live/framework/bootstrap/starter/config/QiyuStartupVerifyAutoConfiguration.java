package org.idea.qiyu.live.framework.bootstrap.starter.config;

import org.idea.qiyu.live.framework.bootstrap.starter.check.QiyuProviderStartupVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Provider 启动自检自动装配
 *
 * @Author qiyu
 */
//@AutoConfiguration
//@ConditionalOnProperty(name = "qiyu.startup.verify", havingValue = "true", matchIfMissing = true)
public class QiyuStartupVerifyAutoConfiguration {

//    @Bean
//    @ConditionalOnMissingBean
//    public QiyuProviderStartupVerifier qiyuProviderStartupVerifier(ApplicationContext applicationContext) {
//        return new QiyuProviderStartupVerifier(applicationContext);
//    }
}
