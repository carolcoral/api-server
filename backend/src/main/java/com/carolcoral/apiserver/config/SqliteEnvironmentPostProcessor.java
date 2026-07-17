/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * SQLite环境后置处理器 - 在Spring Boot启动早期创建数据库和日志目录
 *
 * @author carolcoral
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SqliteEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqliteEnvironmentPostProcessor.class);

    private static final String PROPERTY_SOURCE_NAME = "sqliteDirectoryConfig";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            log.info("========================================");
            log.info("环境初始化 - 加载 .env 配置");
            log.info("========================================");

            // 提前加载 .env 文件到 Environment，确保 @Value 能解析到
            loadDotenvToEnvironment(environment);

            log.info("========================================");
            log.info("SQLite数据库环境初始化");
            log.info("========================================");

            // 获取数据库URL
            String sqliteUrl = environment.getProperty("spring.datasource.url", "jdbc:sqlite:./data/api-server.db");
            String logFilePath = environment.getProperty("logging.file.name", "./logs/api-server.log");

            // 创建数据库目录
            String dbDir = extractDirectoryPath(sqliteUrl, "jdbc:sqlite:");
            if (dbDir != null) {
                createDirectory(dbDir);
                log.info("数据库目录: {}", new File(dbDir).getAbsolutePath());
            }

            // 创建日志目录
            String logDir = extractDirectoryPath(logFilePath, null);
            if (logDir != null) {
                createDirectory(logDir);
                log.info("日志目录: {}", new File(logDir).getAbsolutePath());
            }

            // 将绝对路径设置回环境（可选）
            Map<String, Object> properties = new HashMap<>();
            
            // 注册为最高优先级的属性源
            MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            environment.getPropertySources().addFirst(propertySource);
            
            log.info("========================================");
            log.info("环境初始化完成");
            log.info("========================================");

        } catch (Exception e) {
            log.error("初始化SQLite环境失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从项目根目录加载 .env 文件并注册到 Spring Environment
     * 这必须在 Bean 初始化之前执行，确保 @Value("${jwt.secret}") 等能正确解析
     */
    private void loadDotenvToEnvironment(ConfigurableEnvironment environment) {
        try {
            String userDir = System.getProperty("user.dir");
            File envFile = new File(userDir, ".env");
            if (!envFile.exists()) {
                log.warn(".env 文件不存在: {}", envFile.getAbsolutePath());
                return;
            }

            Map<String, Object> envProperties = new HashMap<>();
            int count = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // 跳过空行和注释
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    // 解析 KEY=VALUE
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        // 去除引号
                        if ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        envProperties.put(key, value);
                        count++;
                    }
                }
            }

            if (!envProperties.isEmpty()) {
                MapPropertySource envPropertySource = new MapPropertySource("dotenvConfig", envProperties);
                environment.getPropertySources().addFirst(envPropertySource);
                log.info("从 .env 文件加载了 {} 个配置项到 Environment", count);

                // 调试：确认 JWT_SECRET 已加载
                String jwtSecret = environment.getProperty("JWT_SECRET");
                if (jwtSecret != null) {
                    log.info("JWT_SECRET 已加载，长度: {} 字符 ({} bits)",
                            jwtSecret.length(), jwtSecret.length() * 8);
                } else {
                    log.warn("JWT_SECRET 未在 .env 中找到，将使用 application.yml 默认值");
                }
            }
        } catch (Exception e) {
            log.error("加载 .env 文件到 Environment 失败: {}", e.getMessage());
        }
    }

    /**
     * 提取目录路径
     *
     * @param path 文件路径或URL
     * @param prefix 需要移除的前缀（如 jdbc:sqlite:）
     * @return 目录路径
     */
    private String extractDirectoryPath(String path, String prefix) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String filePath = path;
        if (prefix != null && path.startsWith(prefix)) {
            filePath = path.substring(prefix.length());
        }

        // 转换为绝对路径
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            file = new File(userDir, filePath);
        }

        // 返回目录路径
        File parentDir = file.getParentFile();
        return parentDir != null ? parentDir.getAbsolutePath() : null;
    }

    /**
     * 创建目录（如果不存在）
     *
     * @param dirPath 目录路径
     */
    private void createDirectory(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return;
        }

        File directory = new File(dirPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("✓ 创建目录成功: {}", directory.getAbsolutePath());
            } else {
                log.error("✗ 创建目录失败: {}", directory.getAbsolutePath());
            }
        } else {
            log.debug("目录已存在: {}", directory.getAbsolutePath());
        }
    }
}
