package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.entity.PlacesApiUsageEntity;
import com.team.cultureevents.features.places.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.concurrent.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = "logging.level.org.hibernate.SQL=OFF")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlacesQuotaServiceTest {
    @Autowired PlacesApiUsageRepository daily;
    @Autowired PlacesQuotaBucketRepository buckets;
    @Autowired PlatformTransactionManager manager;
    Clock clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneId.of("America/Los_Angeles"));
    LocalDate today = LocalDate.of(2026, 9, 15);

    PlacesQuotaService service(int monthly, int rate) {
        return service(monthly, rate, 100);
    }
    PlacesQuotaService service(int monthly, int rate, int memberDaily) {
        return new PlacesQuotaService(daily, buckets, manager,
                monthly, monthly, monthly,
                rate, rate, rate,
                memberDaily, memberDaily, memberDaily,
                clock);
    }
    @BeforeEach void clean() { daily.deleteAll(); buckets.deleteAll(); }

    @Test void persistsAndLimitsSameIpAcrossServiceInstances() {
        service(900, 2).reserve(false, "a");
        service(900, 2).reserve(false, "a");
        assertThatThrownBy(() -> service(900, 2).reserve(false, "a"))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "PLACES_RATE_LIMITED");
        service(900, 2).reserve(false, "b");
        service(900, 2).reserve(true, "a");
        assertThat(daily.findById(today).orElseThrow().getCallCount()).isEqualTo(3);
        assertThat(daily.findById(today).orElseThrow().getPhotoCallCount()).isEqualTo(1);
    }

    @Test void monthlyLimitIncludesExistingDailyHistoryAndRollsBack() {
        daily.save(new PlacesApiUsageEntity(today.minusDays(1), 2, 2));
        service(3, 20).reserve(false, "a");
        service(3, 20).reserve(true, "a");
        for (boolean photo : List.of(false, true)) {
            assertThatThrownBy(() -> service(3, 20).reserve(photo, "b"))
                    .hasFieldOrPropertyWithValue("code", "PLACES_QUOTA_EXCEEDED");
        }
        assertThat(daily.findById(today).orElseThrow().getCallCount()).isEqualTo(1);
        assertThat(daily.findById(today).orElseThrow().getPhotoCallCount()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "false, 5, 100, 0, 5, PLACES_QUOTA_EXCEEDED",
            "true, 5, 100, 0, 5, PLACES_QUOTA_EXCEEDED",
            "false, 900, 100, 78, 2, PLACES_QUOTA_EXCEEDED",
            "true, 900, 100, 58, 2, PLACES_QUOTA_EXCEEDED",
            "false, 900, 3, 0, 3, PLACES_RATE_LIMITED"
    })
    void concurrentRequestsCannotExceedLimits(boolean photo, int monthly, int rate,
            int initial, int expected, String code) throws Exception {
        if (initial > 0) daily.save(new PlacesApiUsageEntity(today, photo ? 0 : initial, photo ? initial : 0));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String ip = rate == 3 ? "same-ip" : "ip-" + i;
                results.add(executor.submit(() -> {
                    start.await();
                    try { service(monthly, rate).reserve(photo, ip); return true; }
                    catch (BusinessException ex) {
                        assertThat(ex.getCode()).isEqualTo(code);
                        return false;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            for (var future : results) if (future.get(30, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(expected);
            var stored = daily.findById(today).orElseThrow();
            assertThat(photo ? stored.getPhotoCallCount() : stored.getCallCount()).isEqualTo(initial + expected);
        } finally { executor.shutdownNow(); }
    }

    @Test void detailQuotaIsIndependentFromSearchAndPhoto() {
        var svc = new PlacesQuotaService(daily, buckets, manager,
                900, 900, 900,
                100, 100, 1,
                100, 100, 1,
                clock);
        svc.reserveDetails("a");
        assertThatThrownBy(() -> svc.reserveDetails("a"))
                .hasFieldOrPropertyWithValue("code", "PLACES_RATE_LIMITED");
        var stored = daily.findById(today).orElseThrow();
        assertThat(stored.getCallCount()).isZero();
        assertThat(stored.getPhotoCallCount()).isZero();
        assertThat(stored.getDetailCallCount()).isEqualTo(1);
    }

    @Test void detailDailyLimitDoesNotAffectOtherClients() {
        var svc = new PlacesQuotaService(daily, buckets, manager,
                900, 900, 900,
                100, 100, 100,
                100, 100, 2,
                clock);
        svc.reserveDetails("client-a");
        svc.reserveDetails("client-a");
        assertThatThrownBy(() -> svc.reserveDetails("client-a"))
                .hasFieldOrPropertyWithValue("code", "PLACES_CLIENT_DAILY_LIMITED");

        svc.reserveDetails("client-b");
        assertThat(daily.findById(today).orElseThrow().getDetailCallCount()).isEqualTo(3);
    }

    @Test void dailyCapsRemainIndependent() {
        daily.save(new PlacesApiUsageEntity(today, 79, 59));
        service(900, 100).reserve(false, "a");
        service(900, 100).reserve(true, "a");
        for (boolean photo : List.of(false, true)) {
            assertThatThrownBy(() -> service(900, 100).reserve(photo, "b"))
                    .hasFieldOrPropertyWithValue("code", "PLACES_QUOTA_EXCEEDED");
        }
    }

    @Test void memberDailyLimitDoesNotAffectOtherMembers() {
        service(900, 100, 2).reserve(false, "member-1");
        service(900, 100, 2).reserve(false, "member-1");
        assertThatThrownBy(() -> service(900, 100, 2).reserve(false, "member-1"))
                .hasFieldOrPropertyWithValue("code", "PLACES_MEMBER_DAILY_LIMITED");

        service(900, 100, 2).reserve(false, "member-2");
        assertThat(daily.findById(today).orElseThrow().getCallCount()).isEqualTo(3);

        var nextDay = new PlacesQuotaService(daily, buckets, manager,
                900, 900, 900,
                100, 100, 100,
                2, 2, 2,
                Clock.offset(clock, Duration.ofDays(1)));
        nextDay.reserve(false, "member-1");
        assertThat(daily.findById(today.plusDays(1)).orElseThrow().getCallCount()).isEqualTo(1);
    }

    @Test void concurrentRequestsCannotExceedMemberDailyLimit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service(900, 100, 3).reserve(false, "same-member");
                        return true;
                    } catch (BusinessException ex) {
                        assertThat(ex.getCode()).isEqualTo("PLACES_MEMBER_DAILY_LIMITED");
                        return false;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(3);
            assertThat(daily.findById(today).orElseThrow().getCallCount()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void minuteWindowResetsForSameMember() {
        service(900, 1).reserve(false, "member-1");
        assertThatThrownBy(() -> service(900, 1).reserve(false, "member-1"))
                .hasFieldOrPropertyWithValue("code", "PLACES_RATE_LIMITED");
        var later = new PlacesQuotaService(daily, buckets, manager,
                900, 900, 900,
                1, 1, 1,
                100, 100, 100,
                Clock.offset(clock, Duration.ofMinutes(1)));
        later.reserve(false, "member-1");
        assertThat(daily.findById(today).orElseThrow().getCallCount()).isEqualTo(2);
    }

    @Test void billingMonthResetsAtPacificMidnight() {
        daily.save(new PlacesApiUsageEntity(LocalDate.of(2026, 9, 30), 1, 1));
        Clock before = Clock.fixed(Instant.parse("2026-10-01T06:59:59Z"), clock.getZone());
        var beforeReset = new PlacesQuotaService(daily, buckets, manager,
                1, 1, 1,
                100, 100, 100,
                100, 100, 100,
                before);
        assertThatThrownBy(() -> beforeReset.reserve(false, "a"))
                .hasFieldOrPropertyWithValue("code", "PLACES_QUOTA_EXCEEDED");
        var afterReset = new PlacesQuotaService(daily, buckets, manager,
                1, 1, 1,
                100, 100, 100,
                100, 100, 100,
                Clock.offset(before, Duration.ofSeconds(1)));
        afterReset.reserve(false, "a");
        assertThat(daily.findById(LocalDate.of(2026, 10, 1)).orElseThrow().getCallCount()).isEqualTo(1);
    }
}
