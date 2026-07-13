/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.config;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.service.MockService;
import com.carolcoral.mockserver.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置Controller
 * 用于管理系统级别的配置，如语言设置、日期格式、Mock超时配置等
 *
 * @author carolcoral
 * @version 1.1
 * @since 2026-03-06
 */
@Tag(name = "系统配置", description = "系统配置管理接口")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemConfigController.class);

    private final SystemConfigService systemConfigService;
    private final MockService mockService;

    /**
     * 构造器
     */
    public SystemConfigController(SystemConfigService systemConfigService, MockService mockService) {
        this.systemConfigService = systemConfigService;
        this.mockService = mockService;
    }

    /**
     * 获取系统配置
     * <p>返回当前系统级别的配置信息，包括默认语言、Mock超时配置等。</p>
     *
     * @return 系统配置信息
     */
    @GetMapping
    @Operation(summary = "获取系统配置")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SystemConfigDTO> getSystemConfig() {
        SystemConfigDTO config = new SystemConfigDTO();
        config.setDefaultLanguage(systemConfigService.getDefaultLanguage());
        config.setDateFormat(systemConfigService.getConfig("dateFormat"));
        config.setDefaultResponseDelay(parseIntConfig("defaultResponseDelay", 0));
        config.setMaxResponseDelay(parseIntConfig("maxResponseDelay", 5000));
        config.setEnableRequestLog(parseBooleanConfig("enableRequestLog", true));
        config.setLogRetentionDays(parseIntConfig("logRetentionDays", 30));
        config.setMaxRequestBodySize(parseIntConfig("maxRequestBodySize", 10));
        config.setAxiosTimeout(parseIntConfig("axiosTimeout", 30000));
        config.setCustomResponseCacheSeconds(parseIntConfig("customResponseCacheSeconds", 600));
        config.setEnableRegistration(parseBooleanConfig("enableRegistration", false));
        config.setAllowedEmailDomains(systemConfigService.getConfig("allowedEmailDomains"));
        config.setEnableEmailVerification(parseBooleanConfig("enableEmailVerification", false));
        config.setSiteBaseUrl(systemConfigService.getConfig("siteBaseUrl"));
        log.info("获取系统配置 - siteBaseUrl={}", config.getSiteBaseUrl());
        return ApiResponse.success(config);
    }

    /**
     * 更新系统语言配置
     * <p>需要管理员权限。更新后会持久化到数据库，所有用户重新登录后将使用新的语言设置。</p>
     *
     * @param dto 语言配置DTO，包含 {@code language} 字段（如 zh-CN, en-US, ja-JP）
     * @return 操作结果
     */
    @PostMapping("/language")
    @Operation(summary = "更新系统语言")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:basic')")
    public ApiResponse<Void> updateLanguage(@RequestBody DefaultLanguageDTO dto) {
        systemConfigService.saveConfig("defaultLanguage", dto.getLanguage(), "系统默认语言");
        log.info("管理员更新系统语言为: {}", dto.getLanguage());
        return ApiResponse.success();
    }

    /**
     * 更新系统日期格式配置
     * <p>需要管理员权限。更新后会持久化到数据库。</p>
     *
     * @param dto 日期格式配置DTO，包含 {@code dateFormat} 字段（如 YYYY-MM-DD, DD/MM/YYYY）
     * @return 操作结果
     */
    @PostMapping("/date-format")
    @Operation(summary = "更新系统日期格式")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:basic')")
    public ApiResponse<Void> updateDateFormat(@RequestBody DateFormatDTO dto) {
        systemConfigService.saveConfig("dateFormat", dto.getDateFormat(), "系统日期格式");
        log.info("管理员更新系统日期格式为: {}", dto.getDateFormat());
        return ApiResponse.success();
    }

    /**
     * 更新Mock配置（响应延迟、日志、请求体大小限制等）
     * <p>需要管理员权限。更新后会持久化到数据库。</p>
     *
     * @param dto Mock配置DTO
     * @return 操作结果
     */
    @PostMapping("/mock")
    @Operation(summary = "更新Mock配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:mock')")
    public ApiResponse<Void> updateMockConfig(@RequestBody MockConfigDTO dto) {
        if (dto.getDefaultResponseDelay() != null) {
            systemConfigService.saveConfig("defaultResponseDelay", String.valueOf(dto.getDefaultResponseDelay()), "默认响应延迟（毫秒）");
        }
        if (dto.getMaxResponseDelay() != null) {
            systemConfigService.saveConfig("maxResponseDelay", String.valueOf(dto.getMaxResponseDelay()), "最大响应延迟（毫秒）");
        }
        if (dto.getEnableRequestLog() != null) {
            systemConfigService.saveConfig("enableRequestLog", String.valueOf(dto.getEnableRequestLog()), "是否启用请求日志");
        }
        if (dto.getLogRetentionDays() != null) {
            systemConfigService.saveConfig("logRetentionDays", String.valueOf(dto.getLogRetentionDays()), "日志保留天数");
        }
        if (dto.getMaxRequestBodySize() != null) {
            systemConfigService.saveConfig("maxRequestBodySize", String.valueOf(dto.getMaxRequestBodySize()), "最大请求体大小（MB）");
        }
        if (dto.getAxiosTimeout() != null) {
            systemConfigService.saveConfig("axiosTimeout", String.valueOf(dto.getAxiosTimeout()), "前端Axios请求超时时间（毫秒）");
        }
        if (dto.getCustomResponseCacheSeconds() != null) {
            systemConfigService.saveConfig("customResponseCacheSeconds", String.valueOf(dto.getCustomResponseCacheSeconds()), "自定义接口响应缓存时间（秒），0表示不缓存");
            // 当缓存时间设置为 0 时，立即清除所有已缓存的响应，确保后续请求使用新逻辑
            if (dto.getCustomResponseCacheSeconds() == 0) {
                mockService.clearCustomResponseCache();
                log.info("缓存时间设置为0，已清除所有自定义响应缓存");
            }
        }
        log.info("管理员更新Mock配置: defaultResponseDelay={}, maxResponseDelay={}, enableRequestLog={}, logRetentionDays={}, maxRequestBodySize={}, axiosTimeout={}, customResponseCacheSeconds={}",
                dto.getDefaultResponseDelay(), dto.getMaxResponseDelay(), dto.getEnableRequestLog(),
                dto.getLogRetentionDays(), dto.getMaxRequestBodySize(), dto.getAxiosTimeout(), dto.getCustomResponseCacheSeconds());
        return ApiResponse.success();
    }

    /**
     * 解析整数型配置，不存在时返回默认值
     */
    private int parseIntConfig(String key, int defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("配置项 {} 的值 '{}' 无法解析为整数，使用默认值 {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * 解析布尔型配置，不存在时返回默认值
     */
    private boolean parseBooleanConfig(String key, boolean defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    /**
     * 保存注册配置
     * <p>需要管理员权限。控制是否开启用户注册功能及允许的邮箱域名。</p>
     *
     * @param dto 注册配置DTO
     * @return 操作结果
     */
    @PostMapping("/registration")
    @Operation(summary = "保存注册配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:registration')")
    public ApiResponse<Void> saveRegistrationConfig(@RequestBody RegistrationConfigDTO dto) {
        if (dto.getEnableRegistration() != null) {
            systemConfigService.saveConfig("enableRegistration", String.valueOf(dto.getEnableRegistration()), "是否开启用户注册");
        }
        if (dto.getAllowedEmailDomains() != null) {
            systemConfigService.saveConfig("allowedEmailDomains", dto.getAllowedEmailDomains(), "允许注册的邮箱域名（逗号分隔）");
        }
        if (dto.getEnableEmailVerification() != null) {
            systemConfigService.saveConfig("enableEmailVerification", String.valueOf(dto.getEnableEmailVerification()), "是否开启邮箱验证");
        }
        if (dto.getSiteBaseUrl() != null) {
            systemConfigService.saveConfig("siteBaseUrl", dto.getSiteBaseUrl(), "系统访问地址（用于邮件模板占位符）");
        }
        log.info("管理员更新注册配置: enableRegistration={}, allowedEmailDomains={}, enableEmailVerification={}, siteBaseUrl={}",
                dto.getEnableRegistration(), dto.getAllowedEmailDomains(), dto.getEnableEmailVerification(), dto.getSiteBaseUrl());
        return ApiResponse.success();
    }

    // ========== 页脚配置 ==========

    /**
     * 获取页脚配置
     * <p>返回页脚的版权信息、链接等配置，用于前端动态渲染页脚。</p>
     *
     * @return 页脚配置信息
     */
    @GetMapping("/footer")
    @Operation(summary = "获取页脚配置")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FooterConfigDTO> getFooterConfig() {
        FooterConfigDTO config = new FooterConfigDTO();
        config.setEnableCopyright(parseBooleanConfig("footerEnableCopyright", true));
        config.setCopyright(systemConfigService.getConfig("footerCopyright"));
        config.setEnableFriendLink(parseBooleanConfig("footerEnableFriendLink", true));
        config.setFriendLinkUrl(systemConfigService.getConfig("footerFriendLinkUrl"));
        config.setFriendLinkTitle(systemConfigService.getConfig("footerFriendLinkTitle"));
        config.setEnableBlog(parseBooleanConfig("footerEnableBlog", true));
        config.setBlogUrl(systemConfigService.getConfig("footerBlogUrl"));
        config.setBlogTitle(systemConfigService.getConfig("footerBlogTitle"));
        config.setEnableGithub(parseBooleanConfig("footerEnableGithub", true));
        config.setGithubUrl(systemConfigService.getConfig("footerGithubUrl"));
        config.setGithubTitle(systemConfigService.getConfig("footerGithubTitle"));
        config.setEnableEmail(parseBooleanConfig("footerEnableEmail", true));
        config.setEmailAddress(systemConfigService.getConfig("footerEmailAddress"));
        config.setEmailTitle(systemConfigService.getConfig("footerEmailTitle"));
        config.setEnableCustomLinks(parseBooleanConfig("footerEnableCustomLinks", true));

        // 自定义链接列表
        List<Map<String, String>> customLinks = new ArrayList<>();
        int index = 0;
        while (true) {
            String url = systemConfigService.getConfig("footerCustomLinkUrl" + index);
            String title = systemConfigService.getConfig("footerCustomLinkTitle" + index);
            if (url == null || url.isEmpty()) break;
            Map<String, String> link = new LinkedHashMap<>();
            link.put("url", url);
            link.put("title", title != null ? title : "");
            String svgIcon = systemConfigService.getConfig("footerCustomLinkSvgIcon" + index);
            if (svgIcon != null && !svgIcon.isEmpty()) {
                link.put("svgIcon", svgIcon);
            }
            customLinks.add(link);
            index++;
        }
        config.setCustomLinks(customLinks);
        return ApiResponse.success(config);
    }

    /**
     * 保存页脚配置
     * <p>需要管理员权限。更新后会持久化到数据库。</p>
     *
     * @param dto 页脚配置DTO
     * @return 操作结果
     */
    @PostMapping("/footer")
    @Operation(summary = "保存页脚配置")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('settings:footer')")
    public ApiResponse<Void> saveFooterConfig(@RequestBody FooterConfigDTO dto) {
        if (dto.getEnableCopyright() != null) {
            systemConfigService.saveConfig("footerEnableCopyright", String.valueOf(dto.getEnableCopyright()), "页脚-是否启用版权信息");
        }
        if (dto.getCopyright() != null) {
            systemConfigService.saveConfig("footerCopyright", dto.getCopyright(), "页脚版权信息");
        }
        if (dto.getEnableFriendLink() != null) {
            systemConfigService.saveConfig("footerEnableFriendLink", String.valueOf(dto.getEnableFriendLink()), "页脚-是否启用友情链接");
        }
        if (dto.getFriendLinkUrl() != null) {
            systemConfigService.saveConfig("footerFriendLinkUrl", dto.getFriendLinkUrl(), "友情链接URL");
        }
        if (dto.getFriendLinkTitle() != null) {
            systemConfigService.saveConfig("footerFriendLinkTitle", dto.getFriendLinkTitle(), "友情链接标题");
        }
        if (dto.getEnableBlog() != null) {
            systemConfigService.saveConfig("footerEnableBlog", String.valueOf(dto.getEnableBlog()), "页脚-是否启用博客链接");
        }
        if (dto.getBlogUrl() != null) {
            systemConfigService.saveConfig("footerBlogUrl", dto.getBlogUrl(), "博客链接URL");
        }
        if (dto.getBlogTitle() != null) {
            systemConfigService.saveConfig("footerBlogTitle", dto.getBlogTitle(), "博客链接标题");
        }
        if (dto.getEnableGithub() != null) {
            systemConfigService.saveConfig("footerEnableGithub", String.valueOf(dto.getEnableGithub()), "页脚-是否启用GitHub链接");
        }
        if (dto.getGithubUrl() != null) {
            systemConfigService.saveConfig("footerGithubUrl", dto.getGithubUrl(), "GitHub链接URL");
        }
        if (dto.getGithubTitle() != null) {
            systemConfigService.saveConfig("footerGithubTitle", dto.getGithubTitle(), "GitHub链接标题");
        }
        if (dto.getEnableEmail() != null) {
            systemConfigService.saveConfig("footerEnableEmail", String.valueOf(dto.getEnableEmail()), "页脚-是否启用邮箱链接");
        }
        if (dto.getEmailAddress() != null) {
            systemConfigService.saveConfig("footerEmailAddress", dto.getEmailAddress(), "邮箱地址");
        }
        if (dto.getEmailTitle() != null) {
            systemConfigService.saveConfig("footerEmailTitle", dto.getEmailTitle(), "邮箱链接标题");
        }
        if (dto.getEnableCustomLinks() != null) {
            systemConfigService.saveConfig("footerEnableCustomLinks", String.valueOf(dto.getEnableCustomLinks()), "页脚-是否启用自定义链接");
        }

        // 保存自定义链接：先清除旧的，再写入新的
        int index = 0;
        while (systemConfigService.getConfig("footerCustomLinkUrl" + index) != null) {
            systemConfigService.deleteConfig("footerCustomLinkUrl" + index);
            systemConfigService.deleteConfig("footerCustomLinkTitle" + index);
            systemConfigService.deleteConfig("footerCustomLinkSvgIcon" + index);
            index++;
        }

        if (dto.getCustomLinks() != null) {
            for (int i = 0; i < dto.getCustomLinks().size(); i++) {
                Map<String, String> link = dto.getCustomLinks().get(i);
                if (link.get("url") != null && !link.get("url").isEmpty()) {
                    systemConfigService.saveConfig("footerCustomLinkUrl" + i, link.get("url"), "自定义链接" + (i + 1) + " URL");
                    systemConfigService.saveConfig("footerCustomLinkTitle" + i,
                            link.get("title") != null ? link.get("title") : "", "自定义链接" + (i + 1) + " 标题");
                    if (link.get("svgIcon") != null && !link.get("svgIcon").isEmpty()) {
                        systemConfigService.saveConfig("footerCustomLinkSvgIcon" + i, link.get("svgIcon"), "自定义链接" + (i + 1) + " SVG图标");
                    }
                }
            }
        }

        log.info("管理员更新页脚配置");
        return ApiResponse.success();
    }
}

/**
 * 系统配置DTO
 */
class SystemConfigDTO {
    /** 系统默认语言，如 zh-CN, en-US, ja-JP */
    private String defaultLanguage;
    /** 系统日期格式，如 YYYY-MM-DD, DD/MM/YYYY */
    private String dateFormat;
    /** 默认响应延迟（毫秒） */
    private Integer defaultResponseDelay;
    /** 最大响应延迟（毫秒） */
    private Integer maxResponseDelay;
    /** 是否启用请求日志 */
    private Boolean enableRequestLog;
    /** 日志保留天数 */
    private Integer logRetentionDays;
    /** 最大请求体大小（MB） */
    private Integer maxRequestBodySize;
    /** 前端Axios请求超时时间（毫秒） */
    private Integer axiosTimeout;
    /** 自定义接口响应缓存时间（秒），0表示不缓存 */
    private Integer customResponseCacheSeconds;
    /** 是否开启用户注册功能 */
    private Boolean enableRegistration;
    /** 允许注册的邮箱域名（逗号分隔，空表示不限制） */
    private String allowedEmailDomains;
    /** 是否开启邮箱验证 */
    private Boolean enableEmailVerification;
    /** 系统访问地址（用于邮件模板 {{siteUrl}} 占位符） */
    private String siteBaseUrl;

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Integer getDefaultResponseDelay() { return defaultResponseDelay; }
    public void setDefaultResponseDelay(Integer defaultResponseDelay) { this.defaultResponseDelay = defaultResponseDelay; }

    public Integer getMaxResponseDelay() { return maxResponseDelay; }
    public void setMaxResponseDelay(Integer maxResponseDelay) { this.maxResponseDelay = maxResponseDelay; }

    public Boolean getEnableRequestLog() { return enableRequestLog; }
    public void setEnableRequestLog(Boolean enableRequestLog) { this.enableRequestLog = enableRequestLog; }

    public Integer getLogRetentionDays() { return logRetentionDays; }
    public void setLogRetentionDays(Integer logRetentionDays) { this.logRetentionDays = logRetentionDays; }

    public Integer getMaxRequestBodySize() { return maxRequestBodySize; }
    public void setMaxRequestBodySize(Integer maxRequestBodySize) { this.maxRequestBodySize = maxRequestBodySize; }

    public Integer getAxiosTimeout() { return axiosTimeout; }
    public void setAxiosTimeout(Integer axiosTimeout) { this.axiosTimeout = axiosTimeout; }

    public Boolean getEnableRegistration() { return enableRegistration; }
    public void setEnableRegistration(Boolean enableRegistration) { this.enableRegistration = enableRegistration; }

    public Integer getCustomResponseCacheSeconds() { return customResponseCacheSeconds; }
    public void setCustomResponseCacheSeconds(Integer customResponseCacheSeconds) { this.customResponseCacheSeconds = customResponseCacheSeconds; }

    public String getAllowedEmailDomains() { return allowedEmailDomains; }
    public void setAllowedEmailDomains(String allowedEmailDomains) { this.allowedEmailDomains = allowedEmailDomains; }

    public Boolean getEnableEmailVerification() { return enableEmailVerification; }
    public void setEnableEmailVerification(Boolean enableEmailVerification) { this.enableEmailVerification = enableEmailVerification; }

    public String getSiteBaseUrl() { return siteBaseUrl; }
    public void setSiteBaseUrl(String siteBaseUrl) { this.siteBaseUrl = siteBaseUrl; }
}

/**
 * 注册配置DTO
 */
class RegistrationConfigDTO {
    private Boolean enableRegistration;
    private String allowedEmailDomains;
    private Boolean enableEmailVerification;
    private String siteBaseUrl;

    public Boolean getEnableRegistration() { return enableRegistration; }
    public void setEnableRegistration(Boolean enableRegistration) { this.enableRegistration = enableRegistration; }

    public String getAllowedEmailDomains() { return allowedEmailDomains; }
    public void setAllowedEmailDomains(String allowedEmailDomains) { this.allowedEmailDomains = allowedEmailDomains; }

    public Boolean getEnableEmailVerification() { return enableEmailVerification; }
    public void setEnableEmailVerification(Boolean enableEmailVerification) { this.enableEmailVerification = enableEmailVerification; }

    public String getSiteBaseUrl() { return siteBaseUrl; }
    public void setSiteBaseUrl(String siteBaseUrl) { this.siteBaseUrl = siteBaseUrl; }
}

/**
 * 默认语言更新DTO
 */
class DefaultLanguageDTO {
    /** 语言代码，如 zh-CN, en-US, ja-JP */
    private String language;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}

/**
 * 日期格式更新DTO
 */
class DateFormatDTO {
    /** 日期格式，如 YYYY-MM-DD, DD/MM/YYYY, MM/DD/YYYY */
    private String dateFormat;

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }
}

/**
 * Mock配置DTO
 */
class MockConfigDTO {
    /** 默认响应延迟（毫秒） */
    private Integer defaultResponseDelay;
    /** 最大响应延迟（毫秒） */
    private Integer maxResponseDelay;
    /** 是否启用请求日志 */
    private Boolean enableRequestLog;
    /** 日志保留天数 */
    private Integer logRetentionDays;
    /** 最大请求体大小（MB） */
    private Integer maxRequestBodySize;
    /** 前端Axios请求超时时间（毫秒） */
    private Integer axiosTimeout;

    public Integer getDefaultResponseDelay() { return defaultResponseDelay; }
    public void setDefaultResponseDelay(Integer defaultResponseDelay) { this.defaultResponseDelay = defaultResponseDelay; }

    public Integer getMaxResponseDelay() { return maxResponseDelay; }
    public void setMaxResponseDelay(Integer maxResponseDelay) { this.maxResponseDelay = maxResponseDelay; }

    public Boolean getEnableRequestLog() { return enableRequestLog; }
    public void setEnableRequestLog(Boolean enableRequestLog) { this.enableRequestLog = enableRequestLog; }

    public Integer getLogRetentionDays() { return logRetentionDays; }
    public void setLogRetentionDays(Integer logRetentionDays) { this.logRetentionDays = logRetentionDays; }

    public Integer getMaxRequestBodySize() { return maxRequestBodySize; }
    public void setMaxRequestBodySize(Integer maxRequestBodySize) { this.maxRequestBodySize = maxRequestBodySize; }

    public Integer getAxiosTimeout() { return axiosTimeout; }
    public void setAxiosTimeout(Integer axiosTimeout) { this.axiosTimeout = axiosTimeout; }

    public Integer getCustomResponseCacheSeconds() { return customResponseCacheSeconds; }
    public void setCustomResponseCacheSeconds(Integer customResponseCacheSeconds) { this.customResponseCacheSeconds = customResponseCacheSeconds; }

    /** 自定义接口响应缓存时间（秒），0表示不缓存 */
    private Integer customResponseCacheSeconds;
}

/**
 * 页脚配置DTO
 */
class FooterConfigDTO {
    /** 是否启用版权信息 */
    private Boolean enableCopyright;
    /** 版权信息，如 "© 2026 carolcoral" */
    private String copyright;
    /** 是否启用友情链接 */
    private Boolean enableFriendLink;
    /** 友情链接URL */
    private String friendLinkUrl;
    /** 友情链接提示文本 */
    private String friendLinkTitle;
    /** 是否启用博客链接 */
    private Boolean enableBlog;
    /** 博客链接URL */
    private String blogUrl;
    /** 博客链接提示文本 */
    private String blogTitle;
    /** 是否启用GitHub链接 */
    private Boolean enableGithub;
    /** GitHub链接URL */
    private String githubUrl;
    /** GitHub链接提示文本 */
    private String githubTitle;
    /** 是否启用邮箱链接 */
    private Boolean enableEmail;
    /** 邮箱地址 */
    private String emailAddress;
    /** 邮箱链接提示文本 */
    private String emailTitle;
    /** 是否启用自定义链接 */
    private Boolean enableCustomLinks;
    /** 自定义链接列表 */
    private List<Map<String, String>> customLinks;

    public Boolean getEnableCopyright() { return enableCopyright; }
    public void setEnableCopyright(Boolean enableCopyright) { this.enableCopyright = enableCopyright; }

    public String getCopyright() { return copyright; }
    public void setCopyright(String copyright) { this.copyright = copyright; }

    public Boolean getEnableFriendLink() { return enableFriendLink; }
    public void setEnableFriendLink(Boolean enableFriendLink) { this.enableFriendLink = enableFriendLink; }

    public String getFriendLinkUrl() { return friendLinkUrl; }
    public void setFriendLinkUrl(String friendLinkUrl) { this.friendLinkUrl = friendLinkUrl; }

    public String getFriendLinkTitle() { return friendLinkTitle; }
    public void setFriendLinkTitle(String friendLinkTitle) { this.friendLinkTitle = friendLinkTitle; }

    public Boolean getEnableBlog() { return enableBlog; }
    public void setEnableBlog(Boolean enableBlog) { this.enableBlog = enableBlog; }

    public String getBlogUrl() { return blogUrl; }
    public void setBlogUrl(String blogUrl) { this.blogUrl = blogUrl; }

    public String getBlogTitle() { return blogTitle; }
    public void setBlogTitle(String blogTitle) { this.blogTitle = blogTitle; }

    public Boolean getEnableGithub() { return enableGithub; }
    public void setEnableGithub(Boolean enableGithub) { this.enableGithub = enableGithub; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public String getGithubTitle() { return githubTitle; }
    public void setGithubTitle(String githubTitle) { this.githubTitle = githubTitle; }

    public Boolean getEnableEmail() { return enableEmail; }
    public void setEnableEmail(Boolean enableEmail) { this.enableEmail = enableEmail; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getEmailTitle() { return emailTitle; }
    public void setEmailTitle(String emailTitle) { this.emailTitle = emailTitle; }

    public Boolean getEnableCustomLinks() { return enableCustomLinks; }
    public void setEnableCustomLinks(Boolean enableCustomLinks) { this.enableCustomLinks = enableCustomLinks; }

    public List<Map<String, String>> getCustomLinks() { return customLinks; }
    public void setCustomLinks(List<Map<String, String>> customLinks) { this.customLinks = customLinks; }
}

