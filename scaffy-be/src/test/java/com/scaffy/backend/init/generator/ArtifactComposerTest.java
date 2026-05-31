package com.scaffy.backend.init.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class ArtifactComposerTest {

	private final ArtifactComposer composer = new ArtifactComposer(new GeneratorCacheManager());

	private static final Map<String, String> TOKENS = Map.of(
			"__SCAFFY_PROJECT_NAME__", "demo-app",
			"__SCAFFY_PROJECT_PASCAL__", "DemoApp",
			"__SCAFFY_PACKAGE__", "com.example.demoapp",
			"__SCAFFY_PACKAGE_DIR__", "com/example/demoapp");

	@Test
	void substitutesTokensInPathsAndTextContent() throws Exception {
		byte[] artifact = zip(Map.of(
				"src/main/java/__SCAFFY_PACKAGE_DIR__/__SCAFFY_PROJECT_PASCAL__Application.java",
				"package __SCAFFY_PACKAGE__;\npublic class __SCAFFY_PROJECT_PASCAL__Application {}\n",
				"package.json",
				"{\"name\": \"__SCAFFY_PROJECT_NAME__\"}"));

		List<EmittedFile> emitted = composer.composeFromBytes(artifact, "backend", TOKENS);

		assertThat(emitted).extracting(EmittedFile::destinationPath).containsExactlyInAnyOrder(
				"backend/src/main/java/com/example/demoapp/DemoAppApplication.java",
				"backend/package.json");

		EmittedFile java = emitted.stream()
				.filter(f -> f.destinationPath().endsWith(".java")).findFirst().orElseThrow();
		assertThat(new String(java.content(), StandardCharsets.UTF_8))
				.contains("package com.example.demoapp;")
				.contains("public class DemoAppApplication")
				.doesNotContain("__SCAFFY_");
	}

	@Test
	void leavesBinaryContentUnchanged() throws Exception {
		byte[] favicon = new byte[] { 0x00, 0x00, 0x01, 0x00, (byte) 0xFF, (byte) 0xFE };
		byte[] artifact = zipBinary("public/favicon.ico", favicon);

		List<EmittedFile> emitted = composer.composeFromBytes(artifact, "frontend", TOKENS);

		assertThat(emitted).hasSize(1);
		assertThat(emitted.get(0).destinationPath()).isEqualTo("frontend/public/favicon.ico");
		assertThat(emitted.get(0).content()).isEqualTo(favicon);
	}

	@Test
	void emptyDestinationPrefixWritesAtRoot() throws Exception {
		byte[] artifact = zip(Map.of("README.md", "# __SCAFFY_PROJECT_NAME__"));

		List<EmittedFile> emitted = composer.composeFromBytes(artifact, "", TOKENS);

		assertThat(emitted).hasSize(1);
		assertThat(emitted.get(0).destinationPath()).isEqualTo("README.md");
		assertThat(new String(emitted.get(0).content(), StandardCharsets.UTF_8))
				.isEqualTo("# demo-app");
	}

	@Test
	void unknownExtensionFallsBackToTextSubstitution() throws Exception {
		// extensionless executable scripts like mvnw should still be tokenized,
		// since they may legitimately contain identifiers.
		byte[] artifact = zip(Map.of("mvnw", "#!/bin/sh\necho __SCAFFY_PROJECT_NAME__\n"));

		List<EmittedFile> emitted = composer.composeFromBytes(artifact, "backend", TOKENS);

		assertThat(new String(emitted.get(0).content(), StandardCharsets.UTF_8))
				.contains("echo demo-app");
	}

	private static byte[] zip(Map<String, String> entries) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (Map.Entry<String, String> e : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(e.getKey()));
				zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	private static byte[] zipBinary(String name, byte[] content) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry(name));
			zip.write(content);
			zip.closeEntry();
		}
		return out.toByteArray();
	}
}
