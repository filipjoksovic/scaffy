package com.scaffy.backend.recommend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FindingHasher {

	private FindingHasher() {
	}

	static String hash(FindingFixRequest.Finding finding) {
		String key = String.join("|",
				nullSafe(finding.ruleId()),
				nullSafe(finding.dimension()),
				nullSafe(finding.capability()),
				nullSafe(finding.type()),
				nullSafe(finding.location()),
				nullSafe(finding.evidence()));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available.", ex);
		}
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
