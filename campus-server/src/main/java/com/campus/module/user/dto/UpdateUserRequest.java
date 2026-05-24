package com.campus.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 64)
    private String nickname;

    @Size(max = 32)
    private String phone;

    @Email
    @Size(max = 128)
    private String email;
}
