package com.team.cultureevents.features.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KakaoApiClientTest {
    @Test
    void badClientSecretGivesActionableErrorWithoutEchoingCredentials() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\",\"error_code\":\"KOE010\"}"));
        AppProperties props = new AppProperties(null, null, null,
                new AppProperties.Kakao("test-key", "test-secret", "http://localhost:8080/api/auth/kakao/callback"),
                "http://localhost:5175");
        KakaoApiClient client = new KakaoApiClient(props, builder, new ObjectMapper());

        assertThatThrownBy(() -> client.fetchProfile("fake-code"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("UPSTREAM_UNAVAILABLE");
                    assertThat(ex.getMessage()).contains("KOE010", "Client Secret")
                            .doesNotContain("test-secret", "test-key");
                });
        server.verify();
    }
}
