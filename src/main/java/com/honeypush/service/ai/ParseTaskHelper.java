package com.honeypush.service.ai;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParseTaskHelper {
    private static final Set<String> CATEGORIES = Set.of(
            "import-urgent", "import-noturgent", "notimport-urgent", "notimport-noturgent");

    private ParseTaskHelper() {}

    public record ParsedTaskDraft(String text, String category, int durationMinutes, String deadline) {}

    public static List<ParsedTaskDraft> fallbackParseTasksFromNaturalLanguage(
            String input, int timezoneOffsetMinutes) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] segments = trimmed.split("[；;。\\n]+");
        List<String> parts = new ArrayList<>();
        for (String segment : segments) {
            String cleaned = cleanTaskText(segment);
            if (cleaned.length() >= 2) {
                parts.add(cleaned);
            }
        }
        if (parts.isEmpty()) {
            parts.add(trimmed);
        }
        List<ParsedTaskDraft> out = new ArrayList<>();
        for (String segment : parts) {
            Instant deadline = deadlineFromPhrase(segment, timezoneOffsetMinutes);
            if (deadline == null) {
                deadline = deadlineFromPhrase(trimmed, timezoneOffsetMinutes);
            }
            if (deadline == null) {
                deadline = defaultDeadline(timezoneOffsetMinutes);
            }
            out.add(new ParsedTaskDraft(
                    extractTaskTitle(segment).isBlank() ? cleanTaskText(segment) : extractTaskTitle(segment),
                    guessCategory(segment + " " + trimmed),
                    resolveDurationMinutes(segment, trimmed),
                    deadline.toString()));
        }
        return out;
    }

    public static List<ParsedTaskDraft> normalizeParsedTasks(JsonNode rawList, int timezoneOffsetMinutes) {
        if (rawList == null || !rawList.isArray()) {
            return List.of();
        }
        List<ParsedTaskDraft> out = new ArrayList<>();
        for (JsonNode item : rawList) {
            ParsedTaskDraft normalized = normalizeParsedTask(item, timezoneOffsetMinutes);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return out;
    }

    public static ParsedTaskDraft normalizeParsedTask(JsonNode row, int timezoneOffsetMinutes) {
        if (row == null || !row.isObject()) {
            return null;
        }
        String rawText = row.path("text").asText("").trim();
        if (rawText.isEmpty()) {
            return null;
        }
        String text = extractTaskTitle(rawText);
        if (text.isBlank()) {
            text = rawText;
        }
        String categoryRaw = row.path("category").asText("import-noturgent");
        String category = CATEGORIES.contains(categoryRaw) ? categoryRaw : "import-noturgent";
        int duration = clampDuration(row.path("durationMinutes").asInt(25));
        Instant deadline = parseDeadlineToDate(row.get("deadline"), timezoneOffsetMinutes);
        if (deadline == null) {
            return null;
        }
        return new ParsedTaskDraft(text, category, duration, deadline.toString());
    }

    public static List<ParsedTaskDraft> applyDurationFromNaturalLanguage(
            List<ParsedTaskDraft> tasks, String naturalLanguage) {
        int fromInput = extractDurationMinutes(naturalLanguage);
        if (fromInput == 25) {
            return tasks;
        }
        if (tasks.size() == 1) {
            ParsedTaskDraft t = tasks.get(0);
            return List.of(new ParsedTaskDraft(t.text(), t.category(), fromInput, t.deadline()));
        }
        List<ParsedTaskDraft> out = new ArrayList<>();
        for (ParsedTaskDraft t : tasks) {
            out.add(t.durationMinutes() == 25
                    ? new ParsedTaskDraft(t.text(), t.category(), fromInput, t.deadline())
                    : t);
        }
        return out;
    }

    public static int extractDurationMinutes(String text) {
        Matcher hourMatch = Pattern.compile(
                        "(?:大概|约|大约|预计|需要|花费|用时|要)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:个)?\\s*(?:小时|钟头|h|hr)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (hourMatch.find()) {
            return clampDuration((int) Math.round(Double.parseDouble(hourMatch.group(1)) * 60));
        }
        Matcher minMatch = Pattern.compile(
                        "(?:大概|约|大约|预计|需要|花费|用时|要)?\\s*(\\d+)\\s*(?:分钟|分(?!析|为|类|享|配|钟)|min)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (minMatch.find()) {
            return clampDuration(Integer.parseInt(minMatch.group(1)));
        }
        return 25;
    }

    private static int resolveDurationMinutes(String segment, String fullInput) {
        int fromSegment = extractDurationMinutes(segment);
        return fromSegment != 25 ? fromSegment : extractDurationMinutes(fullInput);
    }

    private static int clampDuration(int value) {
        return Math.min(180, Math.max(15, value));
    }

    private static Instant parseDeadlineToDate(JsonNode value, int timezoneOffsetMinutes) {
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            return defaultDeadline(timezoneOffsetMinutes);
        }
        try {
            return Instant.parse(value.asText());
        } catch (Exception e) {
            return defaultDeadline(timezoneOffsetMinutes);
        }
    }

    private static Instant defaultDeadline(int timezoneOffsetMinutes) {
        TimezoneUtil.LocalParts parts = TimezoneUtil.localParts(Instant.now(), timezoneOffsetMinutes);
        return TimezoneUtil.localDateTimeToUtc(parts.year(), parts.month(), parts.day() + 3, 18, 0, timezoneOffsetMinutes);
    }

    private static Instant deadlineFromPhrase(String phrase, int timezoneOffsetMinutes) {
        String p = phrase.trim();
        TimezoneUtil.LocalParts parts = TimezoneUtil.localParts(Instant.now(), timezoneOffsetMinutes);
        int dayOffset = 0;
        int hour = 18;
        int minute = 0;

        if (p.matches(".*(今晚|今天晚上).*")) {
            dayOffset = 0;
            hour = 22;
        } else if (p.matches(".*(今天|今日).*")) {
            dayOffset = 0;
            hour = 18;
        } else if (p.matches(".*(明天|明日).*")) {
            dayOffset = 1;
        } else if (p.contains("后天") && !p.contains("大后天")) {
            dayOffset = 2;
        } else if (p.contains("大后天")) {
            dayOffset = 3;
        } else if (p.matches(".*(下周五|下个周五).*")) {
            dayOffset = (7 - parts.weekday() + 5) % 7;
            if (dayOffset == 0) {
                dayOffset = 7;
            }
        } else if (p.contains("下周")) {
            dayOffset = (7 - parts.weekday()) % 7;
            if (dayOffset == 0) {
                dayOffset = 7;
            }
        } else {
            Matcher weekMatch = Pattern.compile("(?:周|星期)([一二三四五六日天])").matcher(p);
            if (weekMatch.find()) {
                int target = weekdayFromChar(weekMatch.group(1));
                dayOffset = (target - parts.weekday() + 7) % 7;
                if (dayOffset == 0 && target <= parts.weekday()) {
                    dayOffset = 7;
                }
            }
        }

        Matcher dateMatch = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?").matcher(p);
        if (dateMatch.find()) {
            int month = Integer.parseInt(dateMatch.group(1));
            int day = Integer.parseInt(dateMatch.group(2));
            int year = parts.year();
            if (month < parts.month() || (month == parts.month() && day < parts.day())) {
                year += 1;
            }
            return TimezoneUtil.localDateTimeToUtc(year, month, day, hour, minute, timezoneOffsetMinutes);
        }

        if (dayOffset > 0 || p.matches(".*(今天|今晚|明日|明天|周|星期).*")) {
            Instant base = TimezoneUtil.localDateTimeToUtc(parts.year(), parts.month(), parts.day(), 0, 0, timezoneOffsetMinutes);
            Instant target = Instant.ofEpochMilli(base.toEpochMilli() + dayOffset * 24L * 60 * 60 * 1000);
            TimezoneUtil.LocalParts tp = TimezoneUtil.localParts(target, timezoneOffsetMinutes);
            return TimezoneUtil.localDateTimeToUtc(tp.year(), tp.month(), tp.day(), hour, minute, timezoneOffsetMinutes);
        }
        return null;
    }

    private static int weekdayFromChar(String ch) {
        return switch (ch) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            default -> 0;
        };
    }

    private static String guessCategory(String text) {
        String t = text.toLowerCase();
        if (t.matches(".*(紧急|火急|ddl|截止|今晚必须|马上).*")) {
            return "import-urgent";
        }
        if (t.matches(".*(重要|论文|考试|面试|交付).*")) {
            return "import-noturgent";
        }
        if (t.matches(".*(顺便|买|取快递|回消息).*")) {
            return "notimport-noturgent";
        }
        if (t.matches(".*(催|临时|尽快).*")) {
            return "notimport-urgent";
        }
        return "import-noturgent";
    }

    private static String cleanTaskText(String segment) {
        return segment.replaceAll("[，,；;。！？!?]+$", "")
                .replaceAll("^(还要|另外|以及|并且|然后|再)+", "")
                .trim();
    }

    private static String extractTaskTitle(String raw) {
        String t = cleanTaskText(raw);
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("(?:大概|约|大约|预计|需要|花费|用时)?\\s*\\d+(?:\\.\\d+)?\\s*(?:个小时|小时|h|hr)", " ");
        t = t.replaceAll("(?:大概|约|大约|预计|需要|花费|用时)?\\s*\\d+\\s*(?:分钟|分(?!析|为|类|享|配|钟)|min)", " ");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^(?:我|我们|还得|还要|先|再|然后|另外|以及|并且)\\s*", "");
        t = t.replaceAll("^(?:要|得|需要|必须|打算|准备|想|想要)\\s*", "");
        return t.length() >= 2 ? t : cleanTaskText(raw);
    }
}
