package com.campus.module.auth.service;

import com.campus.common.constants.RedisKeys;
import com.campus.common.exception.BizException;
import com.campus.config.CampusProperties;
import com.campus.module.auth.enums.EmailCodeScene;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final String SENT = "SENT";
    private static final Long VERIFIED = 1L;
    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
                    local saved = redis.call('GET', KEYS[1])
                    if (not saved) or saved ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    redis.call('DEL', KEYS[2])
                    return 1
            """,
            Long.class
    );
    private static final DefaultRedisScript<Long> VERIFY_CODE_AND_GRANT_SCRIPT = new DefaultRedisScript<>(
            """
                    local grant = redis.call('GET', KEYS[3])
                    if (not grant) or grant ~= ARGV[2] then
                        return 0
                    end
                    local current = redis.call('GET', KEYS[4])
                    if (not current) or current ~= ARGV[3] then
                        return 0
                    end
                    local saved = redis.call('GET', KEYS[1])
                    if (not saved) or saved ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    redis.call('DEL', KEYS[2])
                    redis.call('DEL', KEYS[3])
                    redis.call('DEL', KEYS[4])
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final CampusProperties properties;
    private final AccountCryptoService accountCryptoService;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void send(EmailCodeScene scene, String email, String ip) {
        String normalizedEmail = accountCryptoService.normalizeEmail(email);
        String emailHash = accountCryptoService.emailHash(normalizedEmail);
        String ipHash = accountCryptoService.hmacHex(StringUtils.hasText(ip) ? ip : "unknown");
        String ipLimitKey = RedisKeys.emailCodeIpLimit(scene.name(), ipHash);
        Long ipCount = stringRedisTemplate.opsForValue().increment(ipLimitKey);
        if (ipCount != null && ipCount == 1) {
            stringRedisTemplate.expire(
                    ipLimitKey,
                    properties.getSecurity().getEmailCode().getTtlSeconds(),
                    TimeUnit.SECONDS
            );
        }
        if (ipCount != null && ipCount > properties.getSecurity().getEmailCode().getMaxIpSends()) {
            throw new BizException(429, "验证码发送过于频繁，请稍后再试");
        }
        String resendKey = RedisKeys.emailCodeResendLimit(scene.name(), emailHash);
        Boolean firstSend = stringRedisTemplate.opsForValue().setIfAbsent(
                resendKey,
                SENT,
                properties.getSecurity().getEmailCode().getResendIntervalSeconds(),
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(firstSend)) {
            throw new BizException(429, "验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode(properties.getSecurity().getEmailCode().getCodeLength());
        String codeHash = codeHash(scene, emailHash, code);
        stringRedisTemplate.opsForValue().set(
                RedisKeys.emailCode(scene.name(), emailHash),
                codeHash,
                properties.getSecurity().getEmailCode().getTtlSeconds(),
                TimeUnit.SECONDS
        );
        stringRedisTemplate.delete(RedisKeys.emailCodeAttempt(scene.name(), emailHash));
        mailService.sendEmailCode(normalizedEmail, code, properties.getSecurity().getEmailCode().getTtlSeconds());
    }

    public void verifyAndConsume(EmailCodeScene scene, String email, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(-1, "请输入验证码");
        }
        String normalizedEmail = accountCryptoService.normalizeEmail(email);
        String emailHash = accountCryptoService.emailHash(normalizedEmail);
        String attemptKey = assertAttemptAllowed(scene, emailHash);
        String codeKey = RedisKeys.emailCode(scene.name(), emailHash);
        Long verified = stringRedisTemplate.execute(
                VERIFY_AND_CONSUME_SCRIPT,
                List.of(codeKey, attemptKey),
                codeHash(scene, emailHash, code)
        );
        if (!VERIFIED.equals(verified)) {
            throw new BizException(-1, "验证码错误或已过期");
        }
    }

    public void verifyAndConsumeWithGrant(EmailCodeScene scene, String email, String code,
                                          String grantKey, String currentGrantKey,
                                          String expectedGrantValue, String ticketHash) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(-1, "请输入验证码");
        }
        if (!StringUtils.hasText(grantKey)
                || !StringUtils.hasText(currentGrantKey)
                || !StringUtils.hasText(expectedGrantValue)
                || !StringUtils.hasText(ticketHash)) {
            throw new BizException(-1, "邮箱修改凭证无效，请重新验证旧邮箱");
        }
        String normalizedEmail = accountCryptoService.normalizeEmail(email);
        String emailHash = accountCryptoService.emailHash(normalizedEmail);
        String attemptKey = assertAttemptAllowed(scene, emailHash);
        String codeKey = RedisKeys.emailCode(scene.name(), emailHash);
        Long verified = stringRedisTemplate.execute(
                VERIFY_CODE_AND_GRANT_SCRIPT,
                List.of(codeKey, attemptKey, grantKey, currentGrantKey),
                codeHash(scene, emailHash, code),
                expectedGrantValue,
                ticketHash
        );
        if (!VERIFIED.equals(verified)) {
            throw new BizException(-1, "验证码错误或邮箱修改凭证已过期");
        }
    }

    private String codeHash(EmailCodeScene scene, String emailHash, String code) {
        return accountCryptoService.hmacHex(scene.name() + ":" + emailHash + ":" + code);
    }

    private String assertAttemptAllowed(EmailCodeScene scene, String emailHash) {
        String attemptKey = RedisKeys.emailCodeAttempt(scene.name(), emailHash);
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) {
            stringRedisTemplate.expire(attemptKey, properties.getSecurity().getEmailCode().getTtlSeconds(), TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > properties.getSecurity().getEmailCode().getMaxAttempts()) {
            throw new BizException(429, "验证码错误次数过多，请重新获取");
        }
        return attemptKey;
    }

    private String generateCode(int length) {
        int safeLength = Math.max(4, Math.min(length, 8));
        int bound = (int) Math.pow(10, safeLength);
        int min = (int) Math.pow(10, safeLength - 1);
        return String.valueOf(min + secureRandom.nextInt(bound - min));
    }
}
