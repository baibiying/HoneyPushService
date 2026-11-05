package com.honeypush.service.web.dto;

import com.honeypush.service.entity.ScheduledFocusSegmentEmbeddable;
import com.honeypush.service.entity.TaskEntity;

import java.time.Instant;
import java.util.List;

public record TaskDto(
        Integer id,
        String userId,
        String text,
        Integer durationMinutes,
        String category,
        Boolean checked,
        Instant deadline,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        List<ScheduledFocusSegmentEmbeddable> scheduledFocusSegments,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskDto from(TaskEntity entity) {
        return new TaskDto(
                entity.getId(),
                entity.getUserId(),
                entity.getText(),
                entity.getDurationMinutes(),
                entity.getCategory(),
                entity.getChecked(),
                entity.getDeadline(),
                entity.getScheduledStartAt(),
                entity.getScheduledEndAt(),
                entity.getScheduledFocusSegments(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
