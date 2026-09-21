package com.team.cultureevents.features.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.events.util.EventIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void fallbackIdAlwaysFitsDatabaseColumn() {
        String id = EventIdGenerator.fallback("제목".repeat(200), "2026-10-10", "장소".repeat(200));
        assertTrue(id.length() <= 512);
        assertEquals(id, EventIdGenerator.fallback("제목".repeat(200), "2026-10-10", "장소".repeat(200)));
    }

    @Test
    void sharedPortalUrlGetsDistinctFallbackIdsAndOrganizationLinkIsNotAnId() {
        String body = """
                {"culturalEventInfo":{"list_total_count":3,"RESULT":{"CODE":"INFO-000"},"row":[
                  {"TITLE":"행사 A","STRTDATE":"2026-10-10 10:00:00","END_DATE":"2026-10-12 10:00:00","PLACE":"장소 A","HMPG_ADDR":"https://culture.seoul.go.kr/shared"},
                  {"TITLE":"행사 B","STRTDATE":"2026-10-11 10:00:00","END_DATE":"2026-10-13 10:00:00","PLACE":"장소 B","HMPG_ADDR":"https://culture.seoul.go.kr/shared"},
                  {"TITLE":"행사 C","STRTDATE":"2026-10-14 10:00:00","END_DATE":"2026-10-15 10:00:00","PLACE":"장소 C","ORG_LINK":"https://organization.example.org"}
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
}
