package com.honeypush.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.honeypush.service.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class LlmClient {
    private final AppProperties appProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LlmClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public boolean isConfigured() {
        return appProperties.llm().isConfigured();
    }

    public String chatCompletion(List<Map<String, String>> messages) {
        if (!isConfigured()) {
            throw new IllegalStateException("LLM not configured");
        }
        var llm = appProperties.llm();
        Map<String, Object> body = Map.of(
                "model", llm.effectiveModel(),
                "messages", messages,
                "temperature", 0.2);
        String response = restClient
                .post()
                .uri(llm.normalizedBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + llm.apiKey())
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText() : "";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM response", e);
        }
    }

    public JsonNode extractJsonObject(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(text.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return objectMapper.createObjectNode();
    }
}
