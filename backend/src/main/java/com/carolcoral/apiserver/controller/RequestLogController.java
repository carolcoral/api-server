/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.util.DatabaseDialectProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求日志控制器 - 提供 Mock 请求日志查询与调试面板数据
 *
 * @author carolcoral
 */
@Tag(name = "请求日志", description = "Mock 请求日志查询与调试面板相关接口，需 statistics:view 或 debug-panel:view 权限")
@RestController
@RequestMapping("/api/request-logs")
@PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('statistics:view', 'debug-panel:view')")
public class RequestLogController {
    private static final Logger log = LoggerFactory.getLogger(RequestLogController.class);

    private final EntityManager entityManager;
    private final DatabaseDialectProvider dialect;

    public RequestLogController(EntityManager entityManager, DatabaseDialectProvider dialect) {
        this.entityManager = entityManager;
        this.dialect = dialect;
    }

    private void setTimeParameter(Query query, String paramName, LocalDateTime dateTime) {
        if (dialect.isNativeDateTimeColumn()) {
            query.setParameter(paramName, dateTime);
        } else {
            long epochMillis = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            query.setParameter(paramName, epochMillis);
        }
    }

    /**
     * 分页查询请求日志列表
     */
    @Operation(summary = "查询请求日志列表", description = "分页查询 Mock 请求日志，支持按项目、路径、方法、状态码筛选")
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getRequestLogs(
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "项目ID筛选") @RequestParam(required = false) Long projectId,
            @Parameter(description = "请求路径模糊搜索") @RequestParam(required = false) String path,
            @Parameter(description = "请求方法筛选") @RequestParam(required = false) String method,
            @Parameter(description = "状态码筛选") @RequestParam(required = false) Integer statusCode,
            @Parameter(description = "请求IP筛选") @RequestParam(required = false) String requestIp,
            @Parameter(description = "开始时间 (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间 (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String endTime) {

        try {
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM t_request_log r WHERE 1=1");
            StringBuilder dataSql = new StringBuilder(
                    "SELECT r.id, r.mock_api_id, r.project_id, r.method, r.path, r.request_time, " +
                    "r.status_code, r.response_time, r.request_ip, r.user_id, p.name as project_name, " +
                    "a.name as api_name " +
                    "FROM t_request_log r " +
                    "LEFT JOIN t_project p ON r.project_id = p.id " +
                    "LEFT JOIN t_mock_api a ON r.mock_api_id = a.id " +
                    "WHERE 1=1");

            Map<String, Object> params = new HashMap<>();

            if (projectId != null) {
                countSql.append(" AND r.project_id = :projectId");
                dataSql.append(" AND r.project_id = :projectId");
                params.put("projectId", projectId);
            }
            if (path != null && !path.isEmpty()) {
                countSql.append(" AND r.path LIKE :path");
                dataSql.append(" AND r.path LIKE :path");
                params.put("path", "%" + path + "%");
            }
            if (method != null && !method.isEmpty()) {
                countSql.append(" AND r.method = :method");
                dataSql.append(" AND r.method = :method");
                params.put("method", method.toUpperCase());
            }
            if (statusCode != null) {
                countSql.append(" AND r.status_code = :statusCode");
                dataSql.append(" AND r.status_code = :statusCode");
                params.put("statusCode", statusCode);
            }
            if (requestIp != null && !requestIp.isEmpty()) {
                countSql.append(" AND r.request_ip LIKE :requestIp");
                dataSql.append(" AND r.request_ip LIKE :requestIp");
                params.put("requestIp", "%" + requestIp + "%");
            }
            if (startTime != null && !startTime.isEmpty()) {
                countSql.append(" AND r.request_time >= :startTime");
                dataSql.append(" AND r.request_time >= :startTime");
                params.put("startTime", startTime);
            }
            if (endTime != null && !endTime.isEmpty()) {
                countSql.append(" AND r.request_time <= :endTime");
                dataSql.append(" AND r.request_time <= :endTime");
                params.put("endTime", endTime);
            }

            dataSql.append(" ORDER BY r.id DESC");

            // 查询总数
            Query countQuery = entityManager.createNativeQuery(countSql.toString());
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() instanceof LocalDateTime) {
                    setTimeParameter(countQuery, entry.getKey(), (LocalDateTime) entry.getValue());
                } else if (entry.getKey().equals("startTime") || entry.getKey().equals("endTime")) {
                    // 字符串时间格式
                    String timeStr = (String) entry.getValue();
                    LocalDateTime dt = LocalDateTime.parse(timeStr.replace(" ", "T"));
                    setTimeParameter(countQuery, entry.getKey(), dt);
                } else {
                    countQuery.setParameter(entry.getKey(), entry.getValue());
                }
            }
            long total = ((Number) countQuery.getSingleResult()).longValue();

            // 分页查询数据
            Query dataQuery = entityManager.createNativeQuery(dataSql.toString());
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getKey().equals("startTime") || entry.getKey().equals("endTime")) {
                    String timeStr = (String) entry.getValue();
                    LocalDateTime dt = LocalDateTime.parse(timeStr.replace(" ", "T"));
                    setTimeParameter(dataQuery, entry.getKey(), dt);
                } else {
                    dataQuery.setParameter(entry.getKey(), entry.getValue());
                }
            }
            dataQuery.setFirstResult((page - 1) * size);
            dataQuery.setMaxResults(size);

            List<Object[]> rows = dataQuery.getResultList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row[0] != null ? ((Number) row[0]).longValue() : null);
                item.put("mockApiId", row[1] != null ? ((Number) row[1]).longValue() : null);
                item.put("projectId", row[2] != null ? ((Number) row[2]).longValue() : null);
                item.put("method", row[3]);
                item.put("path", row[4]);
                item.put("requestTime", row[5] != null ? String.valueOf(row[5]) : null);
                item.put("statusCode", row[6]);
                item.put("responseTime", row[7] != null ? ((Number) row[7]).longValue() : null);
                item.put("requestIp", row[8]);
                item.put("userId", row[9] != null ? ((Number) row[9]).longValue() : null);
                item.put("projectName", row[10]);
                item.put("apiName", row[11]);
                list.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("list", list);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询请求日志列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询请求日志列表失败");
        }
    }

    /**
     * 获取响应延迟分布统计
     */
    @Operation(summary = "获取响应延迟分布", description = "统计请求响应时间的分布情况，用于调试面板延迟分析")
    @GetMapping("/delay-distribution")
    public ApiResponse<Map<String, Object>> getDelayDistribution(
            @Parameter(description = "统计最近N分钟，默认60") @RequestParam(defaultValue = "60") int minutes) {

        try {
            if (minutes > 1440) minutes = 1440;

            LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);

            // 按延迟区间统计：0-50ms, 50-100ms, 100-200ms, 200-500ms, 500-1000ms, 1000ms+
            String[] ranges = {"0-50ms", "50-100ms", "100-200ms", "200-500ms", "500-1000ms", "1000ms+"};
            int[][] rangeBounds = {{0, 50}, {50, 100}, {100, 200}, {200, 500}, {500, 1000}, {1000, Integer.MAX_VALUE}};

            List<String> labels = new ArrayList<>();
            List<Long> values = new ArrayList<>();
            long totalWithDelay = 0;

            for (int i = 0; i < ranges.length; i++) {
                int min = rangeBounds[i][0];
                int max = rangeBounds[i][1];

                String sql;
                if (max == Integer.MAX_VALUE) {
                    sql = "SELECT COUNT(*) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL AND r.response_time >= :minDelay";
                } else {
                    sql = "SELECT COUNT(*) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL AND r.response_time >= :minDelay AND r.response_time < :maxDelay";
                }

                Query query = entityManager.createNativeQuery(sql);
                setTimeParameter(query, "startTime", startTime);
                query.setParameter("minDelay", min);
                if (max != Integer.MAX_VALUE) {
                    query.setParameter("maxDelay", max);
                }

                long count = ((Number) query.getSingleResult()).longValue();
                labels.add(ranges[i]);
                values.add(count);
                totalWithDelay += count;
            }

            // 平均延迟
            String avgSql = "SELECT AVG(r.response_time) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL";
            Query avgQuery = entityManager.createNativeQuery(avgSql);
            setTimeParameter(avgQuery, "startTime", startTime);
            Object avgResult = avgQuery.getSingleResult();
            double avgDelay = avgResult != null ? ((Number) avgResult).doubleValue() : 0.0;

            // P50/P90/P95/P99 延迟
            String countSql = "SELECT COUNT(*) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL";
            Query cntQuery = entityManager.createNativeQuery(countSql);
            setTimeParameter(cntQuery, "startTime", startTime);
            long total = ((Number) cntQuery.getSingleResult()).longValue();

            long p50 = 0, p90 = 0, p95 = 0, p99 = 0;
            if (total > 0) {
                String percentileSql = "SELECT r.response_time FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL ORDER BY r.response_time ASC";
                Query pQuery = entityManager.createNativeQuery(percentileSql);
                setTimeParameter(pQuery, "startTime", startTime);
                // 只取需要的行数
                int p99Idx = (int) Math.ceil(total * 0.99) - 1;
                pQuery.setMaxResults(p99Idx + 1);
                @SuppressWarnings("unchecked")
                List<Number> allDelays = pQuery.getResultList();
                if (!allDelays.isEmpty()) {
                    int p50Idx = Math.min((int) Math.ceil(total * 0.50) - 1, allDelays.size() - 1);
                    int p90Idx = Math.min((int) Math.ceil(total * 0.90) - 1, allDelays.size() - 1);
                    int p95Idx = Math.min((int) Math.ceil(total * 0.95) - 1, allDelays.size() - 1);
                    p99Idx = Math.min(p99Idx, allDelays.size() - 1);
                    p50 = allDelays.get(Math.max(0, p50Idx)).longValue();
                    p90 = allDelays.get(Math.max(0, p90Idx)).longValue();
                    p95 = allDelays.get(Math.max(0, p95Idx)).longValue();
                    p99 = allDelays.get(Math.max(0, p99Idx)).longValue();
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("labels", labels);
            result.put("values", values);
            result.put("totalWithDelay", totalWithDelay);
            result.put("avgDelay", Math.round(avgDelay * 100.0) / 100.0);
            result.put("p50", p50);
            result.put("p90", p90);
            result.put("p95", p95);
            result.put("p99", p99);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取响应延迟分布失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取响应延迟分布失败");
        }
    }

    /**
     * 获取实时请求概览（用于调试面板顶部卡片）
     */
    @Operation(summary = "获取请求概览", description = "获取近N分钟的请求概览数据：总请求数、平均延迟、错误率等")
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview(
            @Parameter(description = "统计最近N分钟，默认5") @RequestParam(defaultValue = "5") int minutes) {

        try {
            if (minutes > 1440) minutes = 1440;

            LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);

            // 总请求数
            String totalSql = "SELECT COUNT(*) FROM t_request_log r WHERE r.request_time >= :startTime";
            Query totalQuery = entityManager.createNativeQuery(totalSql);
            setTimeParameter(totalQuery, "startTime", startTime);
            long totalRequests = ((Number) totalQuery.getSingleResult()).longValue();

            // 错误请求数（状态码 >= 400）
            String errorSql = "SELECT COUNT(*) FROM t_request_log r WHERE r.request_time >= :startTime AND r.status_code >= 400";
            Query errorQuery = entityManager.createNativeQuery(errorSql);
            setTimeParameter(errorQuery, "startTime", startTime);
            long errorRequests = ((Number) errorQuery.getSingleResult()).longValue();

            // 平均延迟
            String avgSql = "SELECT AVG(r.response_time) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL";
            Query avgQuery = entityManager.createNativeQuery(avgSql);
            setTimeParameter(avgQuery, "startTime", startTime);
            Object avgResult = avgQuery.getSingleResult();
            double avgDelay = avgResult != null ? ((Number) avgResult).doubleValue() : 0.0;

            // 最大延迟
            String maxSql = "SELECT MAX(r.response_time) FROM t_request_log r WHERE r.request_time >= :startTime AND r.response_time IS NOT NULL";
            Query maxQuery = entityManager.createNativeQuery(maxSql);
            setTimeParameter(maxQuery, "startTime", startTime);
            Object maxResult = maxQuery.getSingleResult();
            long maxDelay = maxResult != null ? ((Number) maxResult).longValue() : 0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalRequests", totalRequests);
            result.put("errorRequests", errorRequests);
            result.put("errorRate", totalRequests > 0 ? Math.round((double) errorRequests / totalRequests * 10000.0) / 100.0 : 0.0);
            result.put("avgDelay", Math.round(avgDelay * 100.0) / 100.0);
            result.put("maxDelay", maxDelay);

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取请求概览失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取请求概览失败");
        }
    }
}
