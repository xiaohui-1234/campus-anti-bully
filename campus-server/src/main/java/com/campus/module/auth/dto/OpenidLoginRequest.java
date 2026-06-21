package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpenidLoginRequest {

    @NotBlank(message = "登录参数错误")
    private String openid;
}
