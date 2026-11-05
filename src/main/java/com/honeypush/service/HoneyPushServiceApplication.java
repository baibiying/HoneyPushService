package com.honeypush.service;

import com.honeypush.service.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class HoneyPushServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HoneyPushServiceApplication.class, args);
    }
}
