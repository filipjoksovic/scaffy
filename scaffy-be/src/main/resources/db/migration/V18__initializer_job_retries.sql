ALTER TABLE initializer_generation_jobs
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 3,
    ADD COLUMN heartbeat_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN idempotency_key VARCHAR(128);

-- One in-flight result per (user, idempotency key); anonymous jobs are unconstrained.
CREATE UNIQUE INDEX initializer_generation_jobs_idempotency_key_idx
    ON initializer_generation_jobs(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Reaper lookup: queued jobs whose backoff window has elapsed.
CREATE INDEX initializer_generation_jobs_next_attempt_idx
    ON initializer_generation_jobs(status, next_attempt_at);

-- Reaper lookup: running jobs whose lease (heartbeat) has gone stale.
CREATE INDEX initializer_generation_jobs_heartbeat_idx
    ON initializer_generation_jobs(status, heartbeat_at);
