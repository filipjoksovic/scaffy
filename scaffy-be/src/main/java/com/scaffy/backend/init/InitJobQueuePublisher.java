package com.scaffy.backend.init;

import java.util.UUID;

public interface InitJobQueuePublisher {
	void enqueue(UUID jobId);
}
