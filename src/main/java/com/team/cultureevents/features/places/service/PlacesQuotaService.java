package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.entity.PlacesApiUsageEntity;
import com.team.cultureevents.features.places.domain.entity.PlacesQuotaBucket;
import com.team.cultureevents.features.places.repository.PlacesApiUsageRepository;
import com.team.cultureevents.features.places.repository.PlacesQuotaBucketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
public class PlacesQuotaService {
    private static final int SEARCH_DAILY_LIMIT = 80;
    private static final int PHOTO_DAILY_LIMIT = 60;
    private static final int DETAIL_DAILY_LIMIT = 80;

    private enum Kind { SEARCH, PHOTO, DETAIL }

    private final PlacesApiUsageRepository daily;
    private final PlacesQuotaBucketRepository buckets;
    private final TransactionTemplate transaction;
    private final int searchMonthly;
    private final int photoMonthly;
    private final int detailMonthly;
    private final int searchPerMinute;
    private final int photoPerMinute;
    private final int detailPerMinute;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager,
            @Value("${app.places.search-monthly-limit:900}") int searchMonthly,
            @Value("${app.places.photo-monthly-limit:900}") int photoMonthly,
            @Value("${app.places.detail-monthly-limit:900}") int detailMonthly,
            @Value("${app.places.search-per-minute:20}") int searchPerMinute,
            @Value("${app.places.photo-per-minute:40}") int photoPerMinute,
            @Value("${app.places.detail-per-minute:20}") int detailPerMinute) {
        this(daily, buckets, manager, searchMonthly, photoMonthly, detailMonthly,
                searchPerMinute, photoPerMinute, detailPerMinute,
                Clock.system(ZoneId.of("America/Los_Angeles")));
    }

    // 기존 검색/사진 테스트와 호출부 호환용 생성자.
    PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager, int searchMonthly, int photoMonthly,
            int searchPerMinute, int photoPerMinute, Clock clock) {
        this(daily, buckets, manager, searchMonthly, photoMonthly, searchMonthly,
                searchPerMinute, photoPerMinute, searchPerMinute, clock);
    }

    PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager, int searchMonthly, int photoMonthly, int detailMonthly,
            int searchPerMinute, int photoPerMinute, int detailPerMinute, Clock clock) {
        if (searchMonthly < 0 || photoMonthly < 0 || detailMonthly < 0
                || searchPerMinute < 0 || photoPerMinute < 0 || detailPerMinute < 0) {
            throw new IllegalArgumentException("Places 호출 제한은 0 이상이어야 합니다.");
        }
        this.daily = daily;
        this.buckets = buckets;
        this.searchMonthly = searchMonthly;
        this.photoMonthly = photoMonthly;
        this.detailMonthly = detailMonthly;
        this.searchPerMinute = searchPerMinute;
        this.photoPerMinute = photoPerMinute;
        this.detailPerMinute = detailPerMinute;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void reserve(boolean photo) {
        reserve(photo ? Kind.PHOTO : Kind.SEARCH, requestIp());
    }

    public void reserve(boolean photo, String identity) {
        reserve(photo ? Kind.PHOTO : Kind.SEARCH, identity);
    }

    public void reserveDetails() {
        reserve(Kind.DETAIL, requestIp());
    }

    public void reserveDetails(String identity) {
        reserve(Kind.DETAIL, identity);
    }

    private String requestIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getRemoteAddr() : "internal";
    }

    private void reserve(Kind kind, String identity) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                transaction.executeWithoutResult(status -> consume(kind, identity));
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
                if (attempt == 29) {
                    throw new BusinessException("PLACES_QUOTA_BUSY",
                            "호출량 갱신이 혼잡합니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
                }
            }
        }
    }

    private void consume(Kind kind, String identity) {
        LocalDate today = LocalDate.now(clock);
        long minute = clock.instant().getEpochSecond() / 60;
        String key = kind.name().toLowerCase() + ":ip:" + hash(identity);
        PlacesQuotaBucket rate = buckets.findById(key).orElseGet(() -> new PlacesQuotaBucket(key));
        if (rate.count(minute) >= perMinute(kind)) {
            throw new BusinessException("PLACES_RATE_LIMITED", "IP별 1분 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }

        String monthKey = "month:" + YearMonth.from(today);
        PlacesQuotaBucket month = buckets.findById(monthKey).orElseGet(() -> new PlacesQuotaBucket(monthKey));
        var records = daily.findByCallDateBetween(today.withDayOfMonth(1), today);
        int monthly = records.stream().mapToInt(record -> count(record, kind)).sum();
        PlacesApiUsageEntity day = daily.findById(today).orElseGet(() -> new PlacesApiUsageEntity(today, 0, 0, 0));
        if (monthly >= monthlyLimit(kind) || count(day, kind) >= dailyLimit(kind)) {
            throw new BusinessException("PLACES_QUOTA_EXCEEDED",
                    "Google Places 일일 또는 월간 한도를 초과했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        rate.increment(minute);
        month.increment(0);
        increment(day, kind);
        buckets.saveAndFlush(month);
        buckets.save(rate);
        daily.save(day);
    }

    private int perMinute(Kind kind) {
        return switch (kind) {
            case SEARCH -> searchPerMinute;
            case PHOTO -> photoPerMinute;
            case DETAIL -> detailPerMinute;
        };
    }

    private int monthlyLimit(Kind kind) {
        return switch (kind) {
            case SEARCH -> searchMonthly;
            case PHOTO -> photoMonthly;
            case DETAIL -> detailMonthly;
        };
    }

    private static int dailyLimit(Kind kind) {
        return switch (kind) {
            case SEARCH -> SEARCH_DAILY_LIMIT;
            case PHOTO -> PHOTO_DAILY_LIMIT;
            case DETAIL -> DETAIL_DAILY_LIMIT;
        };
    }

    private static int count(PlacesApiUsageEntity entity, Kind kind) {
        return switch (kind) {
            case SEARCH -> entity.getCallCount();
            case PHOTO -> entity.getPhotoCallCount();
            case DETAIL -> entity.getDetailCallCount();
        };
    }

    private static void increment(PlacesApiUsageEntity entity, Kind kind) {
        switch (kind) {
            case SEARCH -> entity.incrementCallCount();
            case PHOTO -> entity.incrementPhotoCallCount();
            case DETAIL -> entity.incrementDetailCallCount();
        }
    }

    private static String hash(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
