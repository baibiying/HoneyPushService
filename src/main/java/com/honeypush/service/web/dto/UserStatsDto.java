package com.honeypush.service.web.dto;

import com.honeypush.service.entity.UserStatsEntity;

import java.time.Instant;
import java.util.List;

public record UserStatsDto(
        Integer id,
        String userId,
        Integer totalCoins,
        Integer consecutiveDays,
        Integer totalSessions,
        Instant lastSessionDate,
        List<String> unlockedBadges,
        Instant updatedAt) {

    public static UserStatsDto from(UserStatsEntity entity) {
        return new UserStatsDto(
                entity.getId(),
                entity.getUserId(),
                entity.getTotalCoins(),
                entity.getConsecutiveDays(),
                entity.getTotalSessions(),
                entity.getLastSessionDate(),
                entity.getUnlockedBadges(),
                entity.getUpdatedAt());
    }

    public static UserStatsDto emptyDefaults() {
        return new UserStatsDto(null, null, 0, 0, 0, null, List.of(), null);
    }

    public UserStatsDto withoutId() {
        return new UserStatsDto(null, userId, totalCoins, consecutiveDays, totalSessions, lastSessionDate, unlockedBadges, updatedAt);
    }
}
