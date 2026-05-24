package com.campus.security;

import com.campus.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw BizException.unauthorized("未登录或 token 失效");
        }
        return loginUser;
    }
}
