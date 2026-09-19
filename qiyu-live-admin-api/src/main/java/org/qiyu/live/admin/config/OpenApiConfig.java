package org.qiyu.live.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 文档元信息：访问 http://127.0.0.1:38100/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI qiyuAdminOpenApi() {
        return new OpenAPI().info(new Info()
                .title("旗鱼运营台 API")
                .description("视频审核/直播巡查/对账/风控/配置管理 等运营接口文档")
                .version("1.0"));
    }
}
