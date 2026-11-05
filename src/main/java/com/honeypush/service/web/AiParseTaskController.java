package com.honeypush.service.web;

import tools.jackson.databind.ObjectMapper;
import com.honeypush.service.ai.LlmClient;
import com.honeypush.service.ai.ParseTaskHelper;
import com.honeypush.service.ai.TimezoneUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-parse-task")
public class AiParseTaskController {

    private static final String SYSTEM_PROMPT = """
            你是任务解析助手。用户会用自然语言（中文为主）描述待办，可能一次提到多个任务。
            请提取结构化信息，返回 JSON：
            {
              "tasks": [
                {
                  "text": "仅任务事项名称（名词短语，如「英语语法作业」「交互设计稿」），不要带写完/完成/提交等动词，也不含时间或时长",
                  "category": "四象限之一：import-urgent | import-noturgent | notimport-urgent | notimport-noturgent",
                  "durationMinutes": 15-180 的整数，表示预计专注用时（默认 25）,
                  "deadline": "ISO 8601 格式的截止时间（UTC），结合用户提到的今天/明天/周五/具体日期与 referenceLocal 理解"
                }
              ]
            }
            只返回 JSON，不要解释""";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AiParseTaskController(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    ResponseEntity<?> parse(@RequestBody Map<String, Object> body) {
        CurrentUser.require();
        String naturalLanguage = String.valueOf(body.getOrDefault("naturalLanguage", "")).trim();
        if (naturalLanguage.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入任务描述"));
        }

        int tz = resolveTimezoneOffset(body);
        String referenceLocal = body.get("referenceLocal") instanceof String s && !s.isBlank()
                ? s.trim()
                : Instant.now().toString();

        if (!llmClient.isConfigured()) {
            return buildResponse(ParseTaskHelper.fallbackParseTasksFromNaturalLanguage(naturalLanguage, tz), "fallback");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("naturalLanguage", naturalLanguage);
        payload.put("referenceLocal", referenceLocal);
        payload.put("timezoneOffsetMinutes", tz);
        payload.put(
                "categoryGuide",
                Map.of(
                        "import-urgent", "重要且紧急",
                        "import-noturgent", "重要不紧急",
                        "notimport-urgent", "紧急不重要",
                        "notimport-noturgent", "不重要不紧急"));

        try {
            String userPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            String raw = llmClient.chatCompletion(List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", userPayload)));
            var parsed = llmClient.extractJsonObject(raw);
            var tasks = ParseTaskHelper.applyDurationFromNaturalLanguage(
                    ParseTaskHelper.normalizeParsedTasks(parsed.get("tasks"), tz), naturalLanguage);
            if (tasks.isEmpty()) {
                return buildResponse(
                        ParseTaskHelper.fallbackParseTasksFromNaturalLanguage(naturalLanguage, tz), "fallback");
            }
            return buildResponse(tasks, "ai");
        } catch (Exception e) {
            return buildResponse(
                    ParseTaskHelper.fallbackParseTasksFromNaturalLanguage(naturalLanguage, tz), "fallback");
        }
    }

    private static int resolveTimezoneOffset(Map<String, Object> body) {
        Integer parsed = TimezoneUtil.parseTimezoneOffsetMinutes(body.get("timezoneOffsetMinutes"));
        if (parsed != null) {
            return parsed;
        }
        return -java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 1000);
    }

    private static ResponseEntity<Map<String, Object>> buildResponse(
            List<ParseTaskHelper.ParsedTaskDraft> tasks, String source) {
        return ResponseEntity.ok(Map.of("tasks", tasks, "source", source));
    }
}
