package com.team.cultureevents.features.places;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.handler.GlobalExceptionHandler;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.places.ctrl.PlacesController;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.service.PlacesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlacesControllerTest {
    GooglePlacesClient client = mock(GooglePlacesClient.class);
    EventService events = mock(EventService.class);
    CurrentMemberService currentMember = mock(CurrentMemberService.class);
    MemberEntity member = mock(MemberEntity.class);
    MockMvc mvc;

    PlacesControllerTest() {
        when(member.getMemberId()).thenReturn(1L);
        when(currentMember.requireMember(any())).thenReturn(member);
        mvc = MockMvcBuilders.standaloneSetup(new PlacesController(new PlacesService(client, events), currentMember))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/places/nearby", "/api/places/nearby?latitude=37.5",
            "/api/places/between?eventId1=a&eventId2=b", "/api/places/photo", "/api/places/details",
            "/api/places/nearby?latitude=abc&longitude=127",
            "/api/places/nearby?latitude=NaN&longitude=127",
            "/api/places/nearby?latitude=37.5&longitude=Infinity",
            "/api/places/nearby?latitude=37.5&longitude=127&radius=0",
            "/api/places/nearby?latitude=37.5&longitude=127&radius=50001",
            "/api/places/nearby?latitude=37.5&longitude=127&types=food",
            "/api/places/between?eventId1=a&eventId2=b&type=bar",
            "/api/places/details?placeId=bad%2Fplace",
            "/api/places/photo?name=places/a/photos/b/media",
            "/api/places/photo?name=places/a/photos/b%3Fx=1"
    })
    void invalidRequestsAre400WithoutExternalCall(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        verifyNoInteractions(client, events);
    }

    @Test
    void placesEndpointsRequireLogin() throws Exception {
        PlacesService places = mock(PlacesService.class);
        CurrentMemberService unauthorized = mock(CurrentMemberService.class);
        when(unauthorized.requireMember(any())).thenThrow(
                new BusinessException("UNAUTHORIZED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
        MockMvc authMvc = MockMvcBuilders.standaloneSetup(new PlacesController(places, unauthorized))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        for (String path : java.util.List.of(
                "/api/places/nearby?latitude=37.5&longitude=127",
                "/api/places/between?eventId1=a&eventId2=b&type=cafe",
                "/api/places/photo?name=places/a/photos/b")) {
            authMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        verifyNoInteractions(places);
    }

    @Test
    void placeDetailsRemainsPublicAndUsesClientAddressForQuota() throws Exception {
        PlaceCandidateDTO detail = mock(PlaceCandidateDTO.class);
        when(client.getDetails("p1", "203.0.113.10")).thenReturn(detail);

        mvc.perform(get("/api/places/details")
                        .param("placeId", "p1")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        }))
                .andExpect(status().isOk());

        verify(client).getDetails("p1", "203.0.113.10");
        verifyNoInteractions(currentMember);
    }
}
