/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.service.ProjectExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 项目导入导出控制器
 * 支持 Swagger/OpenAPI 格式导出 和 项目级 JSON 格式导入导出
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-07-01
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectExportController {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportController.class);

    private final ProjectExportService projectExportService;

    public ProjectExportController(ProjectExportService projectExportService) {
        this.projectExportService = projectExportService;
    }

    /**
     * 导出项目 API 为 Swagger/OpenAPI 格式
     */
    @GetMapping("/{projectId}/export-swagger")
    public ResponseEntity<byte[]> exportSwagger(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "3.0") String version) {

        String swaggerJson = projectExportService.exportSwagger(projectId, version);

        String filename = "swagger-" + projectId + ".json";
        byte[] bytes = swaggerJson.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", URLEncoder.encode(filename, StandardCharsets.UTF_8));

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 导出项目完整数据为 JSON 格式（包含接口名称、地址、请求参数、响应报文等）
     */
    @GetMapping("/{projectId}/export-data")
    public ResponseEntity<byte[]> exportProjectData(@PathVariable Long projectId) {

        String exportJson = projectExportService.exportProjectData(projectId);

        String filename = "mock-project-" + projectId + ".json";
        byte[] bytes = exportJson.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", URLEncoder.encode(filename, StandardCharsets.UTF_8));

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 从 JSON 文件导入项目数据（需指定已有项目ID）
     */
    @PostMapping("/{projectId}/import-data")
    public ApiResponse<ProjectExportService.ImportResult> importProjectData(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "merge") String mode,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 1L;
        }

        if (file.isEmpty()) {
            return ApiResponse.error("上传文件为空");
        }

        try {
            String jsonData = new String(file.getBytes(), StandardCharsets.UTF_8);
            ProjectExportService.ImportResult result = projectExportService.importProjectData(
                    projectId, jsonData, userId, mode);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("导入项目数据失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 文件导入项目数据（自动创建项目）
     * 解析 JSON 中的 project.code，若项目不存在则自动创建，存在则按模式导入
     */
    @PostMapping("/import-data")
    public ApiResponse<ProjectExportService.ImportResult> importProjectDataAuto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "merge") String mode,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 1L;
        }

        if (file.isEmpty()) {
            return ApiResponse.error("上传文件为空");
        }

        try {
            String jsonData = new String(file.getBytes(), StandardCharsets.UTF_8);
            ProjectExportService.ImportResult result = projectExportService.importProjectDataAuto(
                    jsonData, userId, mode);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("导入项目数据失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }
}
