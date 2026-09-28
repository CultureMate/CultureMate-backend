package com.team.cultureevents.features.courses.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.courses.domain.dto.CourseCreateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseDetailResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseFavoriteRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseShareResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseStopRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseSummaryResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseUpdateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.SharedCourseResponseDTO;
import com.team.cultureevents.features.courses.domain.entity.CourseEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopType;
import com.team.cultureevents.features.courses.repository.CourseRepository;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CourseService {

    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_STOPS = 20;
    private static final int MAX_PLACE_ID_LENGTH = 255;

    private final CourseRepository courseRepository;
    private final EventService eventService;

    public CourseService(CourseRepository courseRepository, EventService eventService) {
        this.courseRepository = courseRepository;
        this.eventService = eventService;
    }

    public CourseDetailResponseDTO create(Long memberId, CourseCreateRequestDTO request) {
        String title = validateTitle(request == null ? null : request.title());
        List<CourseStopEntity> stops = buildStops(request == null ? null : request.stops(), Map.of());
        Instant now = Instant.now();

        CourseEntity course = new CourseEntity(memberId, title, now);
        course.initializeStops(stops);
        return CourseDetailResponseDTO.from(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryResponseDTO> list(Long memberId, boolean favoriteOnly) {
        List<CourseEntity> courses = favoriteOnly
                ? courseRepository.findByMemberIdAndFavoritedTrueOrderByFavoritedAtDesc(memberId)
                : courseRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        return courses.stream().map(CourseSummaryResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailResponseDTO get(Long memberId, Long courseId) {
        CourseEntity course = requireCourseWithStops(courseId);
        requireOwner(course, memberId);
        return CourseDetailResponseDTO.from(course);
    }

    public CourseDetailResponseDTO update(Long memberId, Long courseId, CourseUpdateRequestDTO request) {
        CourseEntity course = requireCourseForUpdate(courseId);
        requireOwner(course, memberId);
        if (request == null || request.version() == null) {
            throw BusinessException.badRequest("version은 필수입니다.");
        }
        if (request.version() != course.getContentVersion()) {
            throw versionConflict();
        }

        // 기존 행사 스탑은 서울시 원본에서 사라져도 수정할 수 있도록 저장된 스냅샷을 재사용한다.
        Map<String, CourseStopEntity> existingEvents = new HashMap<>();
        for (CourseStopEntity stop : course.getStops()) {
            if (stop.getType() == CourseStopType.EVENT && stop.getEventId() != null) {
                existingEvents.put(stop.getEventId(), stop);
            }
        }

        String title = validateTitle(request.title());
        List<CourseStopEntity> stops = buildStops(request.stops(), existingEvents);
        course.updateContent(title, stops, Instant.now());
        return CourseDetailResponseDTO.from(courseRepository.save(course));
    }

    public CourseSummaryResponseDTO setFavorite(Long memberId, Long courseId, CourseFavoriteRequestDTO request) {
        if (request == null || request.favorited() == null) {
            throw BusinessException.badRequest("favorited는 필수입니다.");
        }
        CourseEntity course = requireCourseForUpdate(courseId);
        requireOwner(course, memberId);
        course.setFavorited(request.favorited(), Instant.now());
        return CourseSummaryResponseDTO.from(courseRepository.save(course));
    }

    public void delete(Long memberId, Long courseId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        requireOwner(course, memberId);
        courseRepository.delete(course);
    }

    public CourseShareResponseDTO enableShare(Long memberId, Long courseId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        requireOwner(course, memberId);
        if (course.getShareId() == null) {
            course.enableShare(newShareId());
            courseRepository.save(course);
        }
        return new CourseShareResponseDTO(course.getShareId());
    }

    public void disableShare(Long memberId, Long courseId) {
        CourseEntity course = requireCourseForUpdate(courseId);
        requireOwner(course, memberId);
        course.disableShare();
        courseRepository.save(course);
    }

    @Transactional(readOnly = true)
    public SharedCourseResponseDTO getShared(String rawShareId) {
        String shareId = rawShareId == null ? "" : rawShareId.trim();
        if (!shareId.matches("[0-9a-fA-F]{32}")) {
            throw BusinessException.notFound("공유된 코스를 찾을 수 없습니다.");
        }
        CourseEntity course = courseRepository.findByShareId(shareId)
                .orElseThrow(() -> BusinessException.notFound("공유된 코스를 찾을 수 없습니다."));
        return SharedCourseResponseDTO.from(course);
    }

    public void deleteAllByMemberId(Long memberId) {
        List<CourseEntity> courses = courseRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        courseRepository.deleteAll(courses);
    }

    private List<CourseStopEntity> buildStops(List<CourseStopRequestDTO> requests,
                                               Map<String, CourseStopEntity> existingEvents) {
        if (requests == null || requests.isEmpty()) {
            throw BusinessException.badRequest("코스에는 스탑이 1개 이상 필요합니다.");
        }
        if (requests.size() > MAX_STOPS) {
            throw BusinessException.badRequest("코스 스탑은 최대 20개까지 저장할 수 있습니다.");
        }

        List<CourseStopEntity> result = new ArrayList<>();
        Set<String> duplicateKeys = new HashSet<>();
        int eventCount = 0;

        for (int i = 0; i < requests.size(); i++) {
            CourseStopRequestDTO request = requests.get(i);
            if (request == null) {
                throw BusinessException.badRequest("stop 값이 올바르지 않습니다.");
            }
            CourseStopType type = CourseStopType.from(request.type());
            if (type == CourseStopType.EVENT) {
                String eventId = required(request.eventId(), "event 타입에는 eventId가 필요합니다.");
                if (request.placeId() != null && !request.placeId().isBlank()) {
                    throw BusinessException.badRequest("event 타입에는 placeId를 보낼 수 없습니다.");
                }

                CourseStopEntity existing = existingEvents.get(eventId);
                CourseStopEntity stop;
                String canonicalId;
                if (existing != null) {
                    stop = CourseStopEntity.eventFromSnapshot(i, existing);
                    canonicalId = existing.getEventId();
                } else {
                    EventDetailResponseDTO detail = eventService.getDetail(eventId);
                    stop = CourseStopEntity.event(i, detail);
                    canonicalId = detail.eventId();
                }
                if (!duplicateKeys.add("EVENT:" + canonicalId)) {
                    throw BusinessException.badRequest("같은 행사를 코스에 두 번 넣을 수 없습니다.");
                }
                result.add(stop);
                eventCount++;
                continue;
            }

            if (request.eventId() != null && !request.eventId().isBlank()) {
                throw BusinessException.badRequest("cafe/restaurant 타입에는 eventId를 보낼 수 없습니다.");
            }
            String placeId = validatePlaceId(request.placeId());
            if (!duplicateKeys.add("PLACE:" + placeId)) {
                throw BusinessException.badRequest("같은 장소를 코스에 두 번 넣을 수 없습니다.");
            }
            result.add(CourseStopEntity.place(i, type, placeId));
        }

        if (eventCount == 0) {
            throw BusinessException.badRequest("코스에는 행사가 1개 이상 포함되어야 합니다.");
        }
        return result;
    }

    private CourseEntity requireCourseWithStops(Long courseId) {
        if (courseId == null) {
            throw BusinessException.badRequest("courseId가 필요합니다.");
        }
        return courseRepository.findWithStopsById(courseId)
                .orElseThrow(() -> BusinessException.notFound("코스를 찾을 수 없습니다."));
    }

    private CourseEntity requireCourseForUpdate(Long courseId) {
        if (courseId == null) {
            throw BusinessException.badRequest("courseId가 필요합니다.");
        }
        CourseEntity course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> BusinessException.notFound("코스를 찾을 수 없습니다."));
        // 트랜잭션 안에서 기존 스탑을 미리 읽어 두어 수정 시 스냅샷 재사용이 가능하게 한다.
        course.getStops().size();
        return course;
    }

    private static void requireOwner(CourseEntity course, Long memberId) {
        if (!course.getMemberId().equals(memberId)) {
            throw new BusinessException("FORBIDDEN", "다른 회원의 코스에는 접근할 수 없습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private static String validateTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isBlank()) {
            throw BusinessException.badRequest("코스 제목은 공백을 제외하고 1자 이상 입력해야 합니다.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw BusinessException.badRequest("코스 제목은 50자 이하로 입력해야 합니다.");
        }
        return title;
    }

    private static String validatePlaceId(String raw) {
        String placeId = required(raw, "cafe/restaurant 타입에는 placeId가 필요합니다.");
        if (placeId.length() > MAX_PLACE_ID_LENGTH || !placeId.matches("[A-Za-z0-9_-]+")) {
            throw BusinessException.badRequest("placeId 형식이 올바르지 않습니다.");
        }
        return placeId;
    }

    private static String required(String raw, String message) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw BusinessException.badRequest(message);
        }
        return value;
    }

    private String newShareId() {
        for (int i = 0; i < 5; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "");
            if (!courseRepository.existsByShareId(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("SHARE_ID_UNAVAILABLE", "공유 링크를 만들지 못했습니다. 다시 시도해 주세요.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "COURSE_VERSION_CONFLICT",
                "다른 곳에서 코스가 수정되었습니다. 최신 내용을 다시 불러와 주세요.",
                HttpStatus.CONFLICT
        );
    }
}
