package com.campus.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SecurityEmailChangeConfirmRequest {

    @NotBlank(message = "请先验证旧邮箱")
    @Size(max = 128, message = "邮箱修改凭证长度不能超过128位")
    private String changeTicket;

    @NotBlank(message = "请输入新的安全邮箱")
    @Email(message = "请输入正确的邮箱")
    @Size(max = 128, message = "安全邮箱长度不能超过128位")
    private String newSecurityEmail;

    @NotBlank(message = "请输入验证码")
    @Size(max = 16, message = "验证码长度不能超过16位")
    private String newVerifyCode;
}
