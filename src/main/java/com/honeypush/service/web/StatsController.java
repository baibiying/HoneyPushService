package com.honeypush.service.web;

import com.honeypush.service.service.UserStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final UserStatsService userStatsService;

    public StatsController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
    }

    @GetMapping
    ResponseEntity<?> get() {
        String userId = CurrentUser.require().id();
        return userStatsService
                .get(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "totalCoins", 0,
                        "consecutiveDays", 0,
                        "totalSessions", 0,
                        "unlockedBadges", List.of())));
    }

    @PostMapping
    ResponseEntity<?> post(@RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require().id();
        int delta = body.get("deltaCoins") instanceof Number n ? n.intValue() : 15;
        return ResponseEntity.ok(userStatsService.upsert(userId, delta));
    }
}
