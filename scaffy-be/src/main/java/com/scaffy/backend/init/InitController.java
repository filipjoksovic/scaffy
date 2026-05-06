package com.scaffy.backend.init;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scaffy.backend.init.generator.ProjectGenerator;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/init")
public class InitController {

	private final StackValidator stackValidator;
	private final ProjectGenerator projectGenerator;

	public InitController(StackValidator stackValidator, ProjectGenerator projectGenerator) {
		this.stackValidator = stackValidator;
		this.projectGenerator = projectGenerator;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> init(@Valid @RequestBody InitRequest request) throws IOException {
		stackValidator.validate(request);
		byte[] zip = projectGenerator.generate(request);

		String filename = request.projectName() + ".zip";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
				.body(zip);
	}
}
