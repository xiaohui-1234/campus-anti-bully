package com.campus.infrastructure.wx;

import com.campus.common.exception.BizException;
import com.campus.config.CampusProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class WxMiniAppClient {

    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final CampusProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public String exchangeCodeForOpenid(String code) {
        CampusProperties.Miniapp miniapp = properties.getWx().getMiniapp();
        if (!StringUtils.hasText(miniapp.getAppId()) || miniapp.getAppId().startsWith("your-")) {
            log.warn("Using local development openid fallback for wx login");
            return "dev_openid_" + code;
        }
        String url = UriComponentsBuilder.fromHttpUrl(CODE2SESSION_URL)
                .queryParam("appid", miniapp.getAppId())
                .queryParam("secret", miniapp.getAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();
        String response = restTemplate.getForObject(url, String.class);
        JsonNode body = parseResponse(response);
        JsonNode openidNode = body.get("openid");
        if (openidNode == null || !StringUtils.hasText(openidNode.asText())) {
            log.warn("wx code2session failed, errcode={}, errmsg={}",
                    text(body, "errcode"), text(body, "errmsg"));
            throw new BizException("登录失败");
        }
        return openidNode.asText();
    }

    private JsonNode parseResponse(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            log.warn("wx code2session returned invalid json");
            throw new BizException("登录失败");
        }
    }

    private String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null ? "" : node.asText();
    }
}
