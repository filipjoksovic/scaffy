package com.scaffy.backend.init;

import java.util.UUID;

public class NoopInitJobQueuePublisher implements InitJobQueuePublisher {

	@Override
	public void enqueue(UUID jobId) {
		// Local tests can exercise job persistence without requiring Redis.
	}
}
