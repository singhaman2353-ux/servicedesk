package com.servicedesk.throttle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "login_throttle")
public class LoginThrottle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope", nullable = false, length = 30)
    private ThrottleScope scope;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoginThrottle() {
        // for JPA
    }

    public LoginThrottle(ThrottleScope scope, String keyHash, Instant now) {
        this.scope = scope;
        this.keyHash = keyHash;
        this.failureCount = 0;
        this.windowStart = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public ThrottleScope getScope() { return scope; }
    public String getKeyHash() { return keyHash; }
    public int getFailureCount() { return failureCount; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}