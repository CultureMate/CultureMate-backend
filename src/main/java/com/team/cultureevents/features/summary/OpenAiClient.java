package com.team.cultureevents.features.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호출.
 * URL: https://api.openai.com/v1/chat/completions
 * SeoulOpenApiClient와 동일하게 RestClient + AppProperties + BusinessException 패턴을 따른다.
 */
@Component
public class OpenAiClient {

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final AppProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiClient(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /** system/user 프롬프트를 보내고, 응답 message.content 텍스트만 돌려준다. */
    public String chat(String systemPrompt, String userPrompt) {
        String key = props.openai().key();
        if (key == null || key.isBlank()) {
            throw unavailable("OPENAI_API_KEY가 설정되지 않았습니다.");
        }

        Map<String, Object> body = Map.of(
                "model", props.openai().model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + key)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw unavailable("OpenAI 호출에 실패했습니다.");
        }

        if (responseJson == null || responseJson.isBlank()) {
            throw unavailable("OpenAI 응답이 비어 있습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.at("/choices/0/message/content").asText(null);
            if (content == null || content.isBlank()) {
                throw unavailable("OpenAI 응답 형식이 올바르지 않습니다.");
            }
            return content.trim();
        } catch (IOException e) {
            throw unavailable("OpenAI 응답 파싱에 실패했습니다.");
        }
    }

    // 명세상 AI 소개문 실패는 503. BusinessException.upstream()은 502라서 직접 만든다.
    private static BusinessException unavailable(String message) {
        return new BusinessException("AI_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
