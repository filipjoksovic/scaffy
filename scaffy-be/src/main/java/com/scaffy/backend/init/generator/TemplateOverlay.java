package com.scaffy.backend.init.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Emits Scaffy's own glue files — CI configs, root README, root .gitignore —
 * from the {@code templates/} resource tree. These are content we author and
 * own, so they use the simple {@code {{var}}} substitution scheme rather than
 * the {@code __SCAFFY_*__} tokens used by cached framework artifacts.
 */
@Component
public class TemplateOverlay {

	private final TemplateRenderer renderer;
	private final GeneratorCacheManager cacheManager;

	public TemplateOverlay(TemplateRenderer renderer, GeneratorCacheManager cacheManager) {
		this.renderer = renderer;
		this.cacheManager = cacheManager;
	}

	public List<EmittedFile> emit(List<TemplateFile> templates, Map<String, String> variables) throws IOException {
		List<EmittedFile> emitted = new ArrayList<>();
		for (TemplateFile template : templates) {
			byte[] raw = readResource(template.resourcePath());
			byte[] payload = template.render()
					? renderer.render(new String(raw, StandardCharsets.UTF_8), variables)
							.getBytes(StandardCharsets.UTF_8)
					: raw;
			String destPath = renderer.render(template.destinationPath(), variables);
			emitted.add(new EmittedFile(destPath, payload));
		}
		return emitted;
	}

	private byte[] readResource(String resourcePath) throws IOException {
		byte[] cached = cacheManager.getTemplate(resourcePath);
		if (cached != null) {
			return cached;
		}
		ClassPathResource resource = new ClassPathResource(resourcePath);
		if (!resource.exists()) {
			throw new IOException("Template resource not found: " + resourcePath);
		}
		byte[] bytes;
		try (InputStream in = resource.getInputStream()) {
			bytes = in.readAllBytes();
		}
		cacheManager.putTemplate(resourcePath, bytes);
		return bytes;
	}
}
