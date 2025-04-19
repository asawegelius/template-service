package com.template.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(TemplateServiceConfig.SWAGGER_TITLE)
                        .version(TemplateServiceConfig.SWAGGER_VERSION)
                        .description(TemplateServiceConfig.SWAGGER_DESCRIPTION));
    }
}
