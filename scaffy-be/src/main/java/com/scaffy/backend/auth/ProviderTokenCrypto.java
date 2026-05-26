package com.scaffy.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

@Service
public class ProviderTokenCrypto {

	private static final String CIPHER = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final AuthProperties authProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public ProviderTokenCrypto(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public String encrypt(String token) {
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
			return encode(iv) + "." + encode(encrypted);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Provider token could not be encrypted.", ex);
		}
	}

	public String decrypt(String encryptedToken) {
		try {
			String[] parts = encryptedToken.split("\\.");
			if (parts.length != 2) {
				throw new IllegalArgumentException("Invalid encrypted token format.");
			}
			Cipher cipher = Cipher.getInstance(CIPHER);
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, decode(parts[0])));
			return new String(cipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Provider token could not be decrypted.", ex);
		}
	}

	private SecretKeySpec key() throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(authProperties.providerTokenEncryptionSecret().getBytes(StandardCharsets.UTF_8));
		return new SecretKeySpec(digest, "AES");
	}

	private String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private byte[] decode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}
}
