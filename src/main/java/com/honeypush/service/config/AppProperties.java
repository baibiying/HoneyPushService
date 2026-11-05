package com.honeypush.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cookie cookie, Llm llm) {
    public record Cookie(boolean secure) {}

    public record Llm(String apiKey, String baseUrl, String model) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String normalizedBaseUrl() {
            if (baseUrl == null || baseUrl.isBlank()) {
                return "https://api.openai.com/v1";
            }
            return baseUrl.replaceAll("/$", "");
        }

        public String effectiveModel() {
            return model == null || model.isBlank() ? "gpt-4o-mini" : model;
        }
    }
}
