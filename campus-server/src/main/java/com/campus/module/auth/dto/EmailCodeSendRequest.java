package com.campus.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailCodeSendRequest {

    @NotBlank(message = "请选择验证码场景")
    private String scene;

    @Email(message = "请输入正确的邮箱")
    @Size(max = 128, message = "安全邮箱长度不能超过128位")
    private String securityEmail;

    @Size(max = 128, message = "邮箱修改凭证长度不能超过128位")
    private String changeTicket;
}
