package com.team.cultureevents.features.places.ctrl;

import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.service.PlacesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class PlacesController {

    private final PlacesService placesService;
    private final CurrentMemberService currentMember;

    public PlacesController(PlacesService placesService, CurrentMemberService currentMember) {
        this.placesService = placesService;
        this.currentMember = currentMember;
    }

    @GetMapping("/api/places/nearby")
    public ResponseEntity<List<PlaceCandidateDTO>> nearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer maxResults,
            HttpServletRequest request
    ) {
        long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.OK)
                .body(placesService.recommendNearby(latitude, longitude, types, radius, maxResults, memberId));
    }

    /**
     * 코스에서 연속된 두 행사 사이에 끼워 넣을 카페/음식점을 찾는다.
     * 두 행사 좌표의 중점을 중심으로, 둘 사이 거리의 절반을 반경으로 검색한다.
     * type당 최대 20개를 베이지안 평점과 직선 우회거리로 정렬해 한 번에 전부 돌려준다(프론트가 5개씩 페이지로 나눠 보여줌).
     */
    @GetMapping("/api/places/between")
    public ResponseEntity<List<PlaceCandidateDTO>> between(
            @RequestParam String eventId1,
            @RequestParam String eventId2,
            @RequestParam String type,
            HttpServletRequest request
    ) {
        long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.OK)
                .body(placesService.recommendBetweenEvents(eventId1, eventId2, type, memberId));
    }

    /** 저장된 코스의 placeId로 최신 이름·주소·영업상태 등을 다시 조회한다. */
    @GetMapping("/api/places/details")
    public ResponseEntity<PlaceCandidateDTO> details(
            @RequestParam String placeId,
            HttpServletRequest request
    ) {
        long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.OK)
                .body(placesService.getDetails(placeId, memberId));
    }

    /**
     * 프론트가 <img src="/api/places/photo?name=..."> 로 바로 쓸 수 있게, 구글 사진 CDN으로 302 리다이렉트한다.
     * API 키는 이 서버 안에서만 쓰이고 프론트·브라우저에는 절대 노출되지 않는다.
     */
    @GetMapping("/api/places/photo")
    public ResponseEntity<Void> photo(
            @RequestParam String name,
            @RequestParam(required = false) Integer maxWidthPx,
            HttpServletRequest request
    ) {
        long memberId = currentMember.requireMember(request).getMemberId();
        String photoUri = placesService.resolvePhotoUri(name, maxWidthPx, memberId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(photoUri)).build();
    }
}
