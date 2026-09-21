package com.team.cultureevents.features.favorites.ctrl;

import com.team.cultureevents.features.favorites.domain.dto.FavoriteRequestDTO;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteResponseDTO;
import com.team.cultureevents.features.favorites.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 찜. 헤더 X-Client-Id = 브라우저 식별값. 로그인과 무관. */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public ResponseEntity<FavoriteResponseDTO> save(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @Valid @RequestBody FavoriteRequestDTO body
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(favoriteService.save(clientId, body));
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponseDTO>> list(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestParam(required = false) String month
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(favoriteService.list(clientId, month));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @PathVariable String eventId
    ) {
        favoriteService.delete(clientId, eventId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** 문화포털 URL형 eventId 삭제용. 기존 경로 방식도 유지한다. */
    @DeleteMapping
    public ResponseEntity<Void> deleteByQuery(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestParam String eventId
    ) {
        favoriteService.delete(clientId, eventId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
