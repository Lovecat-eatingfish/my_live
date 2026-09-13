package org.qiyu.live.admin;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 旗鱼运营台后端（内部管理系统）
 * 独立于C端：独立登录（t_admin_user），Dubbo复用现有微服务能力
 */
@SpringBootApplication
@EnableDubbo
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}
