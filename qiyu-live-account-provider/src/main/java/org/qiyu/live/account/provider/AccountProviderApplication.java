package org.qiyu.live.account.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.concurrent.CountDownLatch;

/**
 * @Author idea
 * @Date: Created in 10:13 2023/6/20
 * @Description
 */
@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
public class AccountProviderApplication{

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(AccountProviderApplication.class);
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
