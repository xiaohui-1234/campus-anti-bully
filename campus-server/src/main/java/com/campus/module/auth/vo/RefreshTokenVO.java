package com.campus.module.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenVO {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
}
