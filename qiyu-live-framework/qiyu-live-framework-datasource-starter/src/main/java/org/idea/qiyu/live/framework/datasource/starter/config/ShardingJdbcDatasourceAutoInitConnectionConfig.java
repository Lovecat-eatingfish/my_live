package org.idea.qiyu.live.framework.datasource.starter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 后续考虑如何将这个配置类做成一个参数控制
 *
 * @Author idea
 * @Date: Created in 18:06 2023/5/7
 * @Description
 */
@Configuration
public class ShardingJdbcDatasourceAutoInitConnectionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShardingJdbcDatasourceAutoInitConnectionConfig.class);

    @Bean
    public ApplicationRunner runner(DataSource dataSource) {
        return args -> {
            LOGGER.info("dataSource: {}", dataSource);
            //手动触发下连接池的连接创建
            // 解决的问题：Spring Boot 启动时 DataSource 可能懒加载连接，等到第一次请求时才建连接，导致第一次请求慢。
            //作用：启动时主动调用一次 getConnection()，提前把连接池建好。
            Connection connection = dataSource.getConnection();
        };
    }
}
