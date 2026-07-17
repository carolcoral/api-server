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

import java.io.File;

/**
 * 磁盘健康检查 — 检查工作目录磁盘空间
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Component("diskSpace")
public class DiskSpaceHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DiskSpaceHealthIndicator.class);
    private static final long THRESHOLD_WARN_BYTES = 1024 * 1024 * 1024;     // 1 GB
    private static final long THRESHOLD_ERROR_BYTES = 512 * 1024 * 1024;     // 512 MB

    @Override
    @Operation(summary = "磁盘空间健康检查")
    public Health health() {
        try {
            File currentDir = new File(".").getAbsoluteFile();
            long freeSpace = currentDir.getFreeSpace();
            long totalSpace = currentDir.getTotalSpace();
            long usableSpace = currentDir.getUsableSpace();

            long freeMB = freeSpace / (1024 * 1024);
            long totalMB = totalSpace / (1024 * 1024);
            double usedPercent = totalSpace > 0 ? ((double)(totalSpace - freeSpace) / totalSpace * 100) : 0;

            Health.Builder builder;

            if (usableSpace < THRESHOLD_ERROR_BYTES) {
                builder = Health.down();
            } else if (usableSpace < THRESHOLD_WARN_BYTES) {
                builder = Health.status("WARN");
            } else {
                builder = Health.up();
            }

            return builder
                    .withDetail("total", totalMB + " MB")
                    .withDetail("free", freeMB + " MB")
                    .withDetail("usedPercent", String.format("%.1f%%", usedPercent))
                    .withDetail("path", currentDir.getAbsolutePath())
                    .build();

        } catch (Exception e) {
            log.warn("磁盘空间检查失败: {}", e.getMessage());
            return Health.unknown()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
