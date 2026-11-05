package com.honeypush.service.web;

import tools.jackson.databind.JsonNode;
import com.honeypush.service.ai.AvailabilityHelper;
import com.honeypush.service.ai.FallbackSchedule;
import com.honeypush.service.ai.LlmClient;
import com.honeypush.service.ai.SchedulePriority;
import com.honeypush.service.ai.ScheduleTimes;
import com.honeypush.service.ai.TimezoneUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ai-schedule")
public class AiScheduleController {

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "import-urgent", "import-noturgent", "notimport-urgent", "notimport-noturgent");

    private static final String SYSTEM_PROMPT = """
            你是一个效率规划专家，擅长根据任务截止时间、预计用时、四象限优先级和用户可用时间段，为用户已有的任务列表做排期。
            返回 JSON：{"schedule":[{"id":1,"category":"import-urgent","durationMinutes":25,"order":1}]}
            只返回 JSON，不要其他说明文字。""";

    private final LlmClient llmClient;

    public AiScheduleController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping
    ResponseEntity<?> schedule(@RequestBody Map<String, Object> body) {
        CurrentUser.require();

        Integer timezoneOffsetMinutes = TimezoneUtil.parseTimezoneOffsetMinutes(body.get("timezoneOffsetMinutes"));
        if (timezoneOffsetMinutes == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "缺少客户端时区信息，请刷新页面后重试"));
        }

        List<AvailabilityHelper.AvailabilitySlot> availability =
                AvailabilityHelper.parseAvailabilityFromBody(body.get("availability"), timezoneOffsetMinutes);
        if (availability == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "请至少添加一个今天或未来几天的可用时间段"));
        }

        List<FallbackSchedule.IncomingTask> sourceTasks = parseTasks(body.get("tasks"));
        if (sourceTasks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请先添加至少一个任务"));
        }

        List<FallbackSchedule.IncomingTask> schedulable = sourceTasks.stream()
                .filter(t -> !isTaskPastDeadline(t.deadline()))
                .toList();
        if (schedulable.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "没有可排期的任务（待排任务均已过截止时间）"));
        }
        if (schedulable.stream().anyMatch(t -> t.deadline() == null || t.deadline().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "请为每个待排期任务填写截止时间（deadline）"));
        }

        if (!llmClient.isConfigured()) {
            var result = FallbackSchedule.buildFallbackScheduleFromTasks(
                    schedulable, availability, timezoneOffsetMinutes);
            return buildResponse(schedulable, result, "fallback");
        }

        try {
            String userPayload = new tools.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(Map.of("availability", body.get("availability"), "tasks", schedulable));
            String raw = llmClient.chatCompletion(List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPayload)));
            JsonNode parsed = llmClient.extractJsonObject(raw);
            var result = normalizeSchedule(parsed, schedulable, availability, timezoneOffsetMinutes);
            return buildResponse(schedulable, result, "ai");
        } catch (Exception e) {
            var result = FallbackSchedule.buildFallbackScheduleFromTasks(
                    schedulable, availability, timezoneOffsetMinutes);
            return buildResponse(schedulable, result, "fallback");
        }
    }

    private static List<FallbackSchedule.IncomingTask> parseTasks(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<FallbackSchedule.IncomingTask> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object idObj = row.get("id");
            if (!(idObj instanceof Number idNum)) {
                continue;
            }
            int id = idNum.intValue();
            String text = String.valueOf(row.get("text")).trim();
            if (text.isEmpty()) {
                continue;
            }
            Integer duration = row.get("durationMinutes") instanceof Number n ? n.intValue() : 25;
            String category = row.get("category") instanceof String s ? s : null;
            String deadline = row.get("deadline") == null
                    ? null
                    : row.get("deadline") instanceof String s ? s : String.valueOf(row.get("deadline"));
            out.add(new FallbackSchedule.IncomingTask(id, text, duration, category, deadline));
        }
        return out;
    }

    private static boolean isTaskPastDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(deadline).isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private static ScheduleTimes.AssignScheduleResult normalizeSchedule(
            JsonNode raw,
            List<FallbackSchedule.IncomingTask> sourceTasks,
            List<AvailabilityHelper.AvailabilitySlot> availability,
            int timezoneOffsetMinutes) {
        Map<Integer, Instant> deadlinesById = new HashMap<>();
        for (FallbackSchedule.IncomingTask task : sourceTasks) {
            Instant d = null;
            if (task.deadline() != null && !task.deadline().isBlank()) {
                try {
                    d = Instant.parse(task.deadline());
                } catch (Exception ignored) {
                }
            }
            deadlinesById.put(task.id(), d);
        }

        if (raw == null || !raw.has("schedule") || !raw.get("schedule").isArray()) {
            return FallbackSchedule.buildFallbackScheduleFromTasks(sourceTasks, availability, timezoneOffsetMinutes);
        }

        Map<Integer, SchedulePriority.SchedulableTaskRef> byId = new HashMap<>();
        for (JsonNode item : raw.get("schedule")) {
            if (!item.isObject()) {
                continue;
            }
            int id = item.path("id").asInt(-1);
            if (id < 0) {
                continue;
            }
            String category = item.path("category").asText("import-noturgent");
            if (!VALID_CATEGORIES.contains(category)) {
                category = "import-noturgent";
            }
            int duration = item.path("durationMinutes").asInt(25);
            duration = Math.min(180, Math.max(15, duration));
            int order = Math.max(1, item.path("order").asInt(999));
            byId.put(id, new SchedulePriority.SchedulableTaskRef(id, category, order, duration));
        }

        List<SchedulePriority.SchedulableTaskRef> merged = new ArrayList<>();
        for (int i = 0; i < sourceTasks.size(); i++) {
            FallbackSchedule.IncomingTask task = sourceTasks.get(i);
            SchedulePriority.SchedulableTaskRef matched = byId.get(task.id());
            if (matched != null) {
                merged.add(matched);
            } else {
                merged.add(new SchedulePriority.SchedulableTaskRef(
                        task.id(),
                        task.category() != null ? task.category() : "import-noturgent",
                        i + 1,
                        task.durationMinutes() != null ? task.durationMinutes() : 25));
            }
        }

        List<SchedulePriority.SchedulableTaskRef> ranked =
                SchedulePriority.rankTasksForSchedule(merged, deadlinesById);
        return ScheduleTimes.assignScheduleTimes(
                ranked, deadlinesById, availability, timezoneOffsetMinutes, Instant.now());
    }

    private static ResponseEntity<Map<String, Object>> buildResponse(
            List<FallbackSchedule.IncomingTask> schedulable,
            ScheduleTimes.AssignScheduleResult result,
            String source) {
        Set<Integer> unscheduled = new HashSet<>(result.unscheduledIds());
        List<Map<String, Object>> unscheduledTasks = schedulable.stream()
                .filter(t -> unscheduled.contains(t.id()))
                .map(t -> Map.<String, Object>of("id", t.id(), "text", t.text()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "schedule", result.schedule(),
                "unscheduledIds", result.unscheduledIds(),
                "unscheduledTasks", unscheduledTasks,
                "source", source));
    }
}
