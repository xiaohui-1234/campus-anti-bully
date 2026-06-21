package com.campus.module.auth.service;

import com.campus.common.exception.BizException;
import com.campus.config.CampusProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final CampusProperties properties;

    public void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BizException(-1, "请输入密码");
        }
        int min = properties.getSecurity().getPassword().getMinLength();
        int max = properties.getSecurity().getPassword().getMaxLength();
        if (password.length() < min || password.length() > max) {
            throw new BizException(-1, "密码长度需为" + min + "到" + max + "位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(-1, "密码需包含字母和数字");
        }
    }
}
