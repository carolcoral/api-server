/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.PageResult;
import com.carolcoral.apiserver.dto.SystemAnnouncementDTO;
import com.carolcoral.apiserver.entity.SystemAnnouncement;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.SystemAnnouncementRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统公告服务类
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Service
public class SystemAnnouncementService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemAnnouncementService.class);

    private final SystemAnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    /**
     * 构造器
     */
    public SystemAnnouncementService(SystemAnnouncementRepository announcementRepository, UserRepository userRepository) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
    }

    /**
     * 获取启用的公告
     *
     * @return 公告信息
     */
    public ApiResponse<SystemAnnouncementDTO> getEnabledAnnouncement() {
        try {
            return announcementRepository.findEnabledAnnouncement()
                    .map(announcement -> ApiResponse.success(convertToDTO(announcement)))
                    .orElse(ApiResponse.success(null));
        } catch (Exception e) {
            log.error("获取启用的公告失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取公告失败");
        }
    }

    /**
     * 获取所有公告（分页）
     *
     * @param pageable 分页参数
     * @return 公告列表（分页）
     */
    public ApiResponse<PageResult<SystemAnnouncementDTO>> getAllAnnouncements(Pageable pageable) {
        try {
            Page<SystemAnnouncement> page = announcementRepository.findAll(pageable);
            List<SystemAnnouncementDTO> dtos = page.getContent().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            PageResult<SystemAnnouncementDTO> result = PageResult.<SystemAnnouncementDTO>builder()
                    .content(dtos)
                    .page(page.getNumber())
                    .size(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .last(page.isLast())
                    .first(page.isFirst())
                    .build();

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取公告列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取公告列表失败");
        }
    }

    /**
     * 获取公告详情
     *
     * @param id 公告ID
     * @return 公告详情
     */
    public ApiResponse<SystemAnnouncementDTO> getAnnouncementById(Long id) {
        try {
            SystemAnnouncement announcement = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));
            return ApiResponse.success(convertToDTO(announcement));
        } catch (Exception e) {
            log.error("获取公告详情失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取公告详情失败");
        }
    }

    /**
     * 创建公告
     *
     * @param dto 公告信息
     * @return 创建的公告
     */
    @Transactional
    public ApiResponse<SystemAnnouncementDTO> createAnnouncement(SystemAnnouncementDTO dto) {
        try {
            Long userId = getCurrentUserId();

            // 如果已经有启用的公告，先禁用它
            if (dto.getEnabled() != null && dto.getEnabled()) {
                announcementRepository.findEnabledAnnouncement().ifPresent(announcement -> {
                    announcement.setEnabled(false);
                    announcementRepository.save(announcement);
                });
            }

            SystemAnnouncement announcement = SystemAnnouncement.builder()
                    .title(dto.getTitle())
                    .content(dto.getContent())
                    .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                    .priority(dto.getPriority() != null ? dto.getPriority() : "NORMAL")
                    .createUserId(userId)
                    .build();

            SystemAnnouncement saved = announcementRepository.save(announcement);
            log.info("创建公告成功: {}", saved.getId());
            return ApiResponse.success(convertToDTO(saved));
        } catch (Exception e) {
            log.error("创建公告失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建公告失败");
        }
    }

    /**
     * 更新公告
     *
     * @param id 公告ID
     * @param dto 公告信息
     * @return 更新的公告
     */
    @Transactional
    public ApiResponse<SystemAnnouncementDTO> updateAnnouncement(Long id, SystemAnnouncementDTO dto) {
        try {
            SystemAnnouncement announcement = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));

            // 如果要启用该公告，先禁用其他启用的公告
            if (dto.getEnabled() != null && dto.getEnabled() && !announcement.getEnabled()) {
                announcementRepository.findEnabledAnnouncement().ifPresent(enabled -> {
                    if (!enabled.getId().equals(id)) {
                        enabled.setEnabled(false);
                        announcementRepository.save(enabled);
                    }
                });
            }

            announcement.setTitle(dto.getTitle());
            announcement.setContent(dto.getContent());
            if (dto.getEnabled() != null) {
                announcement.setEnabled(dto.getEnabled());
            }
            if (dto.getPriority() != null) {
                announcement.setPriority(dto.getPriority());
            }

            SystemAnnouncement updated = announcementRepository.save(announcement);
            log.info("更新公告成功: {}", updated.getId());
            return ApiResponse.success(convertToDTO(updated));
        } catch (Exception e) {
            log.error("更新公告失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新公告失败");
        }
    }

    /**
     * 删除公告
     *
     * @param id 公告ID
     * @return 删除结果
     */
    @Transactional
    public ApiResponse<Void> deleteAnnouncement(Long id) {
        try {
            if (!announcementRepository.existsById(id)) {
                return ApiResponse.error("公告不存在");
            }
            announcementRepository.deleteById(id);
            log.info("删除公告成功: {}", id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除公告失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除公告失败");
        }
    }

    /**
     * 启用/禁用公告
     *
     * @param id 公告ID
     * @param enabled 是否启用
     * @return 操作结果
     */
    @Transactional
    public ApiResponse<SystemAnnouncementDTO> toggleAnnouncementStatus(Long id, Boolean enabled) {
        try {
            SystemAnnouncement announcement = announcementRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("公告不存在"));

            // 如果要启用该公告，先禁用其他启用的公告
            if (enabled && !announcement.getEnabled()) {
                announcementRepository.findEnabledAnnouncement().ifPresent(enabledAnnouncement -> {
                    if (!enabledAnnouncement.getId().equals(id)) {
                        enabledAnnouncement.setEnabled(false);
                        announcementRepository.save(enabledAnnouncement);
                    }
                });
            }

            announcement.setEnabled(enabled);
            SystemAnnouncement updated = announcementRepository.save(announcement);
            log.info("切换公告状态成功: {}, enabled: {}", id, enabled);
            return ApiResponse.success(convertToDTO(updated));
        } catch (Exception e) {
            log.error("切换公告状态失败: {}", e.getMessage(), e);
            return ApiResponse.error("操作失败");
        }
    }

    /**
     * 转换为DTO
     *
     * @param entity 实体
     * @return DTO
     */
    private SystemAnnouncementDTO convertToDTO(SystemAnnouncement entity) {
        SystemAnnouncementDTO dto = new SystemAnnouncementDTO();
        BeanUtils.copyProperties(entity, dto);

        // 设置创建人信息
        if (entity.getCreateUserId() != null) {
            try {
                User user = userRepository.findById(entity.getCreateUserId()).orElse(null);
                if (user != null) {
                    dto.setCreateBy(user.getUsername());
                }
            } catch (Exception e) {
                log.warn("获取创建人信息失败: {}", e.getMessage());
            }
        }

        return dto;
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return 1L; // TODO: 从认证信息中获取真实用户ID
        }
        return 1L;
    }
}
