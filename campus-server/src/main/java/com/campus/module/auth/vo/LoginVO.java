package com.campus.module.auth.vo;

import com.campus.module.user.vo.UserInfoVO;
import lombok.Data;

@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfoVO userInfo;
}
