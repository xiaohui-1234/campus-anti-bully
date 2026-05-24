package com.campus.module.websocket.handler;

import com.campus.security.JwtTokenProvider;
import com.campus.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String LOGIN_USER_ATTR = "loginUser";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket handshake rejected, token missing");
            return false;
        }
        try {
            LoginUser loginUser = jwtTokenProvider.parseAccessToken(token);
            attributes.put(LOGIN_USER_ATTR, loginUser);
            return true;
        } catch (Exception ex) {
            log.warn("WebSocket handshake rejected, token invalid");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
    }
}
