package com.scaffy.backend.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scaffy.repository.analysis")
public class RepositoryAnalysisProperties {

	private final Queue queue = new Queue();
	private final Jobs jobs = new Jobs();
	private final Worker worker = new Worker();

	public Queue getQueue() {
		return queue;
	}

	public Jobs getJobs() {
		return jobs;
	}

	public Worker getWorker() {
		return worker;
	}

	public static class Queue {
		private boolean enabled = false;
		private String name = "scaffy:repository-analysis-jobs";

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

	public static class Jobs {
		private int maxAttempts = 3;
		private long leaseTimeoutSeconds = 120;
		private long reaperIntervalMs = 20000;
		private long retryBackoffMs = 30000;

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
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

		public long getRetryBackoffMs() {
			return retryBackoffMs;
		}

		public void setRetryBackoffMs(long retryBackoffMs) {
			this.retryBackoffMs = retryBackoffMs;
		}
	}

	public static class Worker {
		private boolean enabled = false;
		private long heartbeatIntervalMs = 10000;
		private long pollTimeoutSeconds = 5;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public long getHeartbeatIntervalMs() {
			return heartbeatIntervalMs;
		}

		public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
			this.heartbeatIntervalMs = heartbeatIntervalMs;
		}

		public long getPollTimeoutSeconds() {
			return pollTimeoutSeconds;
		}

		public void setPollTimeoutSeconds(long pollTimeoutSeconds) {
			this.pollTimeoutSeconds = pollTimeoutSeconds;
		}
	}
}
