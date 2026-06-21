package com.campus.module.auth.service;

import com.campus.common.exception.BizException;
import com.campus.common.util.HashUtil;
import com.campus.config.CampusProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountCryptoService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final CampusProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(-1, "请输入安全邮箱");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String hmacHex(String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getSecurity().getCrypto().getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));
            byte[] bytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    public String emailHash(String email) {
        return hmacHex(normalizeEmail(email));
    }

    public String encryptEmail(String email) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalizeEmail(email).getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Email encryption failed", ex);
        }
    }

    public String decryptEmail(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            throw new BizException(-1, "当前账号未绑定安全邮箱");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(-1, "安全邮箱读取失败，请联系管理员");
        }
    }

    public String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = normalizeEmail(email);
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        String prefix = local.substring(0, 1);
        return prefix + "***" + domain;
    }

    public String maskEncryptedEmail(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        return maskEmail(decryptEmail(cipherText));
    }

    private SecretKeySpec aesKey() {
        byte[] key = HashUtil.sha256Bytes(properties.getSecurity().getCrypto().getAesKey());
        return new SecretKeySpec(key, AES_ALGORITHM);
    }
}
