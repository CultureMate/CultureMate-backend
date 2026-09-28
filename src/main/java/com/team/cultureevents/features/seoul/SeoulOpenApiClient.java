package com.team.cultureevents.features.seoul;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.util.EventIdGenerator;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OA-15486 culturalEventInfo 호출.
 * URL: {base}/{KEY}/json/culturalEventInfo/{start}/{end}/
 */
@Component
public class SeoulOpenApiClient {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 좌표 문자열 앞쪽의 숫자만 읽는다. 예: "37.57~2" → 37.57, "45.42°N" → 45.42 */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)");

    private final AppProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SeoulOpenApiClient(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<SeoulEvent> fetchAll() {
        String key = props.seoulApi().key();
        if (key == null || key.isBlank()) {
            throw BusinessException.upstream("SEOUL_API_KEY가 설정되지 않았습니다.");
        }

        // 서울 열린데이터광장은 요청 한 번에 최대 1,000행을 허용한다.
        int pageSize = Math.max(1, Math.min(1000, props.seoulApi().pageSize()));
        // 같은 행사가 문화포털에 여러 번 등록된 경우를 중복 키로 묶는다.
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        int fetched = 0;
        int start = 1;

        while (true) {
            int end = start + pageSize - 1;
            JsonNode root = requestPage(key, start, end);
            JsonNode info = root.path("culturalEventInfo");

            if (info.isMissingNode()) {
                String code = root.path("RESULT").path("CODE").asText("");
                if ("INFO-200".equals(code)) {
                    break;
                }
                throw BusinessException.upstream("서울시 API 오류: " + (code.isBlank() ? "응답 형식 오류" : code));
            }

            String resultCode = info.path("RESULT").path("CODE").asText("");
            if (!"INFO-000".equals(resultCode)) {
                throw BusinessException.upstream("서울시 API 오류: " + resultCode);
            }

            JsonNode rows = info.path("row");
            if (!rows.isArray() || rows.isEmpty()) {
                break;
            }

            for (JsonNode row : rows) {
                groups.computeIfAbsent(duplicateKey(row), k -> new ArrayList<>()).add(row);
            }
            fetched += rows.size();

            int total = info.path("list_total_count").asInt(fetched);
            if (end >= total || rows.size() < pageSize) {
                break;
            }
            start = end + 1;
        }

        List<SeoulEvent> all = new ArrayList<>();
        for (List<JsonNode> group : groups.values()) {
            all.add(mergeDuplicates(group));
        }
        return resolveDuplicateIds(all);
    }

    /** 중복 등록 판단 키: 제목|시작일|장소. 공백을 없애고 영문은 소문자로 통일한다(종료일은 수정될 수 있어 제외). */
    static String duplicateKey(JsonNode row) {
        return normalizeForKey(text(row, "TITLE"))
                + "|" + toDate(text(row, "STRTDATE"))
                + "|" + normalizeForKey(text(row, "PLACE"));
    }

    private static String normalizeForKey(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 중복 등록 행을 하나로 합친다.
     * 내용(이미지·기간·장소 등)은 등록일이 가장 최신인 행을, 대표 eventId는 가장 먼저 등록된 행을 쓴다.
     * 새 등록 때문에 대표 ID가 바뀌면 기존 찜·댓글·소개문·조회수 연결이 끊기기 때문이다.
     * 나머지 행의 ID는 aliasIds로 남겨 그 ID로 들어온 상세 조회도 대표 행사로 연결한다.
     */
    private SeoulEvent mergeDuplicates(List<JsonNode> rows) {
        Comparator<JsonNode> byRegistered = Comparator.comparing(row -> text(row, "RGSTDATE"));
        // 등록일이 같으면 먼저 온 행을 유지한다.
        JsonNode newest = rows.stream().reduce((kept, next) -> byRegistered.compare(next, kept) > 0 ? next : kept).orElseThrow();
        JsonNode oldest = rows.stream().reduce((kept, next) -> byRegistered.compare(next, kept) < 0 ? next : kept).orElseThrow();
        SeoulEvent content = mapRow(newest);
        String eventId = mapRow(oldest).eventId();
        List<String> aliasIds = rows.stream()
                .map(row -> mapRow(row).eventId())
                .filter(id -> !id.equals(eventId))
                .distinct()
                .toList();
        return new SeoulEvent(eventId, content.title(), content.category(), content.district(), content.place(),
                content.startDate(), content.endDate(), content.fee(), content.organization(),
                content.originalUrl(), content.imageUrl(), content.latitude(), content.longitude(), aliasIds);
    }

    /** 동일한 문화포털 URL이 여러 행사에 붙은 경우 명세의 조합키로 식별한다. */
    private static List<SeoulEvent> resolveDuplicateIds(List<SeoulEvent> rows) {
        Map<String, Integer> counts = new HashMap<>();
        rows.forEach(event -> counts.merge(event.eventId(), 1, Integer::sum));

        Map<String, SeoulEvent> unique = new LinkedHashMap<>();
        for (SeoulEvent event : rows) {
            String id = counts.get(event.eventId()) > 1
                    ? EventIdGenerator.fallback(event.title(), event.startDate(), event.place())
                    : event.eventId();
            SeoulEvent normalized = new SeoulEvent(
                    id, event.title(), event.category(), event.district(), event.place(),
                    event.startDate(), event.endDate(), event.fee(), event.organization(),
                    event.originalUrl(), event.imageUrl(), event.latitude(), event.longitude(), event.aliasIds()
            );
            // 완전히 같은 행은 한 번만 보여준다. 조합키도 충돌하면 다른 원본 필드까지
            // 포함한 결정적 키를 사용해 목록과 상세가 서로 다른 행을 가리키게 한다.
            SeoulEvent previous = unique.putIfAbsent(id, normalized);
            if (previous != null && !previous.equals(normalized)) {
                String distinctId = EventIdGenerator.fallback(event.title(), event.startDate(), event.toString());
                SeoulEvent distinct = new SeoulEvent(
                        distinctId, event.title(), event.category(), event.district(), event.place(),
                        event.startDate(), event.endDate(), event.fee(), event.organization(),
                        event.originalUrl(), event.imageUrl(), event.latitude(), event.longitude(), event.aliasIds()
                );
                SeoulEvent collision = unique.putIfAbsent(distinctId, distinct);
                if (collision != null && !collision.equals(distinct)) {
                    throw BusinessException.upstream("서울시 행사 식별키가 중복되었습니다.");
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private JsonNode requestPage(String key, int start, int end) {
        String url = props.seoulApi().baseUrl()
                + "/" + key
                + "/json/culturalEventInfo/"
                + start + "/" + end + "/";
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw BusinessException.upstream("서울시 API 응답이 비어 있습니다.");
            }
            return objectMapper.readTree(body);
        } catch (RestClientException | java.io.IOException e) {
            throw BusinessException.upstream("서울시 API 조회에 실패했습니다.");
        }
    }

    private SeoulEvent mapRow(JsonNode row) {
        String title = text(row, "TITLE");
        String startDate = toDate(text(row, "STRTDATE"));
        String endDate = toDate(text(row, "END_DATE"));
        String place = text(row, "PLACE");
        String portal = text(row, "HMPG_ADDR");
        String originalUrl = firstNonBlank(portal, text(row, "ORG_LINK"));
        // 기관 홈페이지는 여러 행사가 공유할 수 있어 식별키로 사용하지 않는다.
        String eventId = EventIdGenerator.from(portal, title, startDate, place);
        Double[] coordinates = coordinates(text(row, "LAT"), text(row, "LOT"));

        return new SeoulEvent(
                eventId,
                title,
                text(row, "CODENAME"),
                text(row, "GUNAME"),
                place,
                startDate,
                endDate,
                text(row, "USE_FEE"),
                text(row, "ORG_NAME"),
                originalUrl,
                text(row, "MAIN_IMG"),
                coordinates[0],
                coordinates[1]
        );
    }

    /**
     * 서울시 API의 LAT·LOT를 위도·경도로 정리한다.
     * 원본에서 두 값이 뒤바뀐 행이 있어, 서울 범위(위도 33~39, 경도 124~132)에 맞게 바로잡는다.
     * 값이 없거나 범위를 벗어나면 null(프론트는 장소명 검색으로 대체).
     */
    static Double[] coordinates(String lat, String lot) {
        Double a = parseDouble(lat);
        Double b = parseDouble(lot);
        if (a == null || b == null) return new Double[]{null, null};
        if (isLatitude(a) && isLongitude(b)) return new Double[]{a, b};
        if (isLatitude(b) && isLongitude(a)) return new Double[]{b, a};
        return new Double[]{null, null};
    }

    private static boolean isLatitude(double v) {
        return v >= 33 && v <= 39;
    }

    private static boolean isLongitude(double v) {
        return v >= 124 && v <= 132;
    }

    private static Double parseDouble(String raw) {
        if (raw == null) return null;
        Matcher matcher = LEADING_NUMBER.matcher(raw);
        if (!matcher.find()) return null;
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode n = row.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return "";
        }
        return n.asText("").trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return "";
    }

    static String toDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String v = raw.trim();
        if (v.length() < 10) {
            return "";
        }
        try {
            return LocalDate.parse(v.substring(0, 10).replace('.', '-'), ISO_DATE).format(ISO_DATE);
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }
}
