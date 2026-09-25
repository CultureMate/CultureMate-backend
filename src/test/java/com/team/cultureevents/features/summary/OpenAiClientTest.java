package com.team.cultureevents.features.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatSendsMaxTokensAndReturnsTrimmedContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OpenAiClient.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"model":"gpt-test","max_tokens":300}
                        """, false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"  안녕하세요.  "}}]}
                        """, MediaType.APPLICATION_JSON));

        String content = new OpenAiClient(props("sk-test"), builder.build(), objectMapper).chat("system", "user");

        assertThat(content).isEqualTo("안녕하세요.");
        server.verify();
    }

    @Test
    void missingKeyIs503WithoutCallingOpenAi() {
        OpenAiClient client = new OpenAiClient(props("  "), RestClient.builder().build(), objectMapper);

        assertThatThrownBy(() -> client.chat("system", "user"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AI_UNAVAILABLE");
    }

    @Test
    void blankOrInvalidBodyIs503() {
        assertUnavailable("{\"choices\":[{\"message\":{\"content\":\"   \"}}]}");
        assertUnavailable("{}");
        assertUnavailable("not-json");
    }

    @Test
    void httpFailureIs503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OpenAiClient.ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> new OpenAiClient(props("sk-test"), builder.build(), objectMapper).chat("system", "user"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AI_UNAVAILABLE");
    }

    private void assertUnavailable(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OpenAiClient.ENDPOINT))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OpenAiClient(props("sk-test"), builder.build(), objectMapper).chat("s", "u"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AI_UNAVAILABLE");
    }

    private static AppProperties props(String key) {
        return new AppProperties(null, null, new AppProperties.Openai(key, "gpt-test"), null, null);
    }
}
