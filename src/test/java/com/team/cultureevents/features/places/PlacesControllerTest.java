package com.team.cultureevents.features.places;

import com.team.cultureevents.features.commons.handler.GlobalExceptionHandler;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.places.ctrl.PlacesController;
import com.team.cultureevents.features.places.service.PlacesService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlacesControllerTest {
    GooglePlacesClient client = mock(GooglePlacesClient.class);
    EventService events = mock(EventService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlacesController(new PlacesService(client, events)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/places/nearby", "/api/places/nearby?latitude=37.5",
            "/api/places/between?eventId1=a&eventId2=b", "/api/places/photo",
            "/api/places/nearby?latitude=abc&longitude=127",
            "/api/places/nearby?latitude=NaN&longitude=127",
            "/api/places/nearby?latitude=37.5&longitude=Infinity",
            "/api/places/nearby?latitude=37.5&longitude=127&radius=0",
            "/api/places/nearby?latitude=37.5&longitude=127&radius=50001",
            "/api/places/nearby?latitude=37.5&longitude=127&types=food",
            "/api/places/between?eventId1=a&eventId2=b&type=bar",
            "/api/places/photo?name=places/a/photos/b/media",
            "/api/places/photo?name=places/a/photos/b%3Fx=1"
    })
    void invalidRequestsAre400WithoutExternalCall(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        verifyNoInteractions(client, events);
    }
}
