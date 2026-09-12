package org.qiyu.live.user.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.concurrent.CountDownLatch;

/**
 * @Author idea
 * @Date: Created in 15:39 2023/4/16
 * @Description 用户中台服务提供者
 */

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
public class UserProviderApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(UserProviderApplication.class);
        springApplication.setWebApplicationType(WebApplicationType.NONE);
        var ctx = springApplication.run(args);

        CountDownLatch latch = new CountDownLatch(1);
        // JVM关闭钩子，收到正常终止信号，释放latch，触发Spring优雅销毁
        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}
