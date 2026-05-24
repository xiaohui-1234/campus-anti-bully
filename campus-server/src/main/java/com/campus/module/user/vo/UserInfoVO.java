package com.campus.module.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoVO {

    private String userId;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private Boolean isNewUser;
}
