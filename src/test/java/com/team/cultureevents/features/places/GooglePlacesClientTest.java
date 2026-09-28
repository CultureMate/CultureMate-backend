package com.team.cultureevents.features.places;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.service.PlacesQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.mock;

class GooglePlacesClientTest {

    @Test
    void invalidInputDoesNotReserveQuota() {
        var quota = mock(PlacesQuotaService.class);
        var client = new GooglePlacesClient(props("key"), RestClient.builder().build(), new ObjectMapper(), quota);
        assertThatThrownBy(() -> client.searchNearby(Double.NaN, 127, List.of("cafe"), 500, 20, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.searchNearby(37.5, 127, List.of("food"), 500, 20, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.searchNearby(37.5, 127, List.of("cafe"), 50001, 20, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.resolvePhotoUri("places/a/photos/b?key=other", 400, 1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.getDetails("bad/place/id", 1L)).isInstanceOf(BusinessException.class);
        org.mockito.Mockito.verifyNoInteractions(quota);
    }

    @Test
    void quotaRejectionDoesNotContactGoogle() {
        var quota = mock(PlacesQuotaService.class);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GooglePlacesClient(props("key"), builder.build(), new ObjectMapper(), quota);
        org.mockito.Mockito.doThrow(new BusinessException("PLACES_RATE_LIMITED", "limit",
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)).when(quota).reserve(false, 1L);
        assertThatThrownBy(() -> client.searchNearby(37.5, 127, List.of("cafe"), 500, 20, 1L))
                .hasFieldOrPropertyWithValue("code", "PLACES_RATE_LIMITED");
        server.verify();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SUCCESS_BODY = """
            {"places":[{"id":"p1","displayName":{"text":"테스트 카페"},"formattedAddress":"주소",
            "rating":4.5,"userRatingCount":100,"location":{"latitude":37.5,"longitude":127.0},
            "googleMapsUri":"https://maps.google.com/?cid=1","businessStatus":"OPERATIONAL",
            "currentOpeningHours":{"openNow":true},
            "photos":[{"name":"places/p1/photos/abc","authorAttributions":[{"displayName":"홍길동","uri":"https://example.com/a","photoUri":"https://example.com/a.jpg"},{"displayName":"김철수","uri":"https://example.com/b","photoUri":"https://example.com/b.jpg"}]}]}]}
            """;

    @Test
    void missingKeyIs503WithoutCallingGoogle() {
        GooglePlacesClient client = new GooglePlacesClient(
                props("  "), RestClient.builder().build(), objectMapper, mock(PlacesQuotaService.class));

        assertThatThrownBy(() -> client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PLACES_UNAVAILABLE");
    }

    @Test
    void successfulCallParsesCandidatesIncludingPhoto() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GooglePlacesClient.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"languageCode\":\"ko\""),
                        containsString("\"regionCode\":\"KR\"")
                )))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        PlacesQuotaService quota = mock(PlacesQuotaService.class);
        List<PlaceCandidateDTO> result = new GooglePlacesClient(
                        props("key"), builder.build(), objectMapper, quota)
                .searchNearby(37.5, 127.0, List.of("cafe"), 500, 5, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("테스트 카페");
        assertThat(result.get(0).rating()).isEqualTo(4.5);
        assertThat(result.get(0).photoName()).isEqualTo("places/p1/photos/abc");
        assertThat(result.get(0).authorAttributions().get(0).displayName()).isEqualTo("홍길동");
        assertThat(result.get(0).authorAttributions()).hasSize(2);
        assertThat(result.get(0).authorAttributions().get(1).photoUri()).isEqualTo("https://example.com/b.jpg");
        assertThat(result.get(0).businessStatus()).isEqualTo("OPERATIONAL");
        assertThat(result.get(0).openNow()).isTrue();
        org.mockito.Mockito.verify(quota).reserve(false, 1L);
        server.verify();
    }

    @Test
    void placeDetailsUsesKoreanRegionAndParsesOnePlace() {
        var quota = mock(PlacesQuotaService.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://places.googleapis.com/v1/places/p1?languageCode=ko&regionCode=KR"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"p1","displayName":{"text":"테스트 카페"},"formattedAddress":"서울 주소",
                        "rating":4.5,"userRatingCount":100,"location":{"latitude":37.5,"longitude":127.0},
                        "googleMapsUri":"https://maps.google.com/?cid=1","businessStatus":"OPERATIONAL",
                        "currentOpeningHours":{"openNow":true}}
                        """, MediaType.APPLICATION_JSON));

        PlaceCandidateDTO result = new GooglePlacesClient(props("key"), builder.build(), objectMapper, quota)
                .getDetails("p1", 1L);

        assertThat(result.placeId()).isEqualTo("p1");
        assertThat(result.name()).isEqualTo("테스트 카페");
        org.mockito.Mockito.verify(quota).reserveDetails(1L);
        server.verify();
    }

    @Test
    void httpFailureIs503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(GooglePlacesClient.ENDPOINT)).andRespond(withServerError());

        GooglePlacesClient client = new GooglePlacesClient(
                props("key"), builder.build(), objectMapper, mock(PlacesQuotaService.class));

        assertThatThrownBy(() -> client.searchNearby(37.5, 127.0, List.of("cafe"), 500, 5, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PLACES_UNAVAILABLE");
    }

    @Test
    void resolvePhotoUriReturnsCdnLinkWithoutApiKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"name":"places/p1/photos/abc/media","photoUri":"https://lh3.googleusercontent.com/abc"}
                        """, MediaType.APPLICATION_JSON));

        PlacesQuotaService quota = mock(PlacesQuotaService.class);
        String photoUri = new GooglePlacesClient(props("key"), builder.build(), objectMapper, quota)
                .resolvePhotoUri("places/p1/photos/abc", 400, 1L);

        assertThat(photoUri).isEqualTo("https://lh3.googleusercontent.com/abc");
        org.mockito.Mockito.verify(quota).reserve(true, 1L);
        server.verify();
    }

    private static AppProperties props(String key) {
        return new AppProperties(null, null, null, null, new AppProperties.Places(key), null);
    }
}
