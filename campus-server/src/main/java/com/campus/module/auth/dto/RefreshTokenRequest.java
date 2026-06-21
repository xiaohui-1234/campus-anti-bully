package com.campus.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "登录状态已失效，请重新登录")
    private String refreshToken;
}
