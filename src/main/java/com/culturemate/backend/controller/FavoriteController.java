package com.culturemate.backend.controller;

import com.culturemate.backend.entity.Favorite;
import com.culturemate.backend.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping
    public Favorite addFavorite(
            @RequestParam String browserId,
            @RequestParam String eventId,
            @RequestParam String title,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String place) {
        return favoriteService.addFavorite(browserId, eventId, title, startDate, endDate, place);
    }

    @GetMapping
    public List<Favorite> getFavorites(@RequestParam String browserId) {
        return favoriteService.getFavorites(browserId);
    }

    @DeleteMapping("/{id}")
    public void deleteFavorite(@PathVariable Long id) {
        favoriteService.deleteFavorite(id);
    }
}