package org.qiyu.live.video.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.CountDownLatch;

/**
 * 视频服务启动类
 */
@SpringBootApplication
@EnableDubbo
public class VideoProviderApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(VideoProviderApplication.class);
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
