/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置类
 *
 * @author carolcoral
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API Server API",
                version = "1.0.0",
                description = "API接口模拟服务器 - 支持自定义接口配置、多项目管理、权限控制",
                contact = @Contact(
                        name = "carolcoral",
                        url = "https://github.com/carolcoral"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080/api", description = "本地开发环境")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
public class SwaggerConfig {

    /**
     * 配置OpenAPI
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(In.HEADER)
                                        .name("Authorization")
                        )
                );
    }

    /**
     * 自定义OpenAPI配置
     *
     * @return OpenApiCustomizer
     */
    @Bean
    public OpenApiCustomizer customerGlobalHeaderOpenApiCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    // 为所有操作添加默认的500错误响应
                    // operation.getResponses().addApiResponse("500", new ApiResponse()
                    //         .description("服务器内部错误")
                    //         .content(new Content().addMediaType("application/json",
                    //                 new io.swagger.v3.oas.models.media.MediaType()
                    //                         .example("{\"code\": 500, \"message\": \"服务器内部错误\", \"timestamp\": 1703123456789}"))));
                })
        );
    }
}
