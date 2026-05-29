package com.scaffy.backend.recommend;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfig {
}
