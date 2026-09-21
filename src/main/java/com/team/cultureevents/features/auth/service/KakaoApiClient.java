package com.team.cultureevents.features.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoApiClient {
    private final AppProperties.Kakao settings;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoApiClient(AppProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.settings = properties.kakao();
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public KakaoProfile fetchProfile(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", settings.restKey());
        form.add("redirect_uri", settings.redirectUri());
        form.add("code", code);
        if (settings.clientSecret() != null && !settings.clientSecret().isBlank()) {
            form.add("client_secret", settings.clientSecret());
        }

        try {
            JsonNode token = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = token == null ? "" : token.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw BusinessException.upstream("카카오 토큰 응답에 access_token이 없습니다.");
            }
            JsonNode user = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
            String kakaoId = user == null ? "" : user.path("id").asText("");
            if (kakaoId.isBlank()) {
                throw BusinessException.upstream("카카오 사용자 응답에 id가 없습니다.");
            }
            String nickname = user.path("kakao_account").path("profile").path("nickname").asText("");
            if (nickname.isBlank()) {
                nickname = "카카오 사용자";
            }
            return new KakaoProfile(kakaoId, nickname.length() > 50 ? nickname.substring(0, 50) : nickname);
        } catch (RestClientResponseException ex) {
            String errorCode = "";
            try {
                errorCode = objectMapper.readTree(ex.getResponseBodyAsString())
                        .path("error_code").asText("");
            } catch (Exception ignored) {
                // 카카오 오류 본문이 JSON이 아니어도 인증정보나 원문을 응답으로 노출하지 않는다.
            }
            if ("KOE010".equals(errorCode)) {
                throw BusinessException.upstream("카카오 로그인 Client Secret이 올바르지 않습니다 (KOE010). 같은 REST API 키의 카카오 로그인 코드를 확인해 주세요.");
            }
            if ("KOE320".equals(errorCode)) {
                throw BusinessException.badRequest("카카오 인가 코드가 만료되었거나 이미 사용되었습니다. 로그인을 다시 시작해 주세요 (KOE320).");
            }
            throw BusinessException.upstream("카카오 로그인 서버가 요청을 거부했습니다" + (errorCode.isBlank() ? "." : " (" + errorCode + ")."));
        } catch (RestClientException ex) {
            throw BusinessException.upstream("카카오 로그인 서버 요청에 실패했습니다.");
        }
    }

    public record KakaoProfile(String id, String nickname) {}
}
