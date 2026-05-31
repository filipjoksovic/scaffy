package com.scaffy.backend.init;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.init")
public class InitializerProperties {

	private final Queue queue = new Queue();
	private final Storage storage = new Storage();
	private final Jobs jobs = new Jobs();

	public Queue getQueue() {
		return queue;
	}

	public Storage getStorage() {
		return storage;
	}

	public Jobs getJobs() {
		return jobs;
	}

	public static class Jobs {
		private int maxInFlightPerUser = 3;
		private long leaseTimeoutSeconds = 120;
		private long reaperIntervalMs = 20000;

		public int getMaxInFlightPerUser() {
			return maxInFlightPerUser;
		}

		public void setMaxInFlightPerUser(int maxInFlightPerUser) {
			this.maxInFlightPerUser = maxInFlightPerUser;
		}

		public long getLeaseTimeoutSeconds() {
			return leaseTimeoutSeconds;
		}

		public void setLeaseTimeoutSeconds(long leaseTimeoutSeconds) {
			this.leaseTimeoutSeconds = leaseTimeoutSeconds;
		}

		public long getReaperIntervalMs() {
			return reaperIntervalMs;
		}

		public void setReaperIntervalMs(long reaperIntervalMs) {
			this.reaperIntervalMs = reaperIntervalMs;
		}
	}

	public static class Queue {
		private boolean enabled = false;
		private String name = "scaffy:init-jobs";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Storage {
		private boolean enabled = false;
		private String bucket = "scaffy-initializer";
		private String region = "us-east-1";
		private URI endpoint;
		private String accessKey = "scaffy";
		private String secretKey = "scaffy-secret";
		private boolean pathStyleAccess = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getBucket() {
			return bucket;
		}

		public void setBucket(String bucket) {
			this.bucket = bucket;
		}

		public String getRegion() {
			return region;
		}

		public void setRegion(String region) {
			this.region = region;
		}

		public URI getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(URI endpoint) {
			this.endpoint = endpoint;
		}

		public String getAccessKey() {
			return accessKey;
		}

		public void setAccessKey(String accessKey) {
			this.accessKey = accessKey;
		}

		public String getSecretKey() {
			return secretKey;
		}

		public void setSecretKey(String secretKey) {
			this.secretKey = secretKey;
		}

		public boolean isPathStyleAccess() {
			return pathStyleAccess;
		}

		public void setPathStyleAccess(boolean pathStyleAccess) {
			this.pathStyleAccess = pathStyleAccess;
		}
	}
}
