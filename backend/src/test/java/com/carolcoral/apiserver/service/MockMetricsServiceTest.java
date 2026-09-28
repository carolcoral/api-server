/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockMetricsService 单元测试：请求记录、状态码归类、错误计数与活跃 API 指标。
 */
class MockMetricsServiceTest {

    private SimpleMeterRegistry registry;
    private MockMetricsService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new MockMetricsService(registry);
        service.init();
    }

    @Test
    @DisplayName("初始化注册总请求与错误请求计数器")
    void initRegistersCounters() {
        assertNotNull(registry.find("mock_requests_total").counter());
        assertNotNull(registry.find("mock_error_requests_total").counter());
    }

    @Test
    @DisplayName("成功请求累加总数并按方法统计")
    void recordsSuccess() {
        service.recordRequest("GET", 200, 12, "proj");
        service.recordRequest("get", 201, 8, "proj");

        assertEquals(2.0, registry.find("mock_requests_total").counter().count(), 1e-9);
        assertEquals(2.0, registry.get("mock_requests_by_method").tag("method", "GET").counter().count(), 1e-9);
        assertEquals(0.0, registry.find("mock_error_requests_total").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("状态码按区间归类为 1xx/2xx/3xx/4xx/5xx")
    void statusBuckets() {
        service.recordRequest("GET", 100, 1, "p");
        service.recordRequest("GET", 204, 1, "p");
        service.recordRequest("GET", 302, 1, "p");
        service.recordRequest("GET", 404, 1, "p");
        service.recordRequest("GET", 503, 1, "p");

        assertEquals(1.0, registry.get("mock_requests_by_status").tag("status", "1xx").counter().count(), 1e-9);
        assertEquals(1.0, registry.get("mock_requests_by_status").tag("status", "2xx").counter().count(), 1e-9);
        assertEquals(1.0, registry.get("mock_requests_by_status").tag("status", "3xx").counter().count(), 1e-9);
        assertEquals(1.0, registry.get("mock_requests_by_status").tag("status", "4xx").counter().count(), 1e-9);
        assertEquals(1.0, registry.get("mock_requests_by_status").tag("status", "5xx").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("状态码 >= 400 计入错误请求")
    void countsErrors() {
        service.recordRequest("POST", 200, 1, "p");
        service.recordRequest("POST", 500, 1, "p");

        assertEquals(1.0, registry.find("mock_error_requests_total").counter().count(), 1e-9);
    }

    @Test
    @DisplayName("响应时间写入 Timer 并累计次数")
    void recordsTimer() {
        service.recordRequest("GET", 200, 25, "p");
        service.recordRequest("GET", 200, 75, "p");

        assertEquals(2L, registry.get("mock_response_time").tag("method", "GET").timer().count());
        assertEquals(100.0, registry.get("mock_response_time").tag("method", "GET").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS), 1e-6);
    }

    @Test
    @DisplayName("更新活跃 API 数量写入 gauge")
    void activeApisGauge() {
        service.updateActiveApisCount(42L);
        assertEquals(42.0, registry.get("mock_active_apis").gauge().value(), 1e-9);
    }
}
