package com.scaffy.backend.init;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Component
@ConditionalOnProperty(prefix = "scaffy.init.storage", name = "enabled", havingValue = "true")
public class S3InitArtifactStorage implements InitArtifactStorage {

	private final S3Client s3Client;
	private final InitializerProperties properties;

	public S3InitArtifactStorage(InitializerProperties properties) {
		this.properties = properties;
		InitializerProperties.Storage storage = properties.getStorage();
		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(storage.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(storage.isPathStyleAccess())
						.build());
		URI endpoint = storage.getEndpoint();
		if (endpoint != null) {
			builder.endpointOverride(endpoint);
		}
		this.s3Client = builder.build();
	}

	@Override
	public InitArtifact download(String objectKey) {
		byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(properties.getStorage().getBucket())
				.key(objectKey)
				.build()).asByteArray();
		String filename = objectKey.substring(objectKey.lastIndexOf('/') + 1);
		return new InitArtifact(bytes, filename);
	}
}
