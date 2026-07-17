/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Dotenv配置类 - 加载.env文件到系统属性
 * 确保Spring Boot能够读取.env文件中的配置
 *
 * @author carolcoral
 */
@Configuration
public class DotenvConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DotenvConfig.class);

    @PostConstruct
    public void loadDotenv() {
        try {
            // 获取项目根目录的绝对路径
            String projectRoot = new java.io.File(".").getAbsolutePath();
            log.info("项目根目录: {}", projectRoot);
            
            // 尝试从项目根目录加载.env文件
            Dotenv dotenv = Dotenv.configure()
                    .directory(projectRoot)
                    .filename(".env")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            // 将.env文件中的变量加载到系统属性，供Spring @Value使用
            int loadedCount = 0;
            for (io.github.cdimascio.dotenv.DotenvEntry entry : dotenv.entries()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                // 如果系统属性未设置，则从.env文件加载（优先系统属性，但不管环境变量是否存在都加载）
                if (System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    loadedCount++;
                    
                    // 对于敏感信息，只显示部分
                    String displayValue = value;
                    if (key.contains("PASSWORD") || key.contains("SECRET")) {
                        displayValue = "***" + (value.length() > 6 ? value.substring(value.length() - 4) : "");
                    }
                    log.debug("从.env文件加载配置: {}={}", key, displayValue);
                }
            }

            if (loadedCount > 0) {
                log.info("成功从.env文件加载 {} 个配置项", loadedCount);
                
                // 调试：打印Swagger配置是否加载成功
                String swaggerUser = System.getProperty("SWAGGER_USERNAME");
                if (swaggerUser != null) {
                    log.info("Swagger用户名已加载: {}", swaggerUser);
                } else {
                    log.warn("未找到Swagger用户名配置");
                }
            } else {
                log.warn("从.env文件未加载到任何配置，将使用系统环境变量或默认值");
            }
        } catch (Exception e) {
            log.error("加载.env文件失败: {}", e.getMessage(), e);
        }
    }
}
