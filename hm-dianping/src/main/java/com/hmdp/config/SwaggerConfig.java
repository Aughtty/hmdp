package com.hmdp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demo API")
                        .version("1.0")
                        .description("Swagger 接口测试示例")
                        .contact(new Contact()
                                .name("开发者")
                                .email("yueli98354@gamil.com")
                                .url("https://example.com")
                        )
                );
    }
}
