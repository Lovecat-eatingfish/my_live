package org.qiyu.live.bank.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 文档元信息：访问 http://127.0.0.1:38095/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI qiyuBankOpenApi() {
        return new OpenAPI().info(new Info()
                .title("旗鱼直播 支付网关 API")
                .description("充值下单/支付回调/对账 等银行服务接口文档")
                .version("1.0"));
    }
}
