/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.entity.EmailConfig;
import com.carolcoral.apiserver.entity.EmailTemplate;
import com.carolcoral.apiserver.entity.VerificationCode;
import com.carolcoral.apiserver.repository.EmailConfigRepository;
import com.carolcoral.apiserver.repository.EmailTemplateRepository;
import com.carolcoral.apiserver.repository.VerificationCodeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 邮件服务
 * 负责 SMTP 邮件发送和验证码管理
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮件服务", description = "邮件发送与验证码管理")
@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    private final EmailConfigRepository emailConfigRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final SystemConfigService systemConfigService;

    public EmailService(EmailConfigRepository emailConfigRepository,
                        EmailTemplateRepository emailTemplateRepository,
                        VerificationCodeRepository verificationCodeRepository,
                        SystemConfigService systemConfigService) {
        this.emailConfigRepository = emailConfigRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取启用的邮箱配置
     *
     * @return 邮箱配置
     */
    public Optional<EmailConfig> getActiveEmailConfig() {
        return emailConfigRepository.findFirstByEnabledTrue();
    }

    /**
     * 发送邮件（通用方法）
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 内容（HTML格式）
     * @return 是否发送成功
     */
    @Operation(summary = "发送邮件")
    public boolean sendEmail(String to, String subject, String content) {
        return sendEmail(to, subject, content, null);
    }

    /**
     * 发送邮件（通用方法，支持收集错误信息）
     *
     * @param to        收件人邮箱
     * @param subject   主题
     * @param content   内容（HTML格式）
     * @param errorRef  错误信息收集器（可为 null）
     * @return 是否发送成功
     */
    @Operation(summary = "发送邮件（带错误收集）")
    public boolean sendEmail(String to, String subject, String content, AtomicReference<String> errorRef) {
        Optional<EmailConfig> configOpt = getActiveEmailConfig();
        if (configOpt.isEmpty()) {
            String msg = "未找到启用的邮箱配置";
            log.warn("邮件发送失败：{}", msg);
            if (errorRef != null) errorRef.set(msg);
            return false;
        }

        EmailConfig config = configOpt.get();
        if (!config.getEnabled()) {
            String msg = "邮箱服务未启用";
            log.warn("邮件发送失败：{}", msg);
            if (errorRef != null) errorRef.set(msg);
            return false;
        }

        try {
            JavaMailSender mailSender = createMailSender(config);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = config.getFromAddress();
            String displayName = config.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                helper.setFrom(displayName + " <" + from + ">");
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("邮件发送成功：to={}, subject={}", to, subject);
            return true;
        } catch (AuthenticationFailedException e) {
            String msg = "SMTP认证失败，请检查邮箱账号和密码是否正确（可能需要使用应用专用密码）";
            log.error("邮件发送失败（认证错误）：to={}, error={}", to, e.getMessage());
            if (errorRef != null) errorRef.set(msg);
            return false;
        } catch (MessagingException e) {
            String msg = extractErrorMessage(e);
            log.error("邮件发送失败：to={}, error={}", to, msg != null ? msg : e.getMessage());
            if (errorRef != null) errorRef.set(msg != null ? msg : "邮件发送失败：" + e.getMessage());
            return false;
        } catch (Exception e) {
            String msg = "邮件发送异常：" + e.getMessage();
            log.error("邮件发送失败（未知错误）：to={}, error={}", to, e.getMessage(), e);
            if (errorRef != null) errorRef.set(msg);
            return false;
        }
    }

    /**
     * 发送测试邮件
     *
     * @param to 收件人邮箱
     * @return 包含 success (boolean) 和 error (String, 成功时为 null) 的结果
     */
    @Operation(summary = "发送测试邮件")
    public java.util.Map<String, Object> sendTestEmail(String to) {
        AtomicReference<String> errorRef = new AtomicReference<>();
        boolean success = sendEmail(to, "【API Server】邮件发送测试",
                "<div style='padding:20px;font-family:Arial,sans-serif;'>" +
                "<h2 style='color:#667eea;'>API Server</h2>" +
                "<p>这是一封测试邮件。</p>" +
                "<p>如果您能收到此邮件，说明邮箱配置正确，邮件发送功能正常。</p>" +
                "<hr style='border:1px solid #eee;'/>" +
                "<p style='color:#999;font-size:12px;'>此邮件由系统自动发送，请勿回复。</p>" +
                "</div>", errorRef);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        if (!success) {
            result.put("error", errorRef.get() != null ? errorRef.get() : "邮件发送失败，请检查SMTP配置");
        }
        return result;
    }

    /**
     * 生成验证码（当前时间戳的后6位）
     *
     * @return 6位验证码字符串
     */
    public String generateVerificationCode() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return timestamp.substring(timestamp.length() - 6);
    }

    /**
     * 发送注册验证码
     *
     * @param email 收件人邮箱
     * @param username 用户名（用于替换模板中的 {{username}} 占位符）
     * @param templateId 模板ID（可选）
     * @return 验证码（如果发送成功），null（如果发送失败）
     */
    @Operation(summary = "发送注册验证码")
    @Transactional
    public String sendRegisterVerificationCode(String email, String username, Long templateId) {
        // 使该邮箱之前的注册验证码失效
        List<VerificationCode> oldCodes = verificationCodeRepository.findByEmailAndTypeAndUsedFalse(email, EmailTemplate.TYPE_REGISTER);
        for (VerificationCode oldCode : oldCodes) {
            oldCode.setUsed(true);
            verificationCodeRepository.save(oldCode);
        }

        // 生成新验证码
        String code = generateVerificationCode();

        // 保存验证码（有效期5分钟）
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setType(EmailTemplate.TYPE_REGISTER);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verificationCode.setUsed(false);
        verificationCodeRepository.save(verificationCode);

        // 获取邮件模板（优先使用指定模板ID，否则按类型查找启用模板）
        Optional<EmailTemplate> templateOpt = Optional.empty();
        if (templateId != null) {
            templateOpt = emailTemplateRepository.findById(templateId);
        }
        if (templateOpt.isEmpty()) {
            templateOpt = emailTemplateRepository.findFirstByTypeAndEnabledTrue(EmailTemplate.TYPE_REGISTER);
        }
        String subject;
        String content;

        if (templateOpt.isPresent()) {
            EmailTemplate template = templateOpt.get();
            subject = template.getSubject();
            content = template.getContent();
        } else {
            // 使用默认模板
            subject = "【API Server】注册验证码";
            content = "<div style='padding:20px;font-family:Arial,sans-serif;'>" +
                    "<h2 style='color:#667eea;'>API Server</h2>" +
                    "<p>您正在注册 API Server 账号，验证码如下：</p>" +
                    "<div style='text-align:center;margin:30px 0;'>" +
                    "<span style='font-size:32px;font-weight:bold;color:#667eea;letter-spacing:8px;'>{{code}}</span>" +
                    "</div>" +
                    "<p>验证码 <strong>5分钟</strong> 内有效，请勿泄露给他人。</p>" +
                    "<p style='color:#909399;'>访问地址：<a href='{{siteUrl}}' style='color:#667eea;'>{{siteUrl}}</a></p>" +
                    "<hr style='border:1px solid #eee;'/>" +
                    "<p style='color:#999;font-size:12px;'>此邮件由系统自动发送，请勿回复。如非本人操作，请忽略。</p>" +
                    "</div>";
        }

        // 替换占位符（传入 username，使模板中 {{username}} 自动填充为当前实际用户名）
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        subject = replacePlaceholders(subject, username, email, currentTime, code);
        content = replacePlaceholders(content, username, email, currentTime, code);

        // 发送邮件
        boolean sent = sendEmail(email, subject, content);
        if (sent) {
            log.info("注册验证码已发送：email={}, code={}", email, code);
            return code; // 返回验证码供后续验证
        } else {
            // 发送失败，删除验证码记录
            verificationCode.setUsed(true);
            verificationCodeRepository.save(verificationCode);
            return null;
        }
    }

    /**
     * 验证注册验证码
     *
     * @param email 邮箱
     * @param code  验证码
     * @return 是否验证通过
     */
    @Operation(summary = "验证注册验证码")
    @Transactional
    public boolean verifyRegisterCode(String email, String code) {
        Optional<VerificationCode> codeOpt = verificationCodeRepository
                .findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreateTimeDesc(email, code, EmailTemplate.TYPE_REGISTER);

        if (codeOpt.isEmpty()) {
            return false;
        }

        VerificationCode vc = codeOpt.get();

        // 检查是否过期
        if (vc.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        // 标记为已使用
        vc.setUsed(true);
        verificationCodeRepository.save(vc);
        return true;
    }

    /**
     * 发送忘记密码邮件（自动生成的新密码）
     *
     * @param email       收件人邮箱
     * @param username    用户名
     * @param newPassword 新密码
     * @return 是否发送成功
     */
    @Operation(summary = "发送忘记密码邮件")
    public boolean sendResetPasswordEmail(String email, String username, String newPassword) {
        // 查找 RESET_PASSWORD 类型的启用模板
        Optional<EmailTemplate> templateOpt = emailTemplateRepository
                .findFirstByTypeAndEnabledTrue(EmailTemplate.TYPE_RESET_PASSWORD);
        String subject;
        String content;

        if (templateOpt.isPresent()) {
            EmailTemplate template = templateOpt.get();
            subject = template.getSubject();
            content = template.getContent();
        } else {
            subject = "【API Server】密码重置 - 您的新密码";
            content = "<div style='padding:20px;font-family:Arial,sans-serif;'>" +
                    "<h2 style='color:#667eea;'>API Server</h2>" +
                    "<p>您好，<strong>{{username}}</strong>：</p>" +
                    "<p>您的密码已被重置，新的登录信息如下：</p>" +
                    "<div style='background:#f5f7fa;padding:20px;border-radius:8px;margin:20px 0;'>" +
                    "<p><strong>用户名：</strong>{{username}}</p>" +
                    "<p><strong>新密码：</strong><code style='background:#e8eaed;padding:2px 8px;border-radius:4px;font-size:16px;'>{{password}}</code></p>" +
                    "</div>" +
                    "<p>请尽快使用此密码登录，建议登录后立即修改密码。</p>" +
                    "<p style='color:#909399;'>访问地址：<a href='{{siteUrl}}' style='color:#667eea;'>{{siteUrl}}</a></p>" +
                    "<hr style='border:1px solid #eee;'/>" +
                    "<p style='color:#999;font-size:12px;'>此邮件由系统自动发送，请勿回复。如非本人操作，请联系管理员。</p>" +
                    "</div>";
        }

        // 替换占位符
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        subject = replacePlaceholders(subject, username, email, currentTime, newPassword);
        content = replacePlaceholders(content, username, email, currentTime, newPassword);

        boolean sent = sendEmail(email, subject, content);
        if (sent) {
            log.info("忘记密码邮件已发送: email={}, username={}", email, username);
        } else {
            log.warn("忘记密码邮件发送失败: email={}", email);
        }
        return sent;
    }

    /**
     * 发送密码被管理员修改通知邮件
     *
     * @param email       收件人邮箱
     * @param username    用户名
     * @param newPassword 新密码
     * @return 是否发送成功
     */
    @Operation(summary = "发送密码修改通知邮件（管理员操作）")
    public boolean sendPasswordChangedEmail(String email, String username, String newPassword) {
        // 优先查找 PASSWORD_CHANGED 类型的启用模板，找不到则 fallback 到 RESET_PASSWORD 模板
        Optional<EmailTemplate> templateOpt = emailTemplateRepository
                .findFirstByTypeAndEnabledTrue(EmailTemplate.TYPE_PASSWORD_CHANGED);
        if (templateOpt.isEmpty()) {
            templateOpt = emailTemplateRepository
                    .findFirstByTypeAndEnabledTrue(EmailTemplate.TYPE_RESET_PASSWORD);
        }
        String subject;
        String content;

        if (templateOpt.isPresent()) {
            EmailTemplate template = templateOpt.get();
            subject = template.getSubject();
            content = template.getContent();
        } else {
            subject = "【API Server】您的密码已被管理员重置";
            content = "<div style='padding:20px;font-family:Arial,sans-serif;'>" +
                    "<h2 style='color:#667eea;'>API Server</h2>" +
                    "<p>您好，<strong>{{username}}</strong>：</p>" +
                    "<p>您的账号密码已被管理员重置，新的登录信息如下：</p>" +
                    "<div style='background:#f5f7fa;padding:20px;border-radius:8px;margin:20px 0;'>" +
                    "<p><strong>用户名：</strong>{{username}}</p>" +
                    "<p><strong>新密码：</strong><code style='background:#e8eaed;padding:2px 8px;border-radius:4px;font-size:16px;'>{{password}}</code></p>" +
                    "</div>" +
                    "<p>请尽快登录并修改密码。</p>" +
                    "<p style='color:#909399;'>访问地址：<a href='{{siteUrl}}' style='color:#667eea;'>{{siteUrl}}</a></p>" +
                    "<hr style='border:1px solid #eee;'/>" +
                    "<p style='color:#999;font-size:12px;'>此邮件由系统自动发送，请勿回复。如非本人操作，请联系管理员。</p>" +
                    "</div>";
        }

        // 替换占位符
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        subject = replacePlaceholders(subject, username, email, currentTime, newPassword);
        content = replacePlaceholders(content, username, email, currentTime, newPassword);

        boolean sent = sendEmail(email, subject, content);
        if (sent) {
            log.info("密码修改通知邮件已发送（管理员操作）: email={}, username={}", email, username);
        } else {
            log.warn("密码修改通知邮件发送失败（管理员操作）: email={}", email);
        }
        return sent;
    }

    /**
     * 发送用户自行修改密码的安全通知邮件。
     * <p>
     * 与管理员修改密码不同，此场景用户已知新密码，邮件仅作为安全通知，
     * 告知用户密码已变更的时间，不包含新密码明文。
     * </p>
     *
     * @param email    收件人邮箱
     * @param username 用户名
     * @return 是否发送成功
     */
    @Operation(summary = "发送密码修改安全通知邮件（用户自行操作）")
    public boolean sendPasswordChangedBySelfEmail(String email, String username) {
        // 查找 PASSWORD_CHANGED 类型的启用模板（复用同一类型，通过模板内容区分场景）
        Optional<EmailTemplate> templateOpt = emailTemplateRepository
                .findFirstByTypeAndEnabledTrue(EmailTemplate.TYPE_PASSWORD_CHANGED);
        String subject;
        String content;

        if (templateOpt.isPresent()) {
            EmailTemplate template = templateOpt.get();
            subject = template.getSubject();
            content = template.getContent();
        } else {
            subject = "【API Server】您的密码已被修改";
            content = "<div style='padding:20px;font-family:Arial,sans-serif;'>" +
                    "<h2 style='color:#667eea;'>API Server</h2>" +
                    "<p>您好，<strong>{{username}}</strong>：</p>" +
                    "<p>您的账号密码已于 <strong>{{time}}</strong> 被修改。</p>" +
                    "<p>如果这是您本人操作，请忽略此邮件。</p>" +
                    "<p style='color:#e74c3c;'><strong>如果不是您本人操作，您的账号可能存在安全风险，请立即联系管理员！</strong></p>" +
                    "<p style='color:#909399;'>访问地址：<a href='{{siteUrl}}' style='color:#667eea;'>{{siteUrl}}</a></p>" +
                    "<hr style='border:1px solid #eee;'/>" +
                    "<p style='color:#999;font-size:12px;'>此邮件由系统自动发送，请勿回复。如有疑问，请联系管理员。</p>" +
                    "</div>";
        }

        // 替换占位符（用户自行修改场景不需要密码占位符）
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        subject = replacePlaceholders(subject, username, email, currentTime, null);
        content = replacePlaceholders(content, username, email, currentTime, null);

        boolean sent = sendEmail(email, subject, content);
        if (sent) {
            log.info("密码修改安全通知已发送（用户自行操作）: email={}, username={}", email, username);
        } else {
            log.warn("密码修改安全通知发送失败（用户自行操作）: email={}", email);
        }
        return sent;
    }

    /**
     * 替换模板占位符。
     * <p>
     * 支持的占位符：
     * <ul>
     *   <li>{{@code {{username}}}} - 用户名</li>
     *   <li>{{@code {{email}}}} - 邮箱地址</li>
     *   <li>{{@code {{time}}}} / {{@code {{loginTime}}}} - 发送时间</li>
     *   <li>{{@code {{siteUrl}}}} / {{@code {{baseUrl}}}} - 系统访问地址（来自系统设置 siteBaseUrl）</li>
     *   <li>{{@code {{code}}}} - 验证码（REGISTER 类型）或新密码（RESET_PASSWORD/PASSWORD_CHANGED 类型）</li>
     *   <li>{{@code {{password}}}} / {{@code {{newPassword}}}} - 新密码，与 {{@code {{code}}}} 等价，推荐在密码类模板中使用</li>
     * </ul>
     *
     * @param text        模板文本
     * @param username    用户名
     * @param email       邮箱
     * @param currentTime 当前时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param codeOrPassword 验证码（REGISTER场景）或新密码（RESET_PASSWORD/PASSWORD_CHANGED场景）
     * @return 替换后的文本
     */
    private String replacePlaceholders(String text, String username, String email,
                                       String currentTime, String codeOrPassword) {
        if (text == null) return null;

        String result = text;
        if (username != null) {
            result = result.replace("{{username}}", username);
        }
        if (email != null) {
            result = result.replace("{{email}}", email);
        }
        if (currentTime != null) {
            result = result.replace("{{time}}", currentTime);
            result = result.replace("{{loginTime}}", currentTime);
        }

        // 系统访问地址（从系统配置读取）
        String siteUrl = systemConfigService.getConfig("siteBaseUrl");
        if (siteUrl != null && !siteUrl.isEmpty()) {
            result = result.replace("{{siteUrl}}", siteUrl);
            result = result.replace("{{baseUrl}}", siteUrl);
        }

        if (codeOrPassword != null) {
            result = result.replace("{{code}}", codeOrPassword);
            result = result.replace("{{password}}", codeOrPassword);
            result = result.replace("{{newPassword}}", codeOrPassword);
        }
        return result;
    }

    /**
     * 创建 JavaMailSender 实例
     */
    private JavaMailSender createMailSender(EmailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        if (config.getUseSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    /**
     * 从 MessagingException 中提取可读的错误信息
     * 递归查找根因，提取最具体的错误描述
     */
    private String extractErrorMessage(MessagingException e) {
        // 优先使用根因的消息
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof MessagingException) {
                // 继续深入查找
                Throwable inner = cause.getCause();
                if (inner != null) {
                    cause = inner;
                    continue;
                }
            }
            // 找到非 MessagingException 的根因
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.isEmpty()) {
                return formatErrorByType(causeMsg);
            }
            break;
        }

        // 如果没有根因或根因消息为空，使用当前异常的消息
        String msg = e.getMessage();
        if (msg != null && !msg.isEmpty()) {
            return formatErrorByType(msg);
        }
        return "邮件发送失败，请检查SMTP配置";
    }

    /**
     * 根据错误消息内容，返回更友好的中文提示
     */
    private String formatErrorByType(String rawMessage) {
        if (rawMessage == null) return "邮件发送失败";
        String lower = rawMessage.toLowerCase();

        if (lower.contains("authentication failed") || lower.contains("535") || lower.contains("auth")) {
            return "SMTP认证失败，请检查邮箱账号密码（可能需要使用应用专用密码）：" + rawMessage;
        } else if (lower.contains("connection refused") || lower.contains("connect exception") || lower.contains("timeout")) {
            return "无法连接到SMTP服务器，请检查SMTP地址和端口：" + rawMessage;
        } else if (lower.contains("relay") || lower.contains("denied")) {
            return "邮件发送被拒绝，发件人地址可能未经SMTP服务器授权：" + rawMessage;
        } else if (lower.contains("recipient") || lower.contains("rcpt")) {
            return "收件人地址无效或被拒绝：" + rawMessage;
        } else {
            return "邮件发送失败：" + rawMessage;
        }
    }
}
