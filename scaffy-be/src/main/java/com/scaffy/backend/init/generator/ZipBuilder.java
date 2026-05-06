package com.scaffy.backend.init.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

/**
 * Writes a list of {@link EmittedFile}s into a single ZIP, prefixed by
 * {@code rootDirectory} so the archive expands to a project folder rather
 * than dropping its contents into the user's CWD.
 */
@Component
public class ZipBuilder {

	public byte[] build(String rootDirectory, List<EmittedFile> files) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (EmittedFile file : files) {
				ZipEntry entry = new ZipEntry(rootDirectory + "/" + file.destinationPath());
				zip.putNextEntry(entry);
				zip.write(file.content());
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}
}
