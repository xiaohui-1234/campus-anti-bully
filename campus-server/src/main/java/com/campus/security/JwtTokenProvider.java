package com.campus.security;

import com.campus.common.exception.BizException;
import com.campus.common.util.HashUtil;
import com.campus.config.CampusProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final CampusProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(CampusProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(HashUtil.sha256Bytes(properties.getSecurity().getJwt().getSecret()));
    }

    public String createAccessToken(LoginUser loginUser) {
        return createToken(loginUser, TYPE_ACCESS, properties.getSecurity().getJwt().getAccessTokenExpireSeconds());
    }

    public String createRefreshToken(LoginUser loginUser) {
        return createToken(loginUser, TYPE_REFRESH, properties.getSecurity().getJwt().getRefreshTokenExpireSeconds());
    }

    public LoginUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw BizException.unauthorized("token 类型错误");
        }
        return new LoginUser(claims.get("uid", Long.class), claims.getSubject(), claims.get("role", String.class));
    }

    public LoginUser parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw BizException.unauthorized("refresh_token 类型错误");
        }
        return new LoginUser(claims.get("uid", Long.class), claims.getSubject(), claims.get("role", String.class));
    }

    public String tokenId(String token) {
        return parseClaims(token).getId();
    }

    public long accessTokenExpiresIn() {
        return properties.getSecurity().getJwt().getAccessTokenExpireSeconds();
    }

    private String createToken(LoginUser loginUser, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(loginUser.getUserId())
                .claim("uid", loginUser.getUserTableId())
                .claim("role", loginUser.getRole())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw BizException.unauthorized("token 无效或已过期");
        }
    }
}
