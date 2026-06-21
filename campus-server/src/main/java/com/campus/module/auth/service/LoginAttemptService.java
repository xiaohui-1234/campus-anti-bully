package com.campus.module.auth.service;

import com.campus.common.constants.RedisKeys;
import com.campus.common.exception.BizException;
import com.campus.config.CampusProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;
    private final AccountCryptoService accountCryptoService;

    public void assertAllowed(String loginId, String ip) {
        if (!properties.getSecurity().getLoginLimit().isEnabled()) {
            return;
        }
        if (isLocked(RedisKeys.loginFailSubject(subjectHash(loginId))) || isLocked(RedisKeys.loginFailIp(ipHash(ip)))) {
            throw BizException.unauthorized("账号或密码错误");
        }
    }

    public void recordFailure(String loginId, String ip) {
        if (!properties.getSecurity().getLoginLimit().isEnabled()) {
            return;
        }
        increase(RedisKeys.loginFailSubject(subjectHash(loginId)));
        increase(RedisKeys.loginFailIp(ipHash(ip)));
    }

    public void clearSubject(String loginId) {
        stringRedisTemplate.delete(RedisKeys.loginFailSubject(subjectHash(loginId)));
    }

    private boolean isLocked(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value) >= properties.getSecurity().getLoginLimit().getMaxFailCount();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void increase(String key) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, properties.getSecurity().getLoginLimit().getLockSeconds(), TimeUnit.SECONDS);
        }
    }

    private String subjectHash(String loginId) {
        return accountCryptoService.hmacHex(StringUtils.hasText(loginId) ? loginId.trim() : "unknown");
    }

    private String ipHash(String ip) {
        return accountCryptoService.hmacHex(StringUtils.hasText(ip) ? ip : "unknown");
    }
}
