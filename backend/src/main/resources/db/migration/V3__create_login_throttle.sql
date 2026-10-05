-- Failed-attempt counters used to throttle login (and, later, registration).
-- Only a SHA-256 hash of the email / IP is stored, never the raw value.
CREATE TABLE login_throttle (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    scope          VARCHAR(30)  NOT NULL,
    key_hash       VARCHAR(64)  NOT NULL,
    failure_count  INT          NOT NULL DEFAULT 0,
    window_start   DATETIME(6)  NOT NULL,
    locked_until   DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_login_throttle_scope_key UNIQUE (scope, key_hash),
    KEY idx_login_throttle_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;