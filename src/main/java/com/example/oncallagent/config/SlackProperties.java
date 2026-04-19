package com.example.oncallagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.slack")
public record SlackProperties(String botToken) {}
