package com.team.cultureevents.features.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 호출.
 * URL: https://api.openai.com/v1/chat/completions
 * SeoulOpenApiClient와 동일하게 RestClient + AppProperties + BusinessException 패턴을 따른다.
 */
@Component
public class OpenAiClient {

    static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    static final int MAX_OUTPUT_TOKENS = 300;
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final AppProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiClient(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this(props, openAiRestClient(builder), objectMapper);
    }

    OpenAiClient(AppProperties props, RestClient restClient, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /** 공통 RestClient(읽기 5초)와 분리한다. OpenAI만 읽기 30초. */
    private static RestClient openAiRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.clone().requestFactory(requestFactory).build();
    }

    /** system/user 프롬프트를 보내고, 응답 message.content 텍스트만 돌려준다. */
    public String chat(String systemPrompt, String userPrompt) {
        String key = props.openai().key();
        if (key == null || key.isBlank()) {
            throw unavailable("OPENAI_API_KEY가 설정되지 않았습니다.");
        }

        Map<String, Object> body = Map.of(
                "model", props.openai().model(),
                "max_tokens", MAX_OUTPUT_TOKENS,
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
