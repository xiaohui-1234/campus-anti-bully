package com.campus.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String openidHash;
    private String securityEmailHash;
    private String securityEmailCipher;
    private Boolean securityEmailVerified;
    private LocalDateTime securityEmailVerifiedTime;
    private LocalDateTime securityEmailUpdateTime;
    private String passwordHash;
    private LocalDateTime passwordSetTime;
    private Integer tokenVersion;
    private String nickname;
    private String email;
    private String phone;
    private String avatarUrl;
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
