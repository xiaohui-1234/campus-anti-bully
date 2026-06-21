package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordLoginRequest {

    @NotBlank(message = "请输入用户ID或安全邮箱")
    @Size(max = 128, message = "登录账号长度不能超过128位")
    private String loginId;

    @NotBlank(message = "请输入密码")
    @Size(max = 128, message = "密码长度不能超过128位")
    private String password;
}
