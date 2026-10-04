CREATE TABLE audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id      BIGINT       NULL,
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   VARCHAR(64)  NULL,
    details       VARCHAR(500) NULL,
    ip_address    VARCHAR(45)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    INDEX idx_audit_logs_created_at (created_at),
    INDEX idx_audit_logs_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;