package com.scaffy.backend.init;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Iteration 1: only Angular + Spring Boot is supported. Extending the catalog
 * means adding entries here; nothing else in the request flow needs to change.
 */
@Component
public class StackCatalog {

	public static final String FRONTEND_ANGULAR = "angular";
	public static final String FRONTEND_VUE = "vue";
	public static final String FRONTEND_REACT = "react";
	public static final String BACKEND_SPRING_BOOT = "spring-boot";
	public static final String BACKEND_DOTNET = "dotnet";
	public static final String BACKEND_NESTJS = "nestjs";
	public static final String PIPELINE_GITHUB_ACTIONS = "github-actions";
	public static final String PIPELINE_GITLAB_CI = "gitlab-ci";

	private static final Set<String> FRONTENDS = Set.of(FRONTEND_ANGULAR, FRONTEND_VUE, FRONTEND_REACT);
	private static final Set<String> BACKENDS = Set.of(BACKEND_SPRING_BOOT, BACKEND_DOTNET, BACKEND_NESTJS);
	private static final Set<String> PIPELINES = Set.of(PIPELINE_GITHUB_ACTIONS, PIPELINE_GITLAB_CI);

	public Set<String> frontends() { return FRONTENDS; }
	public Set<String> backends() { return BACKENDS; }
	public Set<String> pipelines() { return PIPELINES; }

	public boolean supportsFrontend(String value) { return FRONTENDS.contains(value); }
	public boolean supportsBackend(String value) { return BACKENDS.contains(value); }
	public boolean supportsPipeline(String value) { return PIPELINES.contains(value); }
}
