package com.servicedesk.throttle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed-window failure counters stored in MySQL. Keys are stored only as SHA-256 hashes.
 * Writes run in their own short transaction, so they persist even when the login request fails.
 */
@Service
public class LoginThrottleService {

    private final LoginThrottleRepository repository;
    private final ThrottleProperties properties;
    private final Clock clock;

    public LoginThrottleService(LoginThrottleRepository repository, ThrottleProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Time left on an active lock for this key, or empty if it is not locked. */
    @Transactional(readOnly = true)
    public Optional<Duration> remainingLock(ThrottleScope scope, String rawKey) {
        Instant now = Instant.now(clock);
        return repository.findByScopeAndKeyHash(scope, hash(rawKey))
                .map(LoginThrottle::getLockedUntil)
                .filter(lockedUntil -> lockedUntil.isAfter(now))
                .map(lockedUntil -> Duration.between(now, lockedUntil));
    }

    /** Counts one failed attempt (for REGISTER_IP: one attempt). Attempts during a lock are ignored. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(ThrottleScope scope, String rawKey) {
        ThrottleProperties.Policy policy = properties.policyFor(scope);
        String keyHash = hash(rawKey);
        Instant now = Instant.now(clock);

        repository.insertIfAbsent(scope.name(), keyHash, now);
        LoginThrottle row = repository.findForUpdate(scope, keyHash).orElseThrow();

        if (row.getLockedUntil() != null && row.getLockedUntil().isAfter(now)) {
            return; // still locked: never extend the lock
        }
        boolean windowExpired = !row.getWindowStart().plus(policy.window()).isAfter(now);
        boolean lockExpired = row.getLockedUntil() != null;
        if (windowExpired || lockExpired) {
            row.setFailureCount(0);
            row.setWindowStart(now);
            row.setLockedUntil(null);
        }
        row.setFailureCount(row.getFailureCount() + 1);
        if (row.getFailureCount() >= policy.maxAttempts()) {
            row.setLockedUntil(now.plus(policy.lock()));
        }
        row.setUpdatedAt(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reset(ThrottleScope scope, String rawKey) {
        repository.deleteByScopeAndKeyHash(scope, hash(rawKey));
    }

    static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}