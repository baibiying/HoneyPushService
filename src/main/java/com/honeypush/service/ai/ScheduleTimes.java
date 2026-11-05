package com.honeypush.service.ai;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ScheduleTimes {
    public static final int POMODORO_FOCUS_MINUTES = 25;
    public static final int POMODORO_BREAK_MINUTES = 5;
    public static final int TASK_GAP_MINUTES = 10;

    private static final long STEP_MS = 5 * 60 * 1000L;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private ScheduleTimes() {}

    public record PomodoroSegment(String kind, int minutes) {}

    public record ScheduledFocusSegment(String startAt, String endAt) {}

    public record TimedScheduleItem(
            int id,
            String category,
            int durationMinutes,
            int order,
            String scheduledStartAt,
            String scheduledEndAt,
            List<ScheduledFocusSegment> focusSegments) {}

    public record AssignScheduleResult(List<TimedScheduleItem> schedule, List<Integer> unscheduledIds) {}

    private record TimeRangeMs(long start, long end) {}

    public static List<PomodoroSegment> planPomodoroSegments(int focusMinutes) {
        List<PomodoroSegment> segments = new ArrayList<>();
        int remaining = Math.max(1, Math.round(focusMinutes));
        while (remaining > 0) {
            int focusLen = Math.min(POMODORO_FOCUS_MINUTES, remaining);
            segments.add(new PomodoroSegment("focus", focusLen));
            remaining -= focusLen;
            if (remaining > 0) {
                segments.add(new PomodoroSegment("break", POMODORO_BREAK_MINUTES));
            }
        }
        return segments;
    }

    public static AssignScheduleResult assignScheduleTimes(
            List<SchedulePriority.SchedulableTaskRef> items,
            Map<Integer, Instant> deadlinesById,
            List<AvailabilityHelper.AvailabilitySlot> availabilitySlots,
            Integer timezoneOffsetMinutes,
            Instant now) {
        List<AvailabilityHelper.AvailabilityWindow> windows =
                AvailabilityHelper.buildAvailabilityWindows(availabilitySlots, now, timezoneOffsetMinutes);
        List<SchedulePriority.SchedulableTaskRef> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> SchedulePriority.compareTasksForSchedule(a, b, deadlinesById));

        if (windows.isEmpty()) {
            return new AssignScheduleResult(
                    List.of(), sorted.stream().map(SchedulePriority.SchedulableTaskRef::id).toList());
        }

        List<TimeRangeMs> occupied = new ArrayList<>();
        List<TimedScheduleItem> result = new ArrayList<>();
        List<Integer> unscheduledIds = new ArrayList<>();
        Instant scheduleStartAfter = windows.get(0).start();

        for (SchedulePriority.SchedulableTaskRef item : sorted) {
            Instant deadline = deadlinesById.get(item.id());
            Placement placement = placeTaskWithPomodoros(
                    windows, item, deadline, occupied, scheduleStartAfter, timezoneOffsetMinutes);
            if (placement.placed() == null) {
                unscheduledIds.add(item.id());
                continue;
            }
            result.add(placement.placed());
            long gapStartMs = placement.notBefore().toEpochMilli();
            occupied.add(new TimeRangeMs(gapStartMs, gapStartMs + TASK_GAP_MINUTES * 60L * 1000L));
            Instant afterGap = advanceNotBefore(windows, placement.notBefore(), TASK_GAP_MINUTES);
            scheduleStartAfter = afterGap != null ? afterGap : Instant.ofEpochMilli(gapStartMs + TASK_GAP_MINUTES * 60L * 1000L);
        }
        return new AssignScheduleResult(result, unscheduledIds);
    }

    private record Placement(TimedScheduleItem placed, Instant notBefore, List<TimeRangeMs> addedRanges) {}

    private static Placement placeTaskWithPomodoros(
            List<AvailabilityHelper.AvailabilityWindow> windows,
            SchedulePriority.SchedulableTaskRef item,
            Instant deadline,
            List<TimeRangeMs> occupied,
            Instant scheduleStartAfter,
            Integer timezoneOffsetMinutes) {
        List<PomodoroSegment> segments = planPomodoroSegments(item.durationMinutes());
        List<Long> candidateDays = collectLocalDayStarts(windows, timezoneOffsetMinutes).stream()
                .filter(dayStart -> dayStart + DAY_MS > scheduleStartAfter.toEpochMilli())
                .toList();

        for (Long dayStart : candidateDays) {
            AttemptResult onDay = attempt(windows, item, deadline, occupied, scheduleStartAfter, timezoneOffsetMinutes, segments, dayStart);
            if (onDay.placed() != null) {
                occupied.addAll(onDay.addedRanges());
                return new Placement(onDay.placed(), onDay.notBefore(), onDay.addedRanges());
            }
        }
        AttemptResult crossDay = attempt(windows, item, deadline, occupied, scheduleStartAfter, timezoneOffsetMinutes, segments, null);
        if (crossDay.placed() != null) {
            occupied.addAll(crossDay.addedRanges());
            return new Placement(crossDay.placed(), crossDay.notBefore(), crossDay.addedRanges());
        }
        return new Placement(null, scheduleStartAfter, List.of());
    }

    private record AttemptResult(TimedScheduleItem placed, Instant notBefore, List<TimeRangeMs> addedRanges) {}

    private static AttemptResult attempt(
            List<AvailabilityHelper.AvailabilityWindow> windows,
            SchedulePriority.SchedulableTaskRef item,
            Instant deadline,
            List<TimeRangeMs> occupied,
            Instant scheduleStartAfter,
            Integer timezoneOffsetMinutes,
            List<PomodoroSegment> segments,
            Long restrictDayStart) {
        List<AvailabilityHelper.AvailabilityWindow> dayWindows =
                restrictDayStart == null ? windows : clipWindowsToLocalDay(windows, restrictDayStart);
        if (dayWindows.isEmpty()) {
            return new AttemptResult(null, scheduleStartAfter, List.of());
        }

        List<TimeRangeMs> trialOccupied = new ArrayList<>(occupied);
        Instant firstFocusStart = null;
        Instant lastFocusEnd = null;
        List<ScheduledFocusSegment> focusSegments = new ArrayList<>();
        Instant notBefore = scheduleStartAfter;

        if (restrictDayStart != null) {
            long dayEndMs = restrictDayStart + DAY_MS;
            if (notBefore.toEpochMilli() >= dayEndMs) {
                return new AttemptResult(null, scheduleStartAfter, List.of());
            }
            long earliestOnDay = dayWindows.get(0).start().toEpochMilli();
            notBefore = Instant.ofEpochMilli(
                    Math.max(notBefore.toEpochMilli(), Math.max(restrictDayStart, earliestOnDay)));
        }

        for (PomodoroSegment segment : segments) {
            if ("break".equals(segment.kind())) {
                Instant afterBreak = advanceNotBefore(dayWindows, notBefore, segment.minutes());
                if (afterBreak == null) {
                    return new AttemptResult(null, scheduleStartAfter, List.of());
                }
                notBefore = afterBreak;
                continue;
            }
            long durationMs = segment.minutes() * 60L * 1000L;
            Slot slot = findEarliestFocusSlot(dayWindows, durationMs, notBefore, trialOccupied, deadline);
            if (slot == null) {
                return new AttemptResult(null, scheduleStartAfter, List.of());
            }
            if (firstFocusStart == null) {
                firstFocusStart = slot.start();
            }
            lastFocusEnd = slot.end();
            focusSegments.add(new ScheduledFocusSegment(slot.start().toString(), slot.end().toString()));
            trialOccupied.add(new TimeRangeMs(slot.start().toEpochMilli(), slot.end().toEpochMilli()));
            notBefore = slot.end();
        }

        if (firstFocusStart == null || lastFocusEnd == null) {
            return new AttemptResult(null, scheduleStartAfter, List.of());
        }

        List<TimeRangeMs> added = trialOccupied.subList(occupied.size(), trialOccupied.size());
        TimedScheduleItem placed = new TimedScheduleItem(
                item.id(),
                item.category(),
                item.durationMinutes(),
                item.order(),
                firstFocusStart.toString(),
                lastFocusEnd.toString(),
                focusSegments);
        return new AttemptResult(placed, notBefore, new ArrayList<>(added));
    }

    private record Slot(Instant start, Instant end) {}

    private static Slot findEarliestFocusSlot(
            List<AvailabilityHelper.AvailabilityWindow> windows,
            long durationMs,
            Instant notBefore,
            List<TimeRangeMs> occupied,
            Instant deadline) {
        long notBeforeMs = notBefore.toEpochMilli();
        for (AvailabilityHelper.AvailabilityWindow window : windows) {
            long candidate = Math.max(window.start().toEpochMilli(), notBeforeMs);
            while (candidate + durationMs <= window.end().toEpochMilli()) {
                long end = candidate + durationMs;
                if (!rangesOverlap(candidate, end, occupied)) {
                    if (deadline != null && end > deadline.toEpochMilli()) {
                        long altStart = deadline.toEpochMilli() - durationMs;
                        if (altStart >= Math.max(window.start().toEpochMilli(), notBeforeMs)
                                && altStart + durationMs <= window.end().toEpochMilli()
                                && !rangesOverlap(altStart, altStart + durationMs, occupied)) {
                            return new Slot(Instant.ofEpochMilli(altStart), Instant.ofEpochMilli(altStart + durationMs));
                        }
                        candidate += STEP_MS;
                        continue;
                    }
                    return new Slot(Instant.ofEpochMilli(candidate), Instant.ofEpochMilli(end));
                }
                long candidateMs = candidate;
                TimeRangeMs blocker = occupied.stream()
                        .filter(slot -> candidateMs < slot.end() && end > slot.start())
                        .findFirst()
                        .orElse(null);
                candidate = blocker != null ? blocker.end() : candidate + STEP_MS;
            }
        }
        return null;
    }

    private static boolean rangesOverlap(long start, long end, List<TimeRangeMs> occupied) {
        return occupied.stream().anyMatch(slot -> start < slot.end() && end > slot.start());
    }

    private static Instant advanceNotBefore(
            List<AvailabilityHelper.AvailabilityWindow> windows, Instant from, int minutes) {
        long needMs = minutes * 60L * 1000L;
        long pos = from.toEpochMilli();
        for (AvailabilityHelper.AvailabilityWindow window : windows) {
            if (window.end().toEpochMilli() <= pos) {
                continue;
            }
            long startInWindow = Math.max(pos, window.start().toEpochMilli());
            if (startInWindow >= window.end().toEpochMilli()) {
                continue;
            }
            long available = window.end().toEpochMilli() - startInWindow;
            long take = Math.min(needMs, available);
            pos = startInWindow + take;
            needMs -= take;
            if (needMs <= 0) {
                return Instant.ofEpochMilli(pos);
            }
        }
        return null;
    }

    private static long getLocalDayStartMs(Instant instant, Integer timezoneOffsetMinutes) {
        if (timezoneOffsetMinutes == null) {
            return instant.atZone(java.time.ZoneOffset.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }
        TimezoneUtil.LocalParts parts = TimezoneUtil.localParts(instant, timezoneOffsetMinutes);
        return TimezoneUtil.localDateTimeToUtc(parts.year(), parts.month(), parts.day(), 0, 0, timezoneOffsetMinutes)
                .toEpochMilli();
    }

    private static List<AvailabilityHelper.AvailabilityWindow> clipWindowsToLocalDay(
            List<AvailabilityHelper.AvailabilityWindow> windows, long dayStartMs) {
        long dayEndMs = dayStartMs + DAY_MS;
        List<AvailabilityHelper.AvailabilityWindow> clipped = new ArrayList<>();
        for (AvailabilityHelper.AvailabilityWindow window : windows) {
            long startMs = Math.max(window.start().toEpochMilli(), dayStartMs);
            long endMs = Math.min(window.end().toEpochMilli(), dayEndMs);
            if (endMs > startMs) {
                clipped.add(new AvailabilityHelper.AvailabilityWindow(
                        Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)));
            }
        }
        return clipped;
    }

    private static List<Long> collectLocalDayStarts(
            List<AvailabilityHelper.AvailabilityWindow> windows, Integer timezoneOffsetMinutes) {
        return windows.stream()
                .map(w -> getLocalDayStartMs(w.start(), timezoneOffsetMinutes))
                .distinct()
                .sorted()
                .toList();
    }

    public static List<TaskFocusSegment> expandScheduledTaskToFocusSegments(
            int taskId,
            Instant scheduledStartAt,
            Integer durationMinutes,
            JsonNode scheduledFocusSegments) {
        List<ScheduledFocusSegment> stored = parseStoredFocusSegments(scheduledFocusSegments);
        if (stored != null) {
            List<TaskFocusSegment> result = new ArrayList<>();
            for (int i = 0; i < stored.size(); i++) {
                ScheduledFocusSegment segment = stored.get(i);
                result.add(new TaskFocusSegment(taskId, i, segment.startAt(), segment.endAt()));
            }
            return result;
        }
        if (scheduledStartAt == null) {
            return List.of();
        }
        List<PomodoroSegment> segments = planPomodoroSegments(durationMinutes != null ? durationMinutes : 25);
        Instant cursor = scheduledStartAt;
        List<TaskFocusSegment> result = new ArrayList<>();
        int index = 0;
        for (PomodoroSegment segment : segments) {
            if ("break".equals(segment.kind())) {
                cursor = cursor.plusSeconds(segment.minutes() * 60L);
                continue;
            }
            Instant start = cursor;
            Instant end = cursor.plusSeconds(segment.minutes() * 60L);
            result.add(new TaskFocusSegment(taskId, index, start.toString(), end.toString()));
            cursor = end;
            index++;
        }
        return result;
    }

    public record TaskFocusSegment(int taskId, int segmentIndex, String startAt, String endAt) {}

    private static List<ScheduledFocusSegment> parseStoredFocusSegments(JsonNode raw) {
        if (raw == null || !raw.isArray() || raw.isEmpty()) {
            return null;
        }
        List<ScheduledFocusSegment> parsed = new ArrayList<>();
        for (JsonNode item : raw) {
            String startAt = item.path("startAt").asText("");
            String endAt = item.path("endAt").asText("");
            if (startAt.isBlank() || endAt.isBlank()) {
                continue;
            }
            try {
                Instant.parse(startAt);
                Instant.parse(endAt);
            } catch (Exception e) {
                continue;
            }
            parsed.add(new ScheduledFocusSegment(startAt, endAt));
        }
        return parsed.isEmpty() ? null : parsed;
    }
}
