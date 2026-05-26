package com.scaffy.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.app")
public record AppProperties(String frontendUrl) {
}
