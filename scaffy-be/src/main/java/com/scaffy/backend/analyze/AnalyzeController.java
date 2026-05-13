package com.scaffy.backend.analyze;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

	private final PipelineAnalyzer pipelineAnalyzer;

	public AnalyzeController(PipelineAnalyzer pipelineAnalyzer) {
		this.pipelineAnalyzer = pipelineAnalyzer;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public AnalysisResponse analyze(@RequestPart("file") MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw PipelineAnalysisException.invalidUpload("Uploaded pipeline file must not be empty.");
		}

		String filename = file.getOriginalFilename();
		if (!hasYamlExtension(filename)) {
			throw new PipelineAnalysisException("Unsupported file type", "Only .yml and .yaml pipeline files are supported.");
		}

		String content = readContent(file);
		if (content.isBlank()) {
			throw PipelineAnalysisException.invalidUpload("Uploaded pipeline file must not be empty.");
		}

		return pipelineAnalyzer.analyze(filename, content);
	}

	private boolean hasYamlExtension(String filename) {
		if (filename == null || filename.isBlank()) {
			return false;
		}
		String normalized = filename.toLowerCase(Locale.ROOT);
		return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
	}

	private String readContent(MultipartFile file) {
		try {
			return new String(file.getBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw PipelineAnalysisException.invalidUpload("Uploaded pipeline file could not be read.");
		}
	}
}
