package com.team.cultureevents.features.commons.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        SeoulApi seoulApi,
        Openai openai,
        Kakao kakao,
        String frontendUrl
) {
    public record Cors(String allowedOrigin) {}

    public record SeoulApi(
            String key,
            String baseUrl,
            int cacheTtlMinutes,
            int pageSize
    ) {}

    public record Openai(String key, String model) {}

    public record Kakao(String restKey, String clientSecret, String redirectUri) {}
}
