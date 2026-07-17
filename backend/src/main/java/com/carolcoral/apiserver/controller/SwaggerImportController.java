/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.service.SwaggerImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class SwaggerImportController {

    private static final Logger log = LoggerFactory.getLogger(SwaggerImportController.class);

    private final SwaggerImportService swaggerImportService;

    public SwaggerImportController(SwaggerImportService swaggerImportService) {
        this.swaggerImportService = swaggerImportService;
    }

    /**
     * 从上传的 Swagger JSON 文件导入接口
     */
    @Operation(summary = "导入Swagger文件", description = "从上传的 Swagger JSON 文件导入接口")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:import_swagger')")
    @PostMapping("/{projectId}/import-swagger-file")
    public ApiResponse<SwaggerImportService.ImportResult> importSwaggerFile(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 1L; // fallback
        }

        if (file.isEmpty()) {
            return ApiResponse.error("上传文件为空");
        }

        try {
            SwaggerImportService.ImportResult result = swaggerImportService.importFromStream(
                    file.getInputStream(), projectId, userId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("导入 Swagger 文件失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 从 Swagger URL 导入接口
     */
    @Operation(summary = "从URL导入Swagger", description = "从 Swagger URL 导入接口")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:import_swagger')")
    @PostMapping("/{projectId}/import-swagger-url")
    public ApiResponse<SwaggerImportService.ImportResult> importSwaggerUrl(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 1L;
        }

        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error("Swagger 地址不能为空");
        }

        try {
            SwaggerImportService.ImportResult result = swaggerImportService.importFromUrl(url, projectId, userId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("从 URL 导入 Swagger 失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 解决导入冲突：用新导入接口信息覆盖已有接口
     */
    @PostMapping("/{projectId}/import-conflicts/resolve")
    public ApiResponse<Map<String, Object>> resolveImportConflicts(
            @PathVariable Long projectId,
            @RequestBody List<SwaggerImportService.ResolveConflictRequest> requests,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 1L;
        }

        if (requests == null || requests.isEmpty()) {
            return ApiResponse.error("冲突列表不能为空");
        }

        try {
            int resolved = swaggerImportService.resolveConflicts(projectId, requests);
            log.info("冲突解决完成: projectId={}, userId={}, resolved={}", projectId, userId, resolved);
            return ApiResponse.success(Map.of("resolved", resolved));
        } catch (Exception e) {
            log.error("解决导入冲突失败", e);
            return ApiResponse.error("冲突解决失败: " + e.getMessage());
        }
    }
}
