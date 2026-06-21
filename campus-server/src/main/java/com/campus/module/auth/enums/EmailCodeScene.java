package com.campus.module.auth.enums;

import com.campus.common.exception.BizException;

import java.util.Locale;

public enum EmailCodeScene {
    PASSWORD_REGISTER(false, true),
    ACTIVATE_ACCOUNT(true, true),
    RESET_PASSWORD(false, true),
    CHANGE_SECURITY_EMAIL_OLD(true, false),
    CHANGE_SECURITY_EMAIL_NEW(true, true);

    private final boolean loginRequired;
    private final boolean emailRequired;

    EmailCodeScene(boolean loginRequired, boolean emailRequired) {
        this.loginRequired = loginRequired;
        this.emailRequired = emailRequired;
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }

    public boolean isEmailRequired() {
        return emailRequired;
    }

    public static EmailCodeScene from(String raw) {
        try {
            return EmailCodeScene.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BizException(-1, "验证码场景不支持");
        }
    }
}
