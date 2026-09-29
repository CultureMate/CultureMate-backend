package com.team.cultureevents.features.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoApiClientTest {
    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

    @Test
    void badClientSecretGivesActionableErrorWithoutEchoingCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\",\"error_code\":\"KOE010\"}"));
        KakaoApiClient client = new KakaoApiClient(props(""), builder, new ObjectMapper());

        assertThatThrownBy(() -> client.fetchProfile("fake-code"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
                    assertThat(ex.getMessage()).contains("KOE010", "Client Secret")
                            .doesNotContain("test-secret", "test-key");
                });
        server.verify();
    }

    @Test
    void unlinkSendsAdminKeyAndKakaoUserId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-admin"))
                .andExpect(content().formDataContains(Map.of(
                        "target_id_type", "user_id",
                        "target_id", "12345")))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));
        KakaoApiClient client = new KakaoApiClient(props("test-admin"), builder, new ObjectMapper());

        assertThat(client.unlink("12345")).isTrue();
        server.verify();
    }

    @Test
    void unlinkIsSkippedWithoutAdminKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoApiClient client = new KakaoApiClient(props(""), builder, new ObjectMapper());

        assertThat(client.unlink("12345")).isFalse();
        server.verify();
    }

    @Test
    void unlinkFailureDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"wrong appKey\",\"code\":-401}"));
        KakaoApiClient client = new KakaoApiClient(props("wrong-admin"), builder, new ObjectMapper());

        assertThat(client.unlink("12345")).isFalse();
        server.verify();
    }

    private static AppProperties props(String adminKey) {
        return new AppProperties(null, null, null,
                new AppProperties.Kakao("test-key", "test-secret", "http://localhost:8080/api/auth/kakao/callback", adminKey),
                null, "http://localhost:5175");
    }
}
