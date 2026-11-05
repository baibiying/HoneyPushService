package com.honeypush.service.web.dto;

import com.honeypush.service.entity.FocusSessionEntity;

import java.time.Instant;

public record FocusSessionDto(
        Integer id,
        String userId,
        String officerId,
        Integer taskId,
        String outcome,
        Integer durationMinutes,
        Instant completedAt,
        Integer coinsEarned,
        Integer distractionCount) {

    public static FocusSessionDto from(FocusSessionEntity entity) {
        return new FocusSessionDto(
                entity.getId(),
                entity.getUserId(),
                entity.getOfficerId(),
                entity.getTaskId(),
                entity.getOutcome(),
                entity.getDurationMinutes(),
                entity.getCompletedAt(),
                entity.getCoinsEarned(),
                entity.getDistractionCount());
    }
}
