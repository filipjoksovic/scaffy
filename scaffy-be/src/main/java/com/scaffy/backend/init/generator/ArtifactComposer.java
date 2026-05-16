package com.scaffy.backend.init.generator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Reads a cached scaffold artifact (produced by {@code regenerate-artifacts.sh})
 * and emits its entries with {@code __SCAFFY_*__} tokens substituted in both
 * paths and text contents. Binary entries pass through unchanged.
 *
 * <p>The artifact format is: a flat ZIP whose entries are the post-tokenized
 * output of the framework CLI. Tokens are introduced by the regeneration
 * script after the CLI runs against placeholder names.
 */
@Component
public class ArtifactComposer {

	/**
	 * File extensions whose content is considered text and should have tokens
	 * substituted. Anything not on this list is treated as binary.
	 */
	private static final Set<String> TEXT_EXTENSIONS = Set.of(
			"java", "kt", "kts", "groovy",
			"cs", "csproj", "sln", "http",
			"ts", "tsx", "js", "jsx", "mjs", "cjs",
			"json", "yml", "yaml", "xml", "properties",
			"html", "htm", "css", "scss", "sass", "less",
			"md", "txt",
			"gitignore", "gitattributes", "editorconfig",
			"cmd", "sh", "bat", "ps1");

	public List<EmittedFile> compose(String artifactResourcePath, String destinationPrefix,
									 Map<String, String> tokens) throws IOException {
		ClassPathResource resource = new ClassPathResource(artifactResourcePath);
		if (!resource.exists()) {
			throw new IOException("Artifact not found on classpath: " + artifactResourcePath
					+ " (run scaffy-be/scripts/regenerate-artifacts.sh)");
		}
		try (InputStream raw = resource.getInputStream()) {
			return readAndSubstitute(raw, destinationPrefix, tokens);
		}
	}

	/** Test seam: compose from raw bytes instead of a classpath resource. */
	List<EmittedFile> composeFromBytes(byte[] artifact, String destinationPrefix,
									   Map<String, String> tokens) throws IOException {
		return readAndSubstitute(new ByteArrayInputStream(artifact), destinationPrefix, tokens);
	}

	private List<EmittedFile> readAndSubstitute(InputStream source, String destinationPrefix,
												Map<String, String> tokens) throws IOException {
		List<EmittedFile> emitted = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(source)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				String tokenizedPath = substitute(entry.getName(), tokens);
				byte[] rawBytes = zip.readAllBytes();
				byte[] payload = isText(entry.getName())
						? substitute(new String(rawBytes, StandardCharsets.UTF_8), tokens)
								.getBytes(StandardCharsets.UTF_8)
						: rawBytes;
				String destPath = destinationPrefix.isEmpty()
						? tokenizedPath
						: destinationPrefix + "/" + tokenizedPath;
				emitted.add(new EmittedFile(destPath, payload));
			}
		}
		return emitted;
	}

	private static String substitute(String input, Map<String, String> tokens) {
		String result = input;
		for (Map.Entry<String, String> e : tokens.entrySet()) {
			if (result.contains(e.getKey())) {
				result = result.replace(e.getKey(), e.getValue());
			}
		}
		return result;
	}

	private static boolean isText(String entryName) {
		int slash = entryName.lastIndexOf('/');
		String base = slash >= 0 ? entryName.substring(slash + 1) : entryName;
		// dotfiles like .gitignore: extension is the part after the dot
		if (base.startsWith(".") && base.indexOf('.', 1) < 0) {
			return TEXT_EXTENSIONS.contains(base.substring(1));
		}
		int dot = base.lastIndexOf('.');
		if (dot < 0) {
			// no extension (mvnw, etc.) — treat as text so shebangs render
			return true;
		}
		return TEXT_EXTENSIONS.contains(base.substring(dot + 1).toLowerCase());
	}
}
