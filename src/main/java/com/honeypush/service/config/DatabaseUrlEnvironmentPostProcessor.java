package com.honeypush.service.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = System.getenv("DATABASE_URL");
        if (url == null || url.isBlank() || url.startsWith("jdbc:")) {
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("spring.datasource.url", normalizeDatabaseUrl(url.trim()));
        environment.getPropertySources().addFirst(new MapPropertySource("honeypushDatabaseUrl", map));
    }

    static String normalizeDatabaseUrl(String url) {
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.replaceFirst("^postgres(ql)?://", "");
        }
        return url;
    }
}
