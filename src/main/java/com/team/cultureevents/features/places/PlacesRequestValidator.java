package com.team.cultureevents.features.places;

import com.team.cultureevents.features.commons.handler.BusinessException;
import java.util.List;
import java.util.Set;

public final class PlacesRequestValidator {
    public static final Set<String> TYPES = GooglePlaceTypes.ALLOWED;
    private PlacesRequestValidator() {}

    public static void coordinates(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < 33 || lat > 39 || lng < 124 || lng > 132) {
            throw BusinessException.badRequest("좌표는 유한한 국내 범위 값이어야 합니다.");
        }
    }

    public static void nearby(double lat, double lng, List<String> types, int radius) {
        coordinates(lat, lng);
        if (radius < 1 || radius > 50_000) throw BusinessException.badRequest("radius는 1~50000이어야 합니다.");
        if (types == null || types.isEmpty() || types.size() > 50
                || types.stream().anyMatch(t -> t == null || !TYPES.contains(t))) {
            throw BusinessException.badRequest("지원하지 않는 장소 types입니다.");
        }
    }

    public static void photo(String name) {
        if (name == null || !name.matches("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_-]+")) {
            throw BusinessException.badRequest("name은 places/{placeId}/photos/{photoId} 형식이어야 합니다.");
        }
    }
}
