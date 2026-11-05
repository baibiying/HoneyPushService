package com.honeypush.service.service;

import tools.jackson.databind.ObjectMapper;
import com.honeypush.service.ai.ScheduleTimes;
import com.honeypush.service.entity.TaskEntity;
import com.honeypush.service.web.dto.FocusSessionDto;
import com.honeypush.service.web.dto.TaskDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaskPerformanceService {
    private static final long SEGMENT_END_GRACE_MS = 60 * 1000L;
    private static final long SEGMENT_START_EARLY_MS = 2 * 60 * 1000L;

    private final ObjectMapper objectMapper;

    public TaskPerformanceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record TaskPerformanceItem(
            int taskId,
            String taskText,
            String scheduledStartAt,
            String scheduledEndAt,
            String at,
            String reason,
            Integer coinsEarned,
            Integer distractionCount) {}

    public record TaskPerformanceReport(
            int totalCoins,
            int successCount,
            int failureCount,
            List<TaskPerformanceItem> successes,
            List<TaskPerformanceItem> failures) {}

    public TaskPerformanceReport buildReport(
            List<TaskDto> tasks, List<FocusSessionDto> sessions, int totalCoins, Instant now) {
        Map<Integer, List<FocusSessionDto>> sessionsByTaskId = new HashMap<>();
        for (FocusSessionDto session : sessions) {
            if (session.taskId() == null) {
                continue;
            }
            sessionsByTaskId.computeIfAbsent(session.taskId(), k -> new ArrayList<>()).add(session);
        }

        List<TaskPerformanceItem> successes = new ArrayList<>();
        List<TaskPerformanceItem> failures = new ArrayList<>();
        Set<Integer> seenSuccess = new HashSet<>();
        Set<Integer> seenFailure = new HashSet<>();

        for (TaskDto task : tasks) {
            List<FocusSessionDto> taskSessions = sessionsByTaskId.getOrDefault(task.id(), List.of());
            FocusSessionDto completed = latestSession(taskSessions, "completed");
            if (completed != null) {
                if (!seenSuccess.contains(task.id())) {
                    seenSuccess.add(task.id());
                    successes.add(buildItem(task, completed.completedAt().toString(), null, completed.coinsEarned(), completed.distractionCount()));
                }
                continue;
            }
            FocusSessionDto failed = latestSession(taskSessions, "failed");
            if (failed != null) {
                if (!seenFailure.contains(task.id())) {
                    seenFailure.add(task.id());
                    failures.add(buildItem(
                            task,
                            failed.completedAt().toString(),
                            "supervision_failed",
                            failed.coinsEarned(),
                            failed.distractionCount()));
                }
                continue;
            }

            var segments = ScheduleTimes.expandScheduledTaskToFocusSegments(
                    task.id(),
                    task.scheduledStartAt(),
                    task.durationMinutes(),
                    task.scheduledFocusSegments() == null
                            ? null
                            : objectMapper.valueToTree(task.scheduledFocusSegments()));
            if (segments.isEmpty()) {
                continue;
            }
            ScheduleTimes.TaskFocusSegment missed = segments.stream()
                    .filter(segment -> isSegmentMissed(segment, taskSessions, now))
                    .findFirst()
                    .orElse(null);
            if (missed == null) {
                continue;
            }
            if (!seenFailure.contains(task.id())) {
                seenFailure.add(task.id());
                failures.add(buildItem(task, missed.startAt(), "missed_schedule", null, null));
            }
        }

        successes.sort(Comparator.comparing((TaskPerformanceItem i) -> Instant.parse(i.at())).reversed());
        failures.sort(Comparator.comparing((TaskPerformanceItem i) -> Instant.parse(i.at())).reversed());

        return new TaskPerformanceReport(totalCoins, successes.size(), failures.size(), successes, failures);
    }

    private static FocusSessionDto latestSession(List<FocusSessionDto> sessions, String outcome) {
        return sessions.stream()
                .filter(s -> outcome.equals(s.outcome()))
                .max(Comparator.comparing(FocusSessionDto::completedAt))
                .orElse(null);
    }

    private static boolean isSegmentMissed(
            ScheduleTimes.TaskFocusSegment segment, List<FocusSessionDto> sessions, Instant now) {
        long endMs = Instant.parse(segment.endAt()).toEpochMilli();
        if (now.toEpochMilli() < endMs) {
            return false;
        }
        return sessions.stream().noneMatch(session -> sessionOverlapsFocusSegment(session, segment));
    }

    private static boolean sessionOverlapsFocusSegment(
            FocusSessionDto session, ScheduleTimes.TaskFocusSegment segment) {
        if (session.taskId() == null) {
            return false;
        }
        long t = session.completedAt().toEpochMilli();
        long start = Instant.parse(segment.startAt()).toEpochMilli();
        long end = Instant.parse(segment.endAt()).toEpochMilli();
        return t >= start - SEGMENT_START_EARLY_MS && t <= end + SEGMENT_END_GRACE_MS;
    }

    private static TaskPerformanceItem buildItem(
            TaskDto task, String at, String reason, Integer coinsEarned, Integer distractionCount) {
        return new TaskPerformanceItem(
                task.id(),
                task.text(),
                task.scheduledStartAt() != null ? task.scheduledStartAt().toString() : null,
                task.scheduledEndAt() != null ? task.scheduledEndAt().toString() : null,
                at,
                reason,
                coinsEarned,
                distractionCount);
    }
}
