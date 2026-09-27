package com.team.cultureevents.features.commons.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        SeoulApi seoulApi,
        Openai openai,
        Kakao kakao,
        Places places,
        String frontendUrl
) {
    /** places 없이 생성하는 경우(기존 테스트 호환용). */
    public AppProperties(Cors cors, SeoulApi seoulApi, Openai openai, Kakao kakao, String frontendUrl) {
        this(cors, seoulApi, openai, kakao, null, frontendUrl);
    }

    public record Cors(String allowedOrigin) {}

    public record SeoulApi(
            String key,
            String baseUrl,
            int cacheTtlMinutes,
            int pageSize
    ) {}

    public record Openai(String key, String model) {}

    public record Kakao(String restKey, String clientSecret, String redirectUri) {}

    public record Places(String key) {}
}
