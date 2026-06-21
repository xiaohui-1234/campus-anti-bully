package com.campus.security;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginUser {

    private Long userTableId;
    private String userId;
    private String role;
    private Integer tokenVersion = 0;

    public LoginUser(Long userTableId, String userId, String role) {
        this(userTableId, userId, role, 0);
    }

    public LoginUser(Long userTableId, String userId, String role, Integer tokenVersion) {
        this.userTableId = userTableId;
        this.userId = userId;
        this.role = role;
        this.tokenVersion = tokenVersion == null ? 0 : tokenVersion;
    }
}
