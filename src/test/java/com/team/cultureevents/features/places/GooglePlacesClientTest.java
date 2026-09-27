package com.team.cultureevents.features.places;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.domain.entity.PlacesApiUsageEntity;
import com.team.cultureevents.features.places.repository.PlacesApiUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GooglePlacesClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SUCCESS_BODY = """
            {"places":[{"id":"p1","displayName":{"text":"테스트 카페"},"formattedAddress":"주소",
            "rating":4.5,"userRatingCount":100,"location":{"latitude":37.5,"longitude":127.0},
            "googleMapsUri":"https://maps.google.com/?cid=1"}]}
            """;

    @Test
    void missingKeyIs503WithoutCallingGoogle() {
        GooglePlacesClient client = new GooglePlacesClient(
                props("  "), RestClient.builder().build(), objectMapper, mock(PlacesApiUsageRepository.class));

        assertThatThrownBy(() -> client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PLACES_UNAVAILABLE");
    }

    @Test
    void successfulCallParsesCandidates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GooglePlacesClient.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        List<PlaceCandidateDTO> result = new GooglePlacesClient(
                        props("key"), builder.build(), objectMapper, mock(PlacesApiUsageRepository.class))
                .searchNearby(37.5, 127.0, List.of("cafe"), 500, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("테스트 카페");
        assertThat(result.get(0).rating()).isEqualTo(4.5);
        server.verify();
    }

    @Test
    void httpFailureIs503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GooglePlacesClient.ENDPOINT)).andRespond(withServerError());

        GooglePlacesClient client = new GooglePlacesClient(
                props("key"), builder.build(), objectMapper, mock(PlacesApiUsageRepository.class));

        assertThatThrownBy(() -> client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PLACES_UNAVAILABLE");
    }

    @Test
    void dailyQuotaBlocksCallsAfterLimitWithoutContactingGoogle() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.times(GooglePlacesClient.DAILY_CALL_LIMIT), requestTo(GooglePlacesClient.ENDPOINT))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        // 진짜 DB 없이, Repository가 하는 일(저장하고 다시 읽어오기)만 흉내 내는 가짜 저장소.
        PlacesApiUsageRepository usageRepository = mock(PlacesApiUsageRepository.class);
        AtomicReference<PlacesApiUsageEntity> stored = new AtomicReference<>();
        when(usageRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(usageRepository.save(any())).thenAnswer(inv -> {
            PlacesApiUsageEntity entity = inv.getArgument(0);
            stored.set(entity);
            return entity;
        });

        GooglePlacesClient client = new GooglePlacesClient(props("key"), builder.build(), objectMapper, usageRepository);

        for (int i = 0; i < GooglePlacesClient.DAILY_CALL_LIMIT; i++) {
            client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5);
        }

        assertThatThrownBy(() -> client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PLACES_QUOTA_EXCEEDED");

        server.verify();
    }

    private static AppProperties props(String key) {
        return new AppProperties(null, null, null, null, new AppProperties.Places(key), null);
    }
}
