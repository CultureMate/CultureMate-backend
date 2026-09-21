package com.team.cultureevents.features.seoul;

import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 명세: 조회 결과 30분 캐시. 서울시 원본은 DB에 저장하지 않음. */
@Component
public class SeoulEventCache {

    private final long ttlMillis;
    private final AtomicReference<CacheEntry> entry = new AtomicReference<>();

    public SeoulEventCache(AppProperties props) {
        this.ttlMillis = props.seoulApi().cacheTtlMinutes() * 60_000L;
    }

    public List<SeoulEvent> getIfFresh() {
        CacheEntry current = entry.get();
        if (current == null) {
            return null;
        }
        if (Instant.now().isAfter(current.expiresAt())) {
            return null;
        }
        return current.events();
    }

    public void put(List<SeoulEvent> events) {
        entry.set(new CacheEntry(List.copyOf(events), Instant.now().plusMillis(ttlMillis)));
    }

    public void clear() {
        entry.set(null);
    }

    private record CacheEntry(List<SeoulEvent> events, Instant expiresAt) {
    }
}
