package com.honeypush.service.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AvailabilityHelper {
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    private AvailabilityHelper() {}

    public record AvailabilitySlot(String date, String startTime, String endTime) {}

    public record AvailabilityWindow(Instant start, Instant end) {}

    public static Instant parseLocalDateTime(String date, String time, Integer timezoneOffsetMinutes) {
        Matcher dateMatch = DATE_PATTERN.matcher(date.trim());
        Matcher timeMatch = TIME_PATTERN.matcher(time.trim());
        if (!dateMatch.matches() || !timeMatch.matches()) {
            return null;
        }
        int year = Integer.parseInt(dateMatch.group(1));
        int month = Integer.parseInt(dateMatch.group(2));
        int day = Integer.parseInt(dateMatch.group(3));
        int hour = Integer.parseInt(timeMatch.group(1));
        int minute = Integer.parseInt(timeMatch.group(2));

        if (hour == 24 && minute == 0) {
            Instant dayStart = TimezoneUtil.localDateTimeToUtc(year, month, day, 0, 0, timezoneOffsetMinutes);
            return Instant.ofEpochMilli(dayStart.toEpochMilli() + DAY_MS);
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return TimezoneUtil.localDateTimeToUtc(year, month, day, hour, minute, timezoneOffsetMinutes);
    }

    public static List<AvailabilityWindow> buildAvailabilityWindows(
            List<AvailabilitySlot> slots, Instant now, Integer timezoneOffsetMinutes) {
        List<AvailabilityWindow> windows = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            Instant start = parseLocalDateTime(slot.date(), slot.startTime(), timezoneOffsetMinutes);
            Instant end = parseLocalDateTime(slot.date(), slot.endTime(), timezoneOffsetMinutes);
            if (start == null || end == null || !end.isAfter(start)) {
                continue;
            }
            Instant effectiveStart = start;
            if (effectiveStart.isBefore(now)) {
                effectiveStart = TimezoneUtil.roundUpToQuarterHourInTimezone(now, timezoneOffsetMinutes);
            }
            if (!effectiveStart.isBefore(end)) {
                continue;
            }
            windows.add(new AvailabilityWindow(effectiveStart, end));
        }
        windows.sort(Comparator.comparing(w -> w.start().toEpochMilli()));
        return windows;
    }

    public static List<AvailabilitySlot> parseAvailabilityFromBody(Object body, Integer timezoneOffsetMinutes) {
        if (!(body instanceof List<?> list)) {
            return null;
        }
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof java.util.Map<?, ?> row)) {
                continue;
            }
            String date = String.valueOf(row.get("date")).trim();
            String startTime = String.valueOf(row.get("startTime")).trim();
            String endTime = String.valueOf(row.get("endTime")).trim();
            if (date.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
                continue;
            }
            if (parseLocalDateTime(date, startTime, timezoneOffsetMinutes) == null
                    || parseLocalDateTime(date, endTime, timezoneOffsetMinutes) == null) {
                continue;
            }
            slots.add(new AvailabilitySlot(date, startTime, endTime));
        }
        return slots.isEmpty() ? null : slots;
    }

    public static String formatDateInput(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
