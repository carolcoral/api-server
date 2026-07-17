/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * SQLite数据库配置 - 自动创建数据库目录
 *
 * @author carolcoral
 */
@Configuration
public class SqliteDatabaseConfig implements InitializingBean {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqliteDatabaseConfig.class);

    // 从系统属性（由.env文件加载）获取配置
    private String sqliteUrl = System.getProperty("DB_URL", "jdbc:sqlite:./data/api-server.db");
    private String logFilePath = System.getProperty("LOG_FILE_PATH", "./logs/api-server.log");

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化SQLite数据库和日志目录...");
        
        try {
            // 提取数据库文件路径
            String dbPath = extractPathFromUrl(sqliteUrl, "jdbc:sqlite:");
            createDirectoryIfNotExists(dbPath);
            
            // 提取日志文件路径
            createDirectoryIfNotExists(logFilePath);
            
            log.info("数据库和日志目录初始化完成");
        } catch (Exception e) {
            log.error("初始化目录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从URL或路径字符串中提取目录路径
     *
     * @param url URL或路径字符串
     * @param prefix 需要移除的前缀
     * @return 目录路径
     */
    private String extractPathFromUrl(String url, String prefix) {
        String path = url;
        if (url.startsWith(prefix)) {
            path = url.substring(prefix.length());
        }
        
        // 如果是相对路径，转换为绝对路径
        File file = new File(path);
        if (!file.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            file = new File(userDir, path);
        }
        
        return file.getParent();
    }

    /**
     * 创建目录（如果不存在）
     *
     * @param path 目录路径
     */
    private void createDirectoryIfNotExists(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        
        File directory;
        if (path.endsWith(".db") || path.endsWith(".log")) {
            // 如果是文件路径，提取目录
            directory = new File(path).getParentFile();
        } else {
            // 如果是目录路径
            directory = new File(path);
        }
        
        if (directory == null) {
            return;
        }
        
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("创建目录成功: {}", directory.getAbsolutePath());
            } else {
                log.error("创建目录失败: {}", directory.getAbsolutePath());
            }
        } else {
            log.debug("目录已存在: {}", directory.getAbsolutePath());
        }
    }
}
