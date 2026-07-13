/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Mock 指标服务 — 为 Prometheus 暴露自定义 Mock 业务指标
 * <p>
 * 指标包括：
 * <ul>
 *   <li>mock_requests_total — Mock 请求总数（按方法/状态码/项目标签）</li>
 *   <li>mock_response_time_seconds — Mock 响应时间分布</li>
 *   <li>mock_active_apis_count — 活跃 API 数量</li>
 *   <li>mock_error_requests_total — Mock 错误请求数</li>
 * </ul>
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Service
public class MockMetricsService {

    private static final Logger log = LoggerFactory.getLogger(MockMetricsService.class);

    private final MeterRegistry meterRegistry;

    /** 按 method 维度统计的请求计数器 */
    private final ConcurrentMap<String, Counter> methodCounters = new ConcurrentHashMap<>();

    /** 按 statusCode 维度统计的响应计数器 */
    private final ConcurrentMap<String, Counter> statusCounters = new ConcurrentHashMap<>();

    /** 按 method 维度统计的响应时间 Timer */
    private final ConcurrentMap<String, Timer> methodTimers = new ConcurrentHashMap<>();

    /** Mock 总请求数 */
    private Counter totalRequestsCounter;

    /** Mock 错误请求数 */
    private Counter errorRequestsCounter;

    public MockMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        totalRequestsCounter = Counter.builder("mock_requests_total")
                .description("Mock 接口请求总数")
                .tag("application", "mock-server")
                .register(meterRegistry);

        errorRequestsCounter = Counter.builder("mock_error_requests_total")
                .description("Mock 接口错误请求数（状态码 >= 400）")
                .tag("application", "mock-server")
                .register(meterRegistry);

        log.info("Mock Prometheus 指标初始化完成");
    }

    /**
     * 记录一次 Mock 请求
     *
     * @param method        HTTP 方法（GET/POST/PUT/DELETE/PATCH）
     * @param statusCode    HTTP 状态码
     * @param responseTimeMs 响应时间（毫秒）
     * @param projectCode   项目编码
     */
    public void recordRequest(String method, int statusCode, long responseTimeMs, String projectCode) {
        // 总请求数 +1
        totalRequestsCounter.increment();

        // 按方法统计
        Counter mc = methodCounters.computeIfAbsent(method, m ->
                Counter.builder("mock_requests_by_method")
                        .description("按 HTTP 方法统计的 Mock 请求数")
                        .tag("method", m.toUpperCase())
                        .register(meterRegistry));
        mc.increment();

        // 按状态码统计
        String statusTag = getStatusTag(statusCode);
        Counter sc = statusCounters.computeIfAbsent(statusTag, s ->
                Counter.builder("mock_requests_by_status")
                        .description("按 HTTP 状态码统计的 Mock 响应数")
                        .tag("status", s)
                        .register(meterRegistry));
        sc.increment();

        // 错误请求
        if (statusCode >= 400) {
            errorRequestsCounter.increment();
        }

        // 响应时间
        Timer timer = methodTimers.computeIfAbsent(method, m ->
                Timer.builder("mock_response_time")
                        .description("Mock 接口响应时间分布")
                        .tag("method", m.toUpperCase())
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(meterRegistry));
        timer.record(responseTimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新活跃 API 数量
     */
    public void updateActiveApisCount(long count) {
        meterRegistry.gauge("mock_active_apis", java.util.Collections.singletonList(
                io.micrometer.core.instrument.Tag.of("application", "mock-server")), count);
    }

    /**
     * 将状态码归类为标签
     */
    private String getStatusTag(int statusCode) {
        if (statusCode < 200) return "1xx";
        if (statusCode < 300) return "2xx";
        if (statusCode < 400) return "3xx";
        if (statusCode < 500) return "4xx";
        return "5xx";
    }
}
