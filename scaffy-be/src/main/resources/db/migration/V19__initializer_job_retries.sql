ALTER TABLE initializer_generation_jobs ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE initializer_generation_jobs ADD COLUMN max_attempts INT NOT NULL DEFAULT 3;
ALTER TABLE initializer_generation_jobs ADD COLUMN heartbeat_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE initializer_generation_jobs ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE initializer_generation_jobs ADD COLUMN idempotency_key VARCHAR(128);

-- One in-flight result per (user, idempotency key); rows with a NULL key are unconstrained
-- because NULLs are distinct in a unique index, so anonymous/keyless jobs stay unconstrained.
CREATE UNIQUE INDEX initializer_generation_jobs_idempotency_key_idx
    ON initializer_generation_jobs(user_id, idempotency_key);

-- Reaper lookup: queued jobs whose backoff window has elapsed.
CREATE INDEX initializer_generation_jobs_next_attempt_idx
    ON initializer_generation_jobs(status, next_attempt_at);

-- Reaper lookup: running jobs whose lease (heartbeat) has gone stale.
CREATE INDEX initializer_generation_jobs_heartbeat_idx
    ON initializer_generation_jobs(status, heartbeat_at);
