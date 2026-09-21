package com.culturemate.backend.repository;

import com.culturemate.backend.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByBrowserId(String browserId);

    Optional<Favorite> findByBrowserIdAndEventId(String browserId, String eventId);
}