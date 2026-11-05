package com.honeypush.service.ai;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class TimezoneUtil {
    private TimezoneUtil() {}

    public static Integer parseTimezoneOffsetMinutes(Object value) {
        if (value == null) {
            return null;
        }
        double n = value instanceof Number num ? num.doubleValue() : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(n) || n < -840 || n > 840) {
            return null;
        }
        return (int) Math.round(n);
    }

    public static Instant localDateTimeToUtc(
            int year, int month, int day, int hour, int minute, Integer tzOffsetMinutes) {
        if (tzOffsetMinutes == null) {
            return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.systemDefault())
                    .toInstant();
        }
        long utcMs = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        return Instant.ofEpochMilli(utcMs + tzOffsetMinutes * 60L * 1000L);
    }

    public static Instant roundUpToQuarterHourInTimezone(Instant instant, Integer tzOffsetMinutes) {
        if (tzOffsetMinutes == null) {
            ZonedDateTime zdt = instant.atZone(ZoneOffset.systemDefault());
            int minutes = zdt.getMinute();
            int rounded = (int) (Math.ceil(minutes / 15.0) * 15);
            if (rounded == 60) {
                return zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant();
            }
            return zdt.withMinute(rounded).withSecond(0).withNano(0).toInstant();
        }

        Instant shifted = Instant.ofEpochMilli(instant.toEpochMilli() - tzOffsetMinutes * 60L * 1000L);
        var parts = localParts(shifted, tzOffsetMinutes);
        int rounded = (int) (Math.ceil(parts.minute() / 15.0) * 15);
        int hour = parts.hour();
        int minute = rounded;
        int year = parts.year();
        int month = parts.month();
        int day = parts.day();
        if (rounded == 60) {
            hour += 1;
            minute = 0;
            if (hour == 24) {
                hour = 0;
                Instant nextDay = localDateTimeToUtc(year, month, day + 1, 0, 0, tzOffsetMinutes);
                var np = localParts(nextDay, tzOffsetMinutes);
                year = np.year();
                month = np.month();
                day = np.day();
            }
        }
        return localDateTimeToUtc(year, month, day, hour, minute, tzOffsetMinutes);
    }

    public static LocalParts localParts(Instant instant, int timezoneOffsetMinutes) {
        Instant shifted = Instant.ofEpochMilli(instant.toEpochMilli() - timezoneOffsetMinutes * 60L * 1000L);
        ZonedDateTime utc = shifted.atZone(ZoneOffset.UTC);
        return new LocalParts(
                utc.getYear(),
                utc.getMonthValue(),
                utc.getDayOfMonth(),
                utc.getHour(),
                utc.getMinute(),
                utc.getDayOfWeek().getValue() % 7);
    }

    public record LocalParts(int year, int month, int day, int hour, int minute, int weekday) {}
}
