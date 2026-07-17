/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * API Server 应用主类
 *
 * @author carolcoral
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(
        info = @Info(
                title = "API Server API",
                version = "1.0.0",
                description = "API接口服务器 - 支持自定义接口配置、多项目管理、权限控制",
                contact = @Contact(
                        name = "carolcoral",
                        url = "https://github.com/carolcoral"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"
                )
        )
)
public class ApiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }

}
