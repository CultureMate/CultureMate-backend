package com.team.cultureevents.features.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.events.util.EventIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeoulEventMappingTest {

    @Test
    void rejectsInvalidDateInsteadOfReturningUnparseablePrefix() {
        assertEquals("", SeoulOpenApiClient.toDate("2026-99-99 12:00:00"));
        assertEquals("2026-10-10", SeoulOpenApiClient.toDate("2026-10-10 12:00:00"));
    }

    @Test
    void coordinatesAreNormalizedToSeoulLatitudeLongitude() {
        Double[] normal = SeoulOpenApiClient.coordinates("37.5665", "126.978");
        assertEquals(37.5665, normal[0]);
        assertEquals(126.978, normal[1]);

        // 원본에서 LAT·LOT가 뒤바뀐 행
        Double[] swapped = SeoulOpenApiClient.coordinates("126.978", "37.5665");
        assertEquals(37.5665, swapped[0]);
        assertEquals(126.978, swapped[1]);

        assertNull(SeoulOpenApiClient.coordinates("", "126.978")[0]);
        assertNull(SeoulOpenApiClient.coordinates("0", "0")[0]);
        assertNull(SeoulOpenApiClient.coordinates("abc", "126.978")[1]);

        // 숫자 뒤에 문자가 붙은 원본 값은 앞쪽 숫자만 읽는다.
        assertEquals(37.5718961547884, SeoulOpenApiClient.coordinates("37.5718961547884~2", "126.987230558854")[0]);
        // 서울 밖(캐나다) 좌표는 null
        assertNull(SeoulOpenApiClient.coordinates("45.4215°N", "75.6917°W")[0]);
    }

    @Test
    void fallbackIdAlwaysFitsDatabaseColumn() {
        String id = EventIdGenerator.fallback("제목".repeat(200), "2026-10-10", "장소".repeat(200));
        assertTrue(id.length() <= 512);
        assertEquals(id, EventIdGenerator.fallback("제목".repeat(200), "2026-10-10", "장소".repeat(200)));
    }

    @Test
    void sharedPortalUrlGetsDistinctFallbackIdsAndOrganizationLinkIsNotAnId() {
        String body = """
                {"culturalEventInfo":{"list_total_count":3,"RESULT":{"CODE":"INFO-000"},"row":[
                  {"TITLE":"행사 A","STRTDATE":"2026-10-10 10:00:00","END_DATE":"2026-10-12 10:00:00","PLACE":"장소 A","HMPG_ADDR":"https://culture.seoul.go.kr/shared","LAT":"37.5665","LOT":"126.978"},
                  {"TITLE":"행사 B","STRTDATE":"2026-10-11 10:00:00","END_DATE":"2026-10-13 10:00:00","PLACE":"장소 B","HMPG_ADDR":"https://culture.seoul.go.kr/shared","LAT":"37.5665","LOT":"126.978"},
                  {"TITLE":"행사 C","STRTDATE":"2026-10-14 10:00:00","END_DATE":"2026-10-15 10:00:00","PLACE":"장소 C","ORG_LINK":"https://organization.example.org","LAT":"37.5665","LOT":"126.978"}
                ]}}
                """;
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://seoul.test/key/json/culturalEventInfo/1/3/"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        AppProperties props = new AppProperties(null,
                new AppProperties.SeoulApi("key", "http://seoul.test", 30, 3), null, null, "http://localhost:5175");
        SeoulOpenApiClient client = new SeoulOpenApiClient(props, builder, new ObjectMapper());

        var events = client.fetchAll();

        assertEquals(3, events.size());
        assertEquals("행사 A|2026-10-10|장소 A", events.get(0).eventId());
        assertEquals("행사 B|2026-10-11|장소 B", events.get(1).eventId());
        assertEquals("행사 C|2026-10-14|장소 C", events.get(2).eventId());
        assertEquals("https://organization.example.org", events.get(2).originalUrl());
        server.verify();
    }

    @Test
    void keepsNewestDuplicateAndKeepsEventsWithoutCoordinates() {
        String body = """
                {"culturalEventInfo":{"list_total_count":4,"RESULT":{"CODE":"INFO-000"},"row":[
                  {"TITLE":"더 아카이브 : 뮤지컬의 순간들","STRTDATE":"2026-06-27 00:00:00.0","END_DATE":"2026-10-30 00:00:00.0","PLACE":"예술의전당","RGSTDATE":"2026-06-01","HMPG_ADDR":"https://culture.seoul.go.kr/old","LAT":"37.48","LOT":"127.01"},
                  {"TITLE":"더 아카이브 :  뮤지컬의 순간들","STRTDATE":"2026-06-27 00:00:00.0","END_DATE":"2026-10-30 00:00:00.0","PLACE":"예술의 전당","RGSTDATE":"2026-06-20","HMPG_ADDR":"https://culture.seoul.go.kr/new","LAT":"37.48","LOT":"127.01"},
                  {"TITLE":"좌표 없는 행사","STRTDATE":"2021-09-28 00:00:00.0","END_DATE":"2021-10-02 00:00:00.0","PLACE":"만리동광장, 정동길","HMPG_ADDR":"https://culture.seoul.go.kr/no-coord","LAT":"","LOT":""},
                  {"TITLE":"향토문화미술대전","STRTDATE":"2026-09-30 00:00:00.0","END_DATE":"2026-10-05 00:00:00.0","PLACE":"한국미술관","HMPG_ADDR":"https://culture.seoul.go.kr/tilde","LAT":"37.5718961547884~2","LOT":"126.987230558854"}
                ]}}
                """;
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://seoul.test/key/json/culturalEventInfo/1/4/"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        AppProperties props = new AppProperties(null,
                new AppProperties.SeoulApi("key", "http://seoul.test", 30, 4), null, null, "http://localhost:5175");
        SeoulOpenApiClient client = new SeoulOpenApiClient(props, builder, new ObjectMapper());

        var events = client.fetchAll();

        // 중복은 등록일이 최신인 행만, 좌표 없는 행사는 좌표 null로 유지, 위도 뒤 문자는 잘라서 읽음
        assertEquals(3, events.size());
        assertEquals("https://culture.seoul.go.kr/new", events.get(0).eventId());
        assertEquals("좌표 없는 행사", events.get(1).title());
        assertNull(events.get(1).latitude());
        assertEquals("향토문화미술대전", events.get(2).title());
        assertEquals(37.5718961547884, events.get(2).latitude());
        server.verify();
    }
}
