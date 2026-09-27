package com.team.cultureevents.features.places.ctrl;

import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.service.PlacesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlacesController {

    private final PlacesService placesService;

    public PlacesController(PlacesService placesService) {
        this.placesService = placesService;
    }

    @GetMapping("/api/places/nearby")
    public ResponseEntity<List<PlaceCandidateDTO>> nearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer maxResults
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(placesService.recommendNearby(latitude, longitude, types, radius, maxResults));
    }
}
