package com.honeypush.service.web;

import com.honeypush.service.entity.ScheduledFocusSegmentEmbeddable;
import com.honeypush.service.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "import-urgent", "import-noturgent", "notimport-urgent", "notimport-noturgent");

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    ResponseEntity<?> list() {
        String userId = CurrentUser.require().id();
        try {
            return ResponseEntity.ok(taskService.listTasks(userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "任务列表加载失败，请确认已执行数据库迁移（npm run db:migrate）"));
        }
    }

    @PostMapping
    ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require().id();
        if (body.get("text") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        Instant deadline = parseDeadline(body.get("deadline"));
        if (deadline == null && body.get("deadline") != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "deadline is invalid"));
        }
        if (deadline == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "deadline is required"));
        }
        String category = body.get("category") instanceof String s ? s : "";
        if (!VALID_CATEGORIES.contains(category)) {
            return ResponseEntity.badRequest().body(Map.of("error", "category is required"));
        }
        int duration = body.get("durationMinutes") instanceof Number n ? n.intValue() : 25;
        return ResponseEntity.ok(taskService.createTask(userId, String.valueOf(body.get("text")), category, duration, deadline));
    }

    @PatchMapping("/{id}")
    ResponseEntity<?> patch(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require().id();
        int taskId;
        try {
            taskId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID"));
        }

        TaskService.TaskUpdate.Builder builder = TaskService.TaskUpdate.builder();

        if (body.containsKey("text")) {
            if (!(body.get("text") instanceof String text) || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
            }
            builder.text(text.trim());
        }
        if (body.containsKey("checked") && body.get("checked") instanceof Boolean checked) {
            builder.checked(checked);
        }
        if (body.containsKey("category") && body.get("category") instanceof String category) {
            builder.category(category);
        }
        if (body.containsKey("durationMinutes") && body.get("durationMinutes") instanceof Number n) {
            int dm = Math.min(180, Math.max(15, (int) Math.round(n.doubleValue())));
            builder.durationMinutes(dm);
        }

        if (body.containsKey("deadline")) {
            Instant parsed = parseOptionalTimestamp(body.get("deadline"));
            if (parsed == null && body.get("deadline") != null && !"".equals(String.valueOf(body.get("deadline")))) {
                return ResponseEntity.badRequest().body(Map.of("error", "deadline is invalid"));
            }
            builder.deadline(parsed);
        }
        if (body.containsKey("scheduledStartAt")) {
            Instant parsed = parseOptionalTimestamp(body.get("scheduledStartAt"));
            if (parsed == null && body.get("scheduledStartAt") != null && !"".equals(String.valueOf(body.get("scheduledStartAt")))) {
                return ResponseEntity.badRequest().body(Map.of("error", "scheduledStartAt is invalid"));
            }
            builder.scheduledStartAt(parsed);
        }
        if (body.containsKey("scheduledEndAt")) {
            Instant parsed = parseOptionalTimestamp(body.get("scheduledEndAt"));
            if (parsed == null && body.get("scheduledEndAt") != null && !"".equals(String.valueOf(body.get("scheduledEndAt")))) {
                return ResponseEntity.badRequest().body(Map.of("error", "scheduledEndAt is invalid"));
            }
            builder.scheduledEndAt(parsed);
        }
        if (body.containsKey("scheduledFocusSegments")) {
            Object raw = body.get("scheduledFocusSegments");
            if (raw == null) {
                builder.scheduledFocusSegments(null);
            } else if (raw instanceof List<?> list) {
                List<ScheduledFocusSegmentEmbeddable> segments = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> row)) {
                        continue;
                    }
                    String startAt = String.valueOf(row.get("startAt"));
                    String endAt = String.valueOf(row.get("endAt"));
                    if (startAt.isBlank() || endAt.isBlank()) {
                        continue;
                    }
                    try {
                        Instant.parse(startAt);
                        Instant.parse(endAt);
                    } catch (Exception e) {
                        continue;
                    }
                    segments.add(new ScheduledFocusSegmentEmbeddable(startAt, endAt));
                }
                builder.scheduledFocusSegments(segments.isEmpty() ? null : segments);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "scheduledFocusSegments must be an array or null"));
            }
        }

        return taskService
                .updateTask(userId, taskId, builder.build())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found")));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<?> delete(@PathVariable String id) {
        String userId = CurrentUser.require().id();
        int taskId;
        try {
            taskId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid ID"));
        }
        boolean ok = taskService.deleteTask(userId, taskId);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    private static Instant parseDeadline(Object value) {
        if (value == null || "".equals(String.valueOf(value))) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            try {
                return Instant.ofEpochMilli(java.util.Date.from(
                                java.time.ZonedDateTime.parse(String.valueOf(value)).toInstant())
                        .getTime());
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static Instant parseOptionalTimestamp(Object value) {
        if (value == null || "".equals(String.valueOf(value))) {
            return null;
        }
        return parseDeadline(value);
    }
}
