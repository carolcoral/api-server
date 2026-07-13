/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.config;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.entity.EmailConfig;
import com.carolcoral.mockserver.repository.EmailConfigRepository;
import com.carolcoral.mockserver.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 邮箱配置Controller
 * 用于管理 SMTP 邮箱配置和测试邮件发送
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮箱配置", description = "SMTP邮箱配置管理接口")
@RestController
@RequestMapping("/api/email-config")
public class EmailConfigController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailConfigController.class);

    private final EmailConfigRepository emailConfigRepository;
    private final EmailService emailService;

    public EmailConfigController(EmailConfigRepository emailConfigRepository,
                                  EmailService emailService) {
        this.emailConfigRepository = emailConfigRepository;
        this.emailService = emailService;
    }

    /**
     * 获取邮箱配置
     *
     * @return 邮箱配置信息
     */
    @GetMapping
    @Operation(summary = "获取邮箱配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:registration')")
    public ApiResponse<Map<String, Object>> getEmailConfig() {
        Optional<EmailConfig> configOpt = emailConfigRepository.findFirstByEnabledTrue();
        Map<String, Object> result = new HashMap<>();
        if (configOpt.isPresent()) {
            EmailConfig config = configOpt.get();
            result.put("id", config.getId());
            result.put("smtpHost", config.getSmtpHost());
            result.put("smtpPort", config.getSmtpPort());
            result.put("useSsl", config.getUseSsl());
            result.put("fromAddress", config.getFromAddress());
            result.put("username", config.getUsername());
            result.put("displayName", config.getDisplayName());
            // 密码不返回，前端需要单独输入
            result.put("hasPassword", config.getPassword() != null && !config.getPassword().isEmpty());
            result.put("enabled", config.getEnabled());
            result.put("verificationTemplateId", config.getVerificationTemplateId());
        }
        return ApiResponse.success(result);
    }

    /**
     * 保存邮箱配置
     *
     * @param dto 邮箱配置DTO
     * @return 操作结果
     */
    @PostMapping
    @Operation(summary = "保存邮箱配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:registration')")
    public ApiResponse<EmailConfig> saveEmailConfig(@RequestBody EmailConfigDTO dto) {
        // 查找现有配置（只有一个启用的记录）
        Optional<EmailConfig> existingOpt = emailConfigRepository.findFirstByEnabledTrue();
        EmailConfig config;

        if (existingOpt.isPresent()) {
            config = existingOpt.get();
        } else {
            config = new EmailConfig();
        }

        config.setSmtpHost(dto.getSmtpHost());
        config.setSmtpPort(dto.getSmtpPort());
        config.setUseSsl(dto.getUseSsl());
        config.setFromAddress(dto.getFromAddress());
        config.setUsername(dto.getUsername());
        if (dto.getDisplayName() != null) {
            config.setDisplayName(dto.getDisplayName());
        }
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            config.setPassword(dto.getPassword());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getVerificationTemplateId() != null) {
            config.setVerificationTemplateId(dto.getVerificationTemplateId());
        }
        config.setUpdateTime(LocalDateTime.now());

        EmailConfig saved = emailConfigRepository.save(config);
        log.info("邮箱配置已保存: host={}, port={}, ssl={}", saved.getSmtpHost(), saved.getSmtpPort(), saved.getUseSsl());
        return ApiResponse.success(saved);
    }

    /**
     * 测试邮件发送
     *
     * @param dto 测试邮件DTO（包含收件人地址）
     * @return 操作结果
     */
    @PostMapping("/test")
    @Operation(summary = "发送测试邮件")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:registration')")
    public ApiResponse<Boolean> testEmail(@RequestBody TestEmailDTO dto) {
        if (dto.getToAddress() == null || dto.getToAddress().isEmpty()) {
            return ApiResponse.error("请输入测试收件人邮箱");
        }

        Map<String, Object> result = emailService.sendTestEmail(dto.getToAddress());
        boolean success = (boolean) result.get("success");
        if (success) {
            return ApiResponse.success(true);
        } else {
            String errorMsg = (String) result.get("error");
            return ApiResponse.error(errorMsg != null ? errorMsg : "测试邮件发送失败，请检查SMTP配置");
        }
    }
}

/**
 * 邮箱配置DTO
 */
class EmailConfigDTO {
    private String smtpHost;
    private Integer smtpPort;
    private Boolean useSsl;
    private String fromAddress;
    private String username;
    private String password;
    private Boolean enabled;
    private String displayName;
    private Long verificationTemplateId;

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public Boolean getUseSsl() { return useSsl; }
    public void setUseSsl(Boolean useSsl) { this.useSsl = useSsl; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Long getVerificationTemplateId() { return verificationTemplateId; }
    public void setVerificationTemplateId(Long verificationTemplateId) { this.verificationTemplateId = verificationTemplateId; }
}

/**
 * 测试邮件DTO
 */
class TestEmailDTO {
    private String toAddress;

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
}
