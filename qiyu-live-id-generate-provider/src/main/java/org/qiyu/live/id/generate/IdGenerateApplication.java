package org.qiyu.live.id.generate;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.util.concurrent.CountDownLatch;


/**
 * @Author idea
 * @Date: Created in 19:45 2023/5/25
 * @Description
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableDubbo
public class IdGenerateApplication{

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(IdGenerateApplication.class);
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
