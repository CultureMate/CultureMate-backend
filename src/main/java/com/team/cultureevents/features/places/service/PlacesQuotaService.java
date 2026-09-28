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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
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
    private final int searchPerMemberDaily;
    private final int photoPerMemberDaily;
    private final int detailPerMemberDaily;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager,
            @Value("${app.places.search-monthly-limit:900}") int searchMonthly,
            @Value("${app.places.photo-monthly-limit:900}") int photoMonthly,
            @Value("${app.places.detail-monthly-limit:900}") int detailMonthly,
            @Value("${app.places.search-per-minute:20}") int searchPerMinute,
            @Value("${app.places.photo-per-minute:40}") int photoPerMinute,
            @Value("${app.places.detail-per-minute:20}") int detailPerMinute,
            @Value("${app.places.search-per-member-daily:20}") int searchPerMemberDaily,
            @Value("${app.places.photo-per-member-daily:20}") int photoPerMemberDaily,
            @Value("${app.places.detail-per-member-daily:20}") int detailPerMemberDaily) {
        this(daily, buckets, manager,
                searchMonthly, photoMonthly, detailMonthly,
                searchPerMinute, photoPerMinute, detailPerMinute,
                searchPerMemberDaily, photoPerMemberDaily, detailPerMemberDaily,
                Clock.system(ZoneId.of("America/Los_Angeles")));
    }

    PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager,
            int searchMonthly, int photoMonthly, int detailMonthly,
            int searchPerMinute, int photoPerMinute, int detailPerMinute,
            int searchPerMemberDaily, int photoPerMemberDaily, int detailPerMemberDaily,
            Clock clock) {
        if (searchMonthly < 0 || photoMonthly < 0 || detailMonthly < 0
                || searchPerMinute < 0 || photoPerMinute < 0 || detailPerMinute < 0
                || searchPerMemberDaily < 0 || photoPerMemberDaily < 0 || detailPerMemberDaily < 0) {
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
        this.searchPerMemberDaily = searchPerMemberDaily;
        this.photoPerMemberDaily = photoPerMemberDaily;
        this.detailPerMemberDaily = detailPerMemberDaily;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void reserve(boolean photo, long memberId) {
        reserve(photo ? Kind.PHOTO : Kind.SEARCH, "member", Long.toString(memberId));
    }

    public void reserveDetails(long memberId) {
        reserve(Kind.DETAIL, "member", Long.toString(memberId));
    }

    void reserve(boolean photo, String identity) {
        reserve(photo ? Kind.PHOTO : Kind.SEARCH, "member", identity);
    }

    private void reserve(Kind kind, String scope, String identity) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                transaction.executeWithoutResult(status -> consume(kind, scope, identity));
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
                if (attempt == 29) {
                    throw new BusinessException("PLACES_QUOTA_BUSY",
                            "호출량 갱신이 혼잡합니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
                }
            }
        }
    }

    private void consume(Kind kind, String scope, String identity) {
        LocalDate today = LocalDate.now(clock);
        long minute = clock.instant().getEpochSecond() / 60;
        String prefix = kind.name().toLowerCase() + ":" + scope;
        String identityHash = hash(identity);

        String minuteKey = prefix + "-minute:" + identityHash;
        PlacesQuotaBucket minuteBucket = buckets.findById(minuteKey)
                .orElseGet(() -> new PlacesQuotaBucket(minuteKey));
        if (minuteBucket.count(minute) >= perMinute(kind)) {
            throw new BusinessException("PLACES_RATE_LIMITED",
                    "회원별 1분 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }

        String dayKey = prefix + "-day:" + identityHash;
        PlacesQuotaBucket identityDay = buckets.findById(dayKey)
                .orElseGet(() -> new PlacesQuotaBucket(dayKey));
        if (identityDay.count(today.toEpochDay()) >= perMemberDaily(kind)) {
            throw new BusinessException("PLACES_MEMBER_DAILY_LIMITED",
                    "회원별 일일 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }

        String monthKey = "month:" + YearMonth.from(today);
        PlacesQuotaBucket month = buckets.findById(monthKey)
                .orElseGet(() -> new PlacesQuotaBucket(monthKey));
        var records = daily.findByCallDateBetween(today.withDayOfMonth(1), today);
        int monthly = records.stream().mapToInt(record -> count(record, kind)).sum();
        PlacesApiUsageEntity day = daily.findById(today)
                .orElseGet(() -> new PlacesApiUsageEntity(today, 0, 0, 0));
        if (monthly >= monthlyLimit(kind) || count(day, kind) >= globalDailyLimit(kind)) {
            throw new BusinessException("PLACES_QUOTA_EXCEEDED",
                    "Google Places 일일 또는 월간 한도를 초과했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        minuteBucket.increment(minute);
        identityDay.increment(today.toEpochDay());
        month.increment(0);
        increment(day, kind);
        buckets.saveAndFlush(month);
        buckets.save(minuteBucket);
        buckets.save(identityDay);
        daily.save(day);
    }

    private int perMinute(Kind kind) {
        return switch (kind) {
            case SEARCH -> searchPerMinute;
            case PHOTO -> photoPerMinute;
            case DETAIL -> detailPerMinute;
        };
    }

    private int perMemberDaily(Kind kind) {
        return switch (kind) {
            case SEARCH -> searchPerMemberDaily;
            case PHOTO -> photoPerMemberDaily;
            case DETAIL -> detailPerMemberDaily;
        };
    }

    private int monthlyLimit(Kind kind) {
        return switch (kind) {
            case SEARCH -> searchMonthly;
            case PHOTO -> photoMonthly;
            case DETAIL -> detailMonthly;
        };
    }

    private static int globalDailyLimit(Kind kind) {
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
