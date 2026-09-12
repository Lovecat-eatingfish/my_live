package org.qiyu.live.stream.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 直播推流服务启动类
 */
@SpringBootApplication
@EnableDubbo
public class StreamProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamProviderApplication.class, args);
    }
}
