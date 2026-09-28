package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.entity.*;
import com.team.cultureevents.features.places.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PlacesQuotaService {
    private static final int SEARCH_DAILY_LIMIT = 80;
    private static final int PHOTO_DAILY_LIMIT = 60;
    private final PlacesApiUsageRepository daily;
    private final PlacesQuotaBucketRepository buckets;
    private final TransactionTemplate transaction;
    private final int searchMonthly;
    private final int photoMonthly;
    private final int searchPerMinute;
    private final int photoPerMinute;
    private final int searchPerMemberDaily;
    private final int photoPerMemberDaily;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager,
            @Value("${app.places.search-monthly-limit:900}") int searchMonthly,
            @Value("${app.places.photo-monthly-limit:900}") int photoMonthly,
            @Value("${app.places.search-per-minute:20}") int searchPerMinute,
            @Value("${app.places.photo-per-minute:40}") int photoPerMinute,
            @Value("${app.places.search-per-member-daily:20}") int searchPerMemberDaily,
            @Value("${app.places.photo-per-member-daily:20}") int photoPerMemberDaily) {
        this(daily, buckets, manager, searchMonthly, photoMonthly, searchPerMinute, photoPerMinute,
                searchPerMemberDaily, photoPerMemberDaily,
                Clock.system(ZoneId.of("America/Los_Angeles")));
    }

    PlacesQuotaService(PlacesApiUsageRepository daily, PlacesQuotaBucketRepository buckets,
            PlatformTransactionManager manager, int searchMonthly, int photoMonthly,
            int searchPerMinute, int photoPerMinute,
            int searchPerMemberDaily, int photoPerMemberDaily, Clock clock) {
        if (searchMonthly < 0 || photoMonthly < 0 || searchPerMinute < 0 || photoPerMinute < 0
                || searchPerMemberDaily < 0 || photoPerMemberDaily < 0) {
            throw new IllegalArgumentException("Places 호출 제한은 0 이상이어야 합니다.");
        }
        this.daily = daily;
        this.buckets = buckets;
        this.searchMonthly = searchMonthly;
        this.photoMonthly = photoMonthly;
        this.searchPerMinute = searchPerMinute;
        this.photoPerMinute = photoPerMinute;
        this.searchPerMemberDaily = searchPerMemberDaily;
        this.photoPerMemberDaily = photoPerMemberDaily;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void reserve(boolean photo, long memberId) {
        reserve(photo, Long.toString(memberId));
    }

    void reserve(boolean photo, String identity) {
        // Every retry starts a fresh transaction. Commit completes before the external API call.
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                transaction.executeWithoutResult(status -> consume(photo, identity));
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
                if (attempt == 29) throw new BusinessException("PLACES_QUOTA_BUSY",
                        "호출량 갱신이 혼잡합니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
    }

    private void consume(boolean photo, String identity) {
        LocalDate today = LocalDate.now(clock);
        long minute = clock.instant().getEpochSecond() / 60;
        String kind = photo ? "photo" : "search";
        String memberHash = hash(identity);
        String rateKey = kind + ":member-minute:" + memberHash;
        PlacesQuotaBucket rate = buckets.findById(rateKey).orElseGet(() -> new PlacesQuotaBucket(rateKey));
        if (rate.count(minute) >= (photo ? photoPerMinute : searchPerMinute)) {
            throw new BusinessException("PLACES_RATE_LIMITED", "회원별 1분 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }
        String memberDayKey = kind + ":member-day:" + memberHash;
        PlacesQuotaBucket memberDay = buckets.findById(memberDayKey)
                .orElseGet(() -> new PlacesQuotaBucket(memberDayKey));
        if (memberDay.count(today.toEpochDay()) >= (photo ? photoPerMemberDaily : searchPerMemberDaily)) {
            throw new BusinessException("PLACES_MEMBER_DAILY_LIMITED",
                    "회원별 일일 호출 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS);
        }
        // Both SKUs update the same monthly version, serializing daily creation and month sums.
        String monthKey = "month:" + YearMonth.from(today);
        PlacesQuotaBucket month = buckets.findById(monthKey).orElseGet(() -> new PlacesQuotaBucket(monthKey));
        var records = daily.findByCallDateBetween(today.withDayOfMonth(1), today);
        int monthly = records.stream().mapToInt(r -> photo ? r.getPhotoCallCount() : r.getCallCount()).sum();
        PlacesApiUsageEntity day = daily.findById(today).orElseGet(() -> new PlacesApiUsageEntity(today, 0, 0));
        if (monthly >= (photo ? photoMonthly : searchMonthly)
                || (photo ? day.getPhotoCallCount() >= PHOTO_DAILY_LIMIT : day.getCallCount() >= SEARCH_DAILY_LIMIT)) {
            throw new BusinessException("PLACES_QUOTA_EXCEEDED", "Google Places 일일 또는 월간 한도를 초과했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        rate.increment(minute);
        memberDay.increment(today.toEpochDay());
        month.increment(0);
        if (photo) day.incrementPhotoCallCount(); else day.incrementCallCount();
        buckets.saveAndFlush(month);
        buckets.save(rate);
        buckets.save(memberDay);
        daily.save(day);
    }

    private static String hash(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
