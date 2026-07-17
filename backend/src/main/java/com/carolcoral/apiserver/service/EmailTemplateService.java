/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.PageResult;
import com.carolcoral.apiserver.entity.EmailTemplate;
import com.carolcoral.apiserver.repository.EmailTemplateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 邮件模板服务
 * 负责邮件模板的 CRUD 操作，保证每种类型最多只有一个启用模板
 *
 * @author carolcoral
 * @version 1.1
 * @since 2026-06-16
 */
@Tag(name = "邮件模板服务", description = "邮件模板业务逻辑处理")
@Service
public class EmailTemplateService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailTemplateService.class);

    private final EmailTemplateRepository emailTemplateRepository;

    public EmailTemplateService(EmailTemplateRepository emailTemplateRepository) {
        this.emailTemplateRepository = emailTemplateRepository;
    }

    /**
     * 获取所有邮件模板
     */
    @Operation(summary = "获取所有邮件模板")
    public ApiResponse<List<EmailTemplate>> getAllTemplates() {
        try {
            List<EmailTemplate> templates = emailTemplateRepository.findAll();
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("获取邮件模板列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取模板列表失败");
        }
    }

    /**
     * 分页搜索邮件模板
     */
    @Operation(summary = "分页搜索邮件模板")
    public ApiResponse<PageResult<EmailTemplate>> searchTemplates(String name, String type,
            Boolean enabled, int page, int size) {
        try {
            Specification<EmailTemplate> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (name != null && !name.isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
                }
                if (type != null && !type.isBlank()) {
                    predicates.add(cb.equal(root.get("type"), type));
                }
                if (enabled != null) {
                    predicates.add(cb.equal(root.get("enabled"), enabled));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<EmailTemplate> result = emailTemplateRepository.findAll(spec, pageRequest);
            PageResult<EmailTemplate> pageResult = new PageResult<>();
            pageResult.setContent(result.getContent());
            pageResult.setPage(result.getNumber());
            pageResult.setSize(result.getSize());
            pageResult.setTotalElements(result.getTotalElements());
            pageResult.setTotalPages(result.getTotalPages());
            pageResult.setFirst(result.isFirst());
            pageResult.setLast(result.isLast());
            return ApiResponse.success(pageResult);
        } catch (Exception e) {
            log.error("分页搜索邮件模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取模板列表失败");
        }
    }

    /**
     * 根据ID获取邮件模板
     */
    @Operation(summary = "根据ID获取邮件模板")
    public ApiResponse<EmailTemplate> getTemplateById(Long id) {
        try {
            Optional<EmailTemplate> template = emailTemplateRepository.findById(id);
            if (template.isPresent()) {
                return ApiResponse.success(template.get());
            }
            return ApiResponse.error("模板不存在");
        } catch (Exception e) {
            log.error("获取邮件模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取模板失败");
        }
    }

    /**
     * 创建邮件模板。
     * 如果新模板被设为启用，则自动禁用同类型的其他已启用模板。
     */
    @Operation(summary = "创建邮件模板")
    @Transactional
    public ApiResponse<EmailTemplate> createTemplate(EmailTemplate template) {
        try {
            // 验证类型是否合法
            if (!EmailTemplate.ALL_TYPES.contains(template.getType())) {
                return ApiResponse.error("不支持的模板类型: " + template.getType() +
                        "，支持的类型: " + String.join(", ", EmailTemplate.ALL_TYPES));
            }

            // 如果新模板启用，则禁用同类型的其他启用模板
            if (Boolean.TRUE.equals(template.getEnabled())) {
                disableOtherEnabledTemplates(template.getType(), null);
            }

            EmailTemplate saved = emailTemplateRepository.save(template);
            log.info("邮件模板创建成功: id={}, name={}, type={}", saved.getId(), saved.getName(), saved.getType());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("创建邮件模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建模板失败");
        }
    }

    /**
     * 更新邮件模板。
     * 如果模板被设为启用，则自动禁用同类型的其他已启用模板。
     */
    @Operation(summary = "更新邮件模板")
    @Transactional
    public ApiResponse<EmailTemplate> updateTemplate(Long id, EmailTemplate template) {
        try {
            Optional<EmailTemplate> existingOpt = emailTemplateRepository.findById(id);
            if (existingOpt.isEmpty()) {
                return ApiResponse.error("模板不存在");
            }

            EmailTemplate existing = existingOpt.get();
            
            // 如果修改了类型，验证是否合法
            String newType = template.getType() != null ? template.getType() : existing.getType();
            if (!EmailTemplate.ALL_TYPES.contains(newType)) {
                return ApiResponse.error("不支持的模板类型: " + newType +
                        "，支持的类型: " + String.join(", ", EmailTemplate.ALL_TYPES));
            }

            if (template.getName() != null) {
                existing.setName(template.getName());
            }
            if (template.getType() != null) {
                existing.setType(template.getType());
            }
            if (template.getSubject() != null) {
                existing.setSubject(template.getSubject());
            }
            if (template.getContent() != null) {
                existing.setContent(template.getContent());
            }
            
            boolean enableChanged = false;
            if (template.getEnabled() != null) {
                enableChanged = !template.getEnabled().equals(existing.getEnabled());
                existing.setEnabled(template.getEnabled());
            }
            
            existing.setUpdateTime(LocalDateTime.now());

            // 如果模板被启用（包括类型变更后启用），则禁用同类型的其他启用模板
            if (Boolean.TRUE.equals(existing.getEnabled()) && 
                (enableChanged || template.getType() != null)) {
                disableOtherEnabledTemplates(existing.getType(), id);
            }

            EmailTemplate updated = emailTemplateRepository.save(existing);
            log.info("邮件模板更新成功: id={}, name={}, type={}", updated.getId(), updated.getName(), updated.getType());
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新邮件模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新模板失败");
        }
    }

    /**
     * 删除邮件模板
     */
    @Operation(summary = "删除邮件模板")
    @Transactional
    public ApiResponse<Void> deleteTemplate(Long id) {
        try {
            if (!emailTemplateRepository.existsById(id)) {
                return ApiResponse.error("模板不存在");
            }
            emailTemplateRepository.deleteById(id);
            log.info("邮件模板删除成功: id={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除邮件模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除模板失败");
        }
    }

    /**
     * 禁用指定类型下除 excludeId 外的所有已启用模板。
     * 保证每种类型最多只有一个启用模板。
     */
    private void disableOtherEnabledTemplates(String type, Long excludeId) {
        List<EmailTemplate> enabledTemplates = emailTemplateRepository.findByType(type);
        for (EmailTemplate t : enabledTemplates) {
            if (Boolean.TRUE.equals(t.getEnabled()) && (excludeId == null || !t.getId().equals(excludeId))) {
                t.setEnabled(false);
                emailTemplateRepository.save(t);
                log.info("自动禁用同类型模板: id={}, name={}, type={}", t.getId(), t.getName(), type);
            }
        }
    }
}
