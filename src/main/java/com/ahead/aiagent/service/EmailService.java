package com.ahead.aiagent.service;

import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮件发送服务（安全修复 E19/D6：找回密码验证码）。
 * 依赖 spring-boot-starter-mail；未配置 MAIL_HOST/MAIL_FROM 时给出明确错误，
 * 而不是让应用启动失败（修复 E25/O21 的降级原则）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    /** 修复：本地开发降级开关 —— SMTP 未配置且开关为 true 时，验证码仅打印 warn 日志、不抛异常，方便本地联调 */
    @Value("${app.mail.dev-log-code:false}")
    private boolean devLogCode;

    public void sendVerificationCode(String to, String code) {
        boolean mailConfigured = StringUtils.hasText(mailHost) && StringUtils.hasText(from);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        // 降级：邮件服务未就绪且开启 dev-log-code 时，验证码输出到日志并正常返回（仅限本地开发使用）
        if ((!mailConfigured || sender == null) && devLogCode) {
            log.warn("[DEV] 邮件服务未配置，找回密码验证码降级输出到日志: to={}, code={}", maskEmail(to), code);
            return;
        }
        if (!mailConfigured) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "邮件服务未配置，请联系管理员（需设置 MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD / MAIL_FROM 环境变量）");
        }
        if (sender == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件服务未初始化，请联系管理员");
        }
        try {
            MimeMessageHelper helper = new MimeMessageHelper(sender.createMimeMessage(), false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("【行境】找回密码验证码");
            helper.setText("""
                    您正在找回「行境」账号的登录密码。

                    验证码：%s

                    验证码 10 分钟内有效。若非本人操作，请忽略本邮件并注意账号安全。
                    """.formatted(code), false);
            sender.send(helper.getMimeMessage());
            log.info("找回密码验证码已发送至 {}", maskEmail(to));
        } catch (Exception e) {
            log.error("找回密码验证码发送失败: to={}", maskEmail(to), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "邮件发送失败，请稍后重试");
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
