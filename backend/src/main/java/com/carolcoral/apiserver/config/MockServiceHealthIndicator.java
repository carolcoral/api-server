/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Mock 服务健康检查 — 检查 Mock API 和项目状态
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Component("mockServiceHealth")
public class MockServiceHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MockServiceHealthIndicator.class);

    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;

    public MockServiceHealthIndicator(ProjectRepository projectRepository,
                                       MockApiRepository mockApiRepository) {
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
    }

    @Override
    @Operation(summary = "Mock 服务健康检查")
    public Health health() {
        try {
            long totalProjects = projectRepository.count();
            long totalApis = mockApiRepository.count();
            long enabledProjects = projectRepository.findByEnabled(true).size();
            long enabledApis = mockApiRepository.findByEnabled(true).size();

            if (totalProjects == 0 && totalApis == 0) {
                return Health.up()
                        .withDetail("status", "就绪（暂无数据）")
                        .withDetail("totalProjects", 0)
                        .withDetail("totalApis", 0)
                        .withDetail("enabledProjects", 0)
                        .withDetail("enabledApis", 0)
                        .build();
            }

            return Health.up()
                    .withDetail("totalProjects", totalProjects)
                    .withDetail("totalApis", totalApis)
                    .withDetail("enabledProjects", enabledProjects)
                    .withDetail("enabledApis", enabledApis)
                    .build();

        } catch (Exception e) {
            log.error("Mock 服务健康检查失败: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
