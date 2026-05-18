package com.scaffy.backend.init.generator;

import java.util.Arrays;

/**
 * One file destined for the generated ZIP. Both the template-overlay path
 * and the cached-artifact path produce these; ZipBuilder just writes them.
 */
public record EmittedFile(String destinationPath, byte[] content) {

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof EmittedFile other)) return false;
		return java.util.Objects.equals(destinationPath, other.destinationPath)
				&& Arrays.equals(content, other.content);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(destinationPath, Arrays.hashCode(content));
	}

	@Override
	public String toString() {
		return "EmittedFile[destinationPath=" + destinationPath + ", content=" + Arrays.toString(content) + "]";
	}
}
