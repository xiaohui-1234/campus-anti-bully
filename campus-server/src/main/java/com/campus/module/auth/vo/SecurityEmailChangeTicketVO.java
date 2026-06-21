package com.campus.module.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SecurityEmailChangeTicketVO {

    private String changeTicket;
    private long expiresIn;
}
