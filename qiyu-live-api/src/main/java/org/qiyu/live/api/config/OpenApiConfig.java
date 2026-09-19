package org.qiyu.live.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 文档元信息：访问 http://127.0.0.1:38085/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI qiyuOpenApi() {
        return new OpenAPI().info(new Info()
                .title("旗鱼直播 C端 API")
                .description("直播/视频/私信/排行/钱包 等前端接口文档")
                .version("1.0"));
    }
}
