/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库健康检查 — 检查数据库连接和表状态
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Component("database")
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    private static final long THRESHOLD_WARN_MB = 1024;  // 1GB
    private static final long THRESHOLD_ERROR_MB = 512;   // 512MB

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Operation(summary = "数据库健康检查")
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            // 检查连接有效性
            if (!conn.isValid(5)) {
                return Health.down()
                        .withDetail("error", "数据库连接无效")
                        .build();
            }

            // 获取数据库信息
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();

            // 统计核心表行数
            long userCount = 0, projectCount = 0, apiCount = 0, requestLogCount = 0;
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_user")) {
                    if (rs.next()) userCount = rs.getLong(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_project")) {
                    if (rs.next()) projectCount = rs.getLong(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_mock_api")) {
                    if (rs.next()) apiCount = rs.getLong(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_request_log")) {
                    if (rs.next()) requestLogCount = rs.getLong(1);
                }
            } catch (Exception e) {
                // 表不存在等情况
                log.debug("统计表行数失败（可能表尚未创建）: {}", e.getMessage());
            }

            return Health.up()
                    .withDetail("type", dbProduct)
                    .withDetail("version", dbVersion)
                    .withDetail("users", userCount)
                    .withDetail("projects", projectCount)
                    .withDetail("apis", apiCount)
                    .withDetail("requestLogs", requestLogCount)
                    .build();

        } catch (Exception e) {
            log.error("数据库健康检查失败: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
