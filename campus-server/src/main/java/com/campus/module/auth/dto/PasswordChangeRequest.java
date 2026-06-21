package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {

    @NotBlank(message = "请输入原密码")
    @Size(max = 128, message = "原密码长度不能超过128位")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(max = 128, message = "新密码长度不能超过128位")
    private String newPassword;
}
