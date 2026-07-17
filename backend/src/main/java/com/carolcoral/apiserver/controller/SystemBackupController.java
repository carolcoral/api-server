/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.service.SystemBackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 系统备份与恢复控制器 — 一键备份/恢复 Mock 配置
 *
 * @author carolcoral
 * @since 2.4.0
 */
@Tag(name = "系统备份与恢复", description = "一键备份/恢复全量 Mock 配置数据，需 ops:view / ops:backup / ops:restore 权限")
@RestController
@RequestMapping("/api/system")
public class SystemBackupController {

    private static final Logger log = LoggerFactory.getLogger(SystemBackupController.class);

    private final SystemBackupService systemBackupService;

    public SystemBackupController(SystemBackupService systemBackupService) {
        this.systemBackupService = systemBackupService;
    }

    /**
     * 获取备份信息（预览各表行数）
     */
    @Operation(summary = "获取备份信息", description = "获取当前数据库中各表的行数统计")
    @GetMapping("/backup/info")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ops:view')")
    public ApiResponse<Map<String, Object>> getBackupInfo() {
        try {
            return ApiResponse.success(systemBackupService.getBackupInfo());
        } catch (Exception e) {
            log.error("获取备份信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取备份信息失败");
        }
    }

    /**
     * 一键备份 — 导出全量配置 JSON
     */
    @Operation(summary = "一键备份", description = "导出全量 Mock 配置数据为 JSON 文件")
    @GetMapping("/backup/export")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ops:backup')")
    public ResponseEntity<byte[]> exportBackup() {
        try {
            SystemBackupService.BackupResult result = systemBackupService.createBackupWithInfo();
            if (!result.isSuccess()) {
                return ResponseEntity.internalServerError().body("备份失败".getBytes(StandardCharsets.UTF_8));
            }

            String json = systemBackupService.createBackup();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment",
                    URLEncoder.encode(result.getFilename(), StandardCharsets.UTF_8));

            log.info("系统备份导出成功: {}, 大小: {} bytes", result.getFilename(), bytes.length);
            return ResponseEntity.ok().headers(headers).body(bytes);

        } catch (Exception e) {
            log.error("备份导出失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 一键恢复 — 从 JSON 文件恢复全量配置
     */
    @Operation(summary = "一键恢复", description = "从备份 JSON 文件恢复全量 Mock 配置数据")
    @PostMapping("/backup/restore")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ops:restore')")
    public ApiResponse<SystemBackupService.RestoreResult> restoreBackup(
            @Parameter(description = "备份 JSON 文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "恢复模式: merge 合并 / replace 替换") @RequestParam(defaultValue = "merge") String mode) {

        if (file.isEmpty()) {
            return ApiResponse.error("上传文件为空");
        }

        try {
            String jsonData = new String(file.getBytes(), StandardCharsets.UTF_8);
            SystemBackupService.RestoreResult result = systemBackupService.restoreFromBackup(jsonData, mode);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("恢复备份失败: {}", e.getMessage(), e);
            return ApiResponse.error("恢复备份失败: " + e.getMessage());
        }
    }
}
