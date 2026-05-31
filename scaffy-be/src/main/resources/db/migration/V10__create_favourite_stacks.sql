CREATE TABLE favourite_stacks (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name              VARCHAR(64)  NOT NULL,
    frontend          VARCHAR(32)  NOT NULL,
    frontend_version  VARCHAR(16)  NOT NULL,
    frontend_runtime  VARCHAR(32)  NOT NULL,
    backend           VARCHAR(32)  NOT NULL,
    backend_version   VARCHAR(16)  NOT NULL,
    backend_runtime   VARCHAR(32)  NOT NULL,
    pipeline          VARCHAR(32)  NOT NULL,
    pipeline_maturity VARCHAR(8)   NOT NULL,
    include_docker    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX favourite_stacks_user_id_idx ON favourite_stacks(user_id);
