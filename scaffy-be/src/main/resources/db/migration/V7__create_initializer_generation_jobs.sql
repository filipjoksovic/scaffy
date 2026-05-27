CREATE TABLE initializer_generation_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    project_name VARCHAR(64) NOT NULL,
    request_json TEXT NOT NULL,
    selection_json TEXT NOT NULL,
    progress_message TEXT,
    error_message TEXT,
    artifact_object_key TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX initializer_generation_jobs_status_created_idx
    ON initializer_generation_jobs(status, created_at);
