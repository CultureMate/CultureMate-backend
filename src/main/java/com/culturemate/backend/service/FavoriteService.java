package com.culturemate.backend.service;

import com.culturemate.backend.entity.Favorite;
import com.culturemate.backend.repository.FavoriteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

    @Autowired
    private FavoriteRepository favoriteRepository;

    public Favorite addFavorite(String browserId, String eventId, String title, String startDate, String endDate, String place) {
        Optional<Favorite> existing = favoriteRepository.findByBrowserIdAndEventId(browserId, eventId);

        if (existing.isPresent()) {
            throw new IllegalStateException("이미 저장된 행사입니다.");
        }

        Favorite favorite = new Favorite();
        favorite.setBrowserId(browserId);
        favorite.setEventId(eventId);
        favorite.setTitle(title);
        favorite.setStartDate(startDate);
        favorite.setEndDate(endDate);
        favorite.setPlace(place);

        return favoriteRepository.save(favorite);
    }

    public List<Favorite> getFavorites(String browserId) {
        return favoriteRepository.findByBrowserId(browserId);
    }

    public void deleteFavorite(Long favoriteId) {
        favoriteRepository.deleteById(favoriteId);
    }
}