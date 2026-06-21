package com.campus.module.auth.service;

import com.campus.common.exception.BizException;
import com.campus.config.CampusProperties;
import com.campus.module.auth.enums.EmailCodeScene;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSecurityServiceTest {

    @Test
    void doesNotSetEmailResendLimitWhenIpSendLimitExceeded() {
        CampusProperties properties = new CampusProperties();
        properties.getSecurity().getEmailCode().setMaxIpSends(1);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);
        EmailCodeService service = new EmailCodeService(
                redis,
                properties,
                new AccountCryptoService(properties),
                mock(MailService.class)
        );

        assertThrows(BizException.class,
                () -> service.send(EmailCodeScene.PASSWORD_REGISTER, "student@example.com", "127.0.0.1"));

        verify(valueOperations, never()).setIfAbsent(anyString(), eq("SENT"), anyLong(), eq(TimeUnit.SECONDS));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void successfulPasswordLoginOnlyClearsSubjectFailureCount() {
        CampusProperties properties = new CampusProperties();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LoginAttemptService service = new LoginAttemptService(redis, properties, new AccountCryptoService(properties));

        service.clearSubject("student@example.com");

        verify(redis).delete(startsWith("auth:login_fail:subject:"));
        verify(redis, never()).delete(startsWith("auth:login_fail:ip:"));
    }
}
