/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.controller;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.dto.PageResult;
import com.carolcoral.mockserver.dto.SystemAnnouncementDTO;
import com.carolcoral.mockserver.entity.SystemAnnouncement;
import com.carolcoral.mockserver.service.SystemAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统公告控制器
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Tag(name = "系统公告管理", description = "系统公告管理相关接口")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/system-announcement")
public class SystemAnnouncementController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemAnnouncementController.class);

    /**
     * 构造器
     */
    public SystemAnnouncementController(SystemAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    private final SystemAnnouncementService announcementService;

    /**
     * 获取启用的公告
     *
     * @return 公告信息
     */
    @Operation(summary = "获取启用的公告", description = "获取当前启用的系统公告")
    @GetMapping("/enabled")
    public ApiResponse<SystemAnnouncementDTO> getEnabledAnnouncement() {
        log.info("获取启用的公告");
        return announcementService.getEnabledAnnouncement();
    }

    /**
     * 获取所有公告（分页）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param sortBy 排序字段
     * @param sortOrder 排序方向（asc/desc）
     * @return 公告列表（分页）
     */
    @Operation(summary = "获取所有公告（分页）", description = "获取所有系统公告（仅管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<PageResult<SystemAnnouncementDTO>> getAllAnnouncements(
            @Parameter(description = "页码", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "15") @RequestParam(defaultValue = "15") int size,
            @Parameter(description = "排序字段", example = "createTime") @RequestParam(defaultValue = "createTime") String sortBy,
            @Parameter(description = "排序方向", example = "desc") @RequestParam(defaultValue = "desc") String sortOrder) {
        log.info("获取公告列表: page={}, size={}, sortBy={}, sortOrder={}", page, size, sortBy, sortOrder);

        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return announcementService.getAllAnnouncements(pageable);
    }

    /**
     * 获取公告详情
     *
     * @param id 公告ID
     * @return 公告详情
     */
    @Operation(summary = "获取公告详情", description = "根据ID获取公告详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<SystemAnnouncementDTO> getAnnouncementById(
            @Parameter(description = "公告ID", example = "1") @PathVariable Long id) {
        log.info("获取公告详情: {}", id);
        return announcementService.getAnnouncementById(id);
    }

    /**
     * 创建公告
     *
     * @param announcementDTO 公告信息
     * @return 创建的公告
     */
    @Operation(summary = "创建公告", description = "创建新的系统公告（仅管理员）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<SystemAnnouncementDTO> createAnnouncement(
            @Parameter(description = "公告信息") @Valid @RequestBody SystemAnnouncementDTO announcementDTO) {
        log.info("创建公告: {}", announcementDTO.getTitle());
        return announcementService.createAnnouncement(announcementDTO);
    }

    /**
     * 更新公告
     *
     * @param id 公告ID
     * @param announcementDTO 公告信息
     * @return 更新的公告
     */
    @Operation(summary = "更新公告", description = "更新系统公告（仅管理员）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<SystemAnnouncementDTO> updateAnnouncement(
            @Parameter(description = "公告ID", example = "1") @PathVariable Long id,
            @Parameter(description = "公告信息") @Valid @RequestBody SystemAnnouncementDTO announcementDTO) {
        log.info("更新公告: {}", id);
        return announcementService.updateAnnouncement(id, announcementDTO);
    }

    /**
     * 删除公告
     *
     * @param id 公告ID
     * @return 删除结果
     */
    @Operation(summary = "删除公告", description = "删除系统公告（仅管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<Void> deleteAnnouncement(
            @Parameter(description = "公告ID", example = "1") @PathVariable Long id) {
        log.info("删除公告: {}", id);
        return announcementService.deleteAnnouncement(id);
    }

    /**
     * 启用/禁用公告
     *
     * @param id 公告ID
     * @param enabled 是否启用
     * @return 操作结果
     */
    @Operation(summary = "启用/禁用公告", description = "切换公告的启用状态（仅管理员）")
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:announcement')")
    public ApiResponse<SystemAnnouncementDTO> toggleAnnouncementStatus(
            @Parameter(description = "公告ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否启用") @RequestBody Map<String, Boolean> enabledMap) {
        Boolean enabled = enabledMap.get("enabled");
        log.info("切换公告状态: {}, enabled: {}", id, enabled);
        return announcementService.toggleAnnouncementStatus(id, enabled);
    }
}
