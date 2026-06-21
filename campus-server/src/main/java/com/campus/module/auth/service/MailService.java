package com.campus.module.auth.service;

import com.campus.config.CampusProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final CampusProperties properties;

    public void sendEmailCode(String email, String code, long ttlSeconds) {
        if (properties.getSecurity().getEmailCode().isMock()) {
            log.info("Mock email code, email={}, code={}, ttl_seconds={}", email, code, ttlSeconds);
            return;
        }
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("JavaMailSender is not configured");
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            String from = properties.getSecurity().getEmailCode().getFrom();
            if (StringUtils.hasText(from)) {
                helper.setFrom(from);
            }
            helper.setTo(email);
            helper.setSubject("校园安全验证码");
            helper.setText(buildPlainText(code, ttlSeconds), buildHtmlText(code, ttlSeconds));
            javaMailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build email code message", ex);
        }
    }

    private String buildPlainText(String code, long ttlSeconds) {
        return "你的校园安全验证码是：" + code + "。验证码将在 " + ttlMinutes(ttlSeconds)
                + " 分钟后失效。如非本人操作，请忽略此邮件，并及时检查账号登录状态。";
    }

    private String buildHtmlText(String code, long ttlSeconds) {
        String safeCode = escapeHtml(code);
        long minutes = ttlMinutes(ttlSeconds);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>校园安全验证码</title>
                </head>
                <body style="margin:0;padding:0;background:#dbe6f2;font-family:Arial,'Microsoft YaHei','PingFang SC',sans-serif;color:#111827;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="min-height:100vh;background:#dbe6f2;padding:34px 12px;">
                    <tr>
                      <td align="center">

                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:640px;border-radius:22px;overflow:hidden;background:#dbe6f2;">
                          <tr>
                            <td style="padding:0;background:linear-gradient(135deg,#9fb3c8 0%,#d8e4ef 38%,#f7ead8 68%,#b8cad8 100%);">
                              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:rgba(15,23,42,0.18);">
                                <tr>
                                  <td style="padding:34px 34px 90px 34px;background:radial-gradient(circle at 18% 20%,rgba(255,255,255,0.75) 0,rgba(255,255,255,0.28) 16%,rgba(255,255,255,0) 32%),radial-gradient(circle at 82% 28%,rgba(255,244,214,0.78) 0,rgba(255,244,214,0.22) 18%,rgba(255,244,214,0) 34%),radial-gradient(circle at 50% 78%,rgba(255,255,255,0.65) 0,rgba(255,255,255,0.16) 24%,rgba(255,255,255,0) 46%);">

                                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:rgba(238,242,247,0.72);border:1px solid rgba(255,255,255,0.56);border-radius:18px;box-shadow:0 18px 46px rgba(15,23,42,0.20);">
                                      <tr>
                                        <td style="padding:22px 26px 18px 26px;">
                                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
                                            <tr>
                                              <td valign="middle">
                                                <div style="font-size:22px;font-weight:800;line-height:30px;color:#172033;">校园防霸凌系统</div>
                                                <div style="margin-top:4px;font-size:13px;line-height:20px;color:#415166;">Campus Anti-Bullying Security Center</div>
                                              </td>
                                              <td align="right" valign="middle" style="width:86px;">
                                                <div style="display:inline-block;padding:6px 11px;border-radius:999px;background:rgba(15,118,110,0.14);border:1px solid rgba(15,118,110,0.22);font-size:12px;font-weight:700;color:#0f766e;">验证中</div>
                                              </td>
                                            </tr>
                                          </table>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:0 26px;">
                                          <div style="height:1px;background:rgba(148,163,184,0.45);line-height:1px;font-size:1px;">&nbsp;</div>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:24px 26px 8px 26px;">
                                          <div style="font-size:18px;font-weight:800;line-height:28px;color:#111827;">请使用以下验证码完成操作</div>
                                          <div style="margin-top:8px;font-size:14px;line-height:24px;color:#475569;">该验证码用于确认你的身份。请勿转发、截图或告知他人。</div>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:18px 26px 18px 26px;">
                                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:rgba(255,255,255,0.58);border:1px solid rgba(203,213,225,0.86);border-radius:16px;">
                                            <tr>
                                              <td align="center" style="padding:24px 12px 22px 12px;">
                                                <div style="font-size:12px;line-height:18px;color:#64748b;font-weight:700;letter-spacing:2px;">VERIFICATION CODE</div>
                                                <div style="margin-top:10px;letter-spacing:11px;font-size:44px;line-height:56px;font-weight:800;color:#172033;text-shadow:0 1px 0 rgba(255,255,255,0.75);">{{code}}</div>
                                                <div style="margin-top:8px;font-size:13px;line-height:20px;color:#64748b;">验证码将在 {{minutes}} 分钟后失效</div>
                                              </td>
                                            </tr>
                                          </table>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:0 26px 24px 26px;">
                                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:rgba(255,247,237,0.78);border:1px solid rgba(253,186,116,0.75);border-radius:14px;">
                                            <tr>
                                              <td style="padding:14px 16px;font-size:13px;line-height:22px;color:#9a3412;">
                                                <strong style="color:#7c2d12;">安全提醒：</strong>
                                                如非本人操作，请忽略此邮件，并及时检查账号登录状态。
                                              </td>
                                            </tr>
                                          </table>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:0 26px 24px 26px;">
                                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
                                            <tr>
                                              <td style="padding:10px 0;font-size:13px;line-height:22px;color:#475569;border-top:1px solid rgba(148,163,184,0.30);">
                                                <span style="display:inline-block;width:22px;font-weight:800;color:#0f766e;">01</span>
                                                验证码仅用于本次身份确认，系统不会要求你在邮件中提供密码。
                                              </td>
                                            </tr>
                                            <tr>
                                              <td style="padding:10px 0;font-size:13px;line-height:22px;color:#475569;border-top:1px solid rgba(148,163,184,0.30);">
                                                <span style="display:inline-block;width:22px;font-weight:800;color:#0f766e;">02</span>
                                                请在有效期内完成验证，过期后需要重新获取。
                                              </td>
                                            </tr>
                                          </table>
                                        </td>
                                      </tr>

                                      <tr>
                                        <td style="padding:16px 26px 18px 26px;background:rgba(248,250,252,0.60);border-top:1px solid rgba(148,163,184,0.35);">
                                          <div style="font-size:12px;line-height:20px;color:#64748b;">此邮件由系统自动发送，请勿直接回复。</div>
                                          <div style="margin-top:4px;font-size:12px;line-height:20px;color:#94a3b8;">Campus Anti-Bullying System · Account Security Verification</div>
                                        </td>
                                      </tr>
                                    </table>

                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>

                        <div style="max-width:640px;margin-top:14px;font-size:12px;line-height:18px;color:#7b8796;text-align:center;">
                          为了你的账号安全，请勿向任何人泄露验证码。
                        </div>

                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{code}}", safeCode)
                .replace("{{minutes}}", String.valueOf(minutes));
    }

    private long ttlMinutes(long ttlSeconds) {
        return Math.max(1, ttlSeconds / 60);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
