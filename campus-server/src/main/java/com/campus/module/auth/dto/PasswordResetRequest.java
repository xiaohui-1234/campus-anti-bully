package com.campus.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {

    @NotBlank(message = "请输入安全邮箱")
    @Email(message = "请输入正确的邮箱")
    @Size(max = 128, message = "安全邮箱长度不能超过128位")
    private String securityEmail;

    @NotBlank(message = "请输入验证码")
    @Size(max = 16, message = "验证码长度不能超过16位")
    private String verifyCode;

    @NotBlank(message = "请输入新密码")
    @Size(max = 128, message = "新密码长度不能超过128位")
    private String newPassword;
}
