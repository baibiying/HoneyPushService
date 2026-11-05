package com.honeypush.service.ai;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FallbackSchedule {
    private FallbackSchedule() {}

    public record IncomingTask(
            int id, String text, Integer durationMinutes, String category, String deadline) {}

    public static ScheduleTimes.AssignScheduleResult buildFallbackScheduleFromTasks(
            List<IncomingTask> tasks,
            List<AvailabilityHelper.AvailabilitySlot> availability,
            Integer timezoneOffsetMinutes) {
        Map<Integer, Instant> deadlinesById = new HashMap<>();
        List<SchedulePriority.SchedulableTaskRef> base = new java.util.ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            IncomingTask task = tasks.get(index);
            Instant deadline = null;
            if (task.deadline() != null && !task.deadline().isBlank()) {
                try {
                    deadline = Instant.parse(task.deadline());
                } catch (Exception ignored) {
                }
            }
            deadlinesById.put(task.id(), deadline);
            String category = SchedulePriority.normalizeQuadrantCategory(
                    task.category() != null ? task.category() : guessCategory(task.text(), index));
            int duration = task.durationMinutes() != null ? task.durationMinutes() : guessDurationMinutes(task.text());
            base.add(new SchedulePriority.SchedulableTaskRef(task.id(), category, index + 1, duration));
        }
        List<SchedulePriority.SchedulableTaskRef> ranked =
                SchedulePriority.rankTasksForSchedule(base, deadlinesById);
        return ScheduleTimes.assignScheduleTimes(
                ranked, deadlinesById, availability, timezoneOffsetMinutes, Instant.now());
    }

    private static int guessDurationMinutes(String text) {
        Matcher hourMatch = Pattern.compile("(\\d+)\\s*小时").matcher(text);
        if (hourMatch.find()) {
            return Math.min(120, Math.max(25, Integer.parseInt(hourMatch.group(1)) * 60));
        }
        Matcher minuteMatch = Pattern.compile("(\\d+)\\s*分钟").matcher(text);
        if (minuteMatch.find()) {
            return Math.min(120, Math.max(15, Integer.parseInt(minuteMatch.group(1))));
        }
        return 25;
    }

    private static String guessCategory(String text, int index) {
        String lower = text.toLowerCase();
        if (lower.matches(".*(紧急|ddl|截止|明天|今天|今晚|马上).*")) {
            return "import-urgent";
        }
        if (lower.matches(".*(学习|论文|复习|项目|核心|重要).*")) {
            return "import-noturgent";
        }
        if (lower.matches(".*(回复|邮件|缴费|取件|快递|联系).*")) {
            return "notimport-urgent";
        }
        if (lower.matches(".*(娱乐|刷|买|逛|看剧).*")) {
            return "notimport-noturgent";
        }
        String[] cycle = {
            "import-urgent", "import-noturgent", "notimport-urgent", "notimport-noturgent"
        };
        return cycle[index % cycle.length];
    }
}
