package org.qiyu.live.im.provider;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.provider.service.ImOnlineService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CountDownLatch;

/**
 * @Author idea
 * @Date: Created in 21:02 2023/7/9
 * @Description
 */
@SpringBootApplication
@EnableDubbo
public class ImProviderApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(ImProviderApplication.class);
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
