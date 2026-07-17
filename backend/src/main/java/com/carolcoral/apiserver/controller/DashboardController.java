/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.SystemAnnouncementDTO;
import com.carolcoral.apiserver.repository.MockApiRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import com.carolcoral.apiserver.repository.RequestLogRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.service.RequestLogService;
import com.carolcoral.apiserver.service.SystemAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 仪表盘控制器 - 提供首页统计数据
 *
 * @author carolcoral
 */
@Tag(name = "仪表盘管理", description = "首页统计数据相关接口")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DashboardController.class);

    /**
     * 构造器
     */
    public DashboardController(ProjectRepository projectRepository,
        MockApiRepository mockApiRepository,
        UserRepository userRepository,
        RequestLogRepository requestLogRepository,
        RequestLogService requestLogService,
        SystemAnnouncementService announcementService) {
        this.projectRepository = projectRepository;
        this.mockApiRepository = mockApiRepository;
        this.userRepository = userRepository;
        this.requestLogRepository = requestLogRepository;
        this.requestLogService = requestLogService;
        this.announcementService = announcementService;
    }

    private final ProjectRepository projectRepository;
    private final MockApiRepository mockApiRepository;
    private final UserRepository userRepository;
    private final RequestLogRepository requestLogRepository;
    private final RequestLogService requestLogService;
    private final SystemAnnouncementService announcementService;

    /**
     * 获取仪表盘统计数据
     *
     * @return 统计数据
     */
    @Operation(summary = "获取仪表盘统计数据", description = "获取首页项目、接口、用户、今日请求、历史请求等统计数据")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('dashboard:view')")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getDashboardStats(
            @Parameter(description = "是否包含今日请求数", example = "false") @RequestParam(required = false, defaultValue = "false") boolean includeTodayRequests) {
        try {
            // 添加调试日志
            org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            log.info("获取仪表盘统计数据, 认证状态: {}, 用户: {}", 
                    auth != null ? auth.isAuthenticated() : false,
                    auth != null ? auth.getName() : "anonymous");
            
            if (auth == null || !auth.isAuthenticated()) {
                log.warn("Dashboard stats 接口未认证访问");
            } else {
                log.info("Dashboard stats 接口已认证: {}", auth.getName());
            }

            Map<String, Object> stats = new HashMap<>();

            // 项目总数
            long projectCount = projectRepository.count();
            stats.put("projectCount", projectCount);

            // 接口总数
            long apiCount = mockApiRepository.count();
            stats.put("apiCount", apiCount);

            // 用户总数
            long userCount = userRepository.count();
            stats.put("userCount", userCount);

            // 今日请求数（统计当天内所有项目的所有自定义接口的请求次数）
            long requestCount = requestLogService.getTodayRequestCount();
            stats.put("requestCount", requestCount);

            // 历史请求数（统计到目前为止所有项目所有接口的总请求次数）
            long totalRequestCount = requestLogRepository.count();
            stats.put("totalRequestCount", totalRequestCount);

            log.info("仪表盘统计数据获取成功: 项目={}, 接口={}, 用户={}, 今日请求={}, 历史请求={}",
                    projectCount, apiCount, userCount, requestCount, totalRequestCount);

            return ApiResponse.success(stats);

        } catch (Exception e) {
            log.error("获取仪表盘统计数据失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取统计数据失败，请稍后重试");
        }
    }

    /**
     * 获取系统公告
     *
     * @return 系统公告
     */
    @Operation(summary = "获取系统公告", description = "获取当前启用的系统公告")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/announcement")
    public ApiResponse<SystemAnnouncementDTO> getAnnouncement() {
        try {
            return announcementService.getEnabledAnnouncement();
        } catch (Exception e) {
            log.error("获取系统公告失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取系统公告失败");
        }
    }
}
