package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SecurityEmailChangeVerifyOldRequest {

    @NotBlank(message = "请输入验证码")
    @Size(max = 16, message = "验证码长度不能超过16位")
    private String oldVerifyCode;
}
