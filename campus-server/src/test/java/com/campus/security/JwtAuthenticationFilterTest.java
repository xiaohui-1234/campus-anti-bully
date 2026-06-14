package com.campus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clearsSecurityContextAfterAuthenticatedRequest() throws Exception {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer access-token");
        when(jwtTokenProvider.tokenId("access-token")).thenReturn("token-id");
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(new LoginUser(1L, "user-1", "USER"));

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertNotNull(SecurityContextHolder.getContext().getAuthentication()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void clearsExistingSecurityContextWhenRequestHasNoToken() throws Exception {
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(mock(org.springframework.security.core.Authentication.class));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
