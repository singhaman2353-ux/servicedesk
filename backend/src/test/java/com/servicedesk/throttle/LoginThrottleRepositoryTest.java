package com.servicedesk.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoginThrottleRepositoryTest {

    private static final String HASH_A = "a".repeat(64);

    @Autowired
    private LoginThrottleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndReadsBackAllFields() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LoginThrottle row = new LoginThrottle(ThrottleScope.LOGIN_EMAIL, HASH_A, now);
        row.setFailureCount(3);
        row.setLockedUntil(now.plusSeconds(900));
        repository.saveAndFlush(row);
        entityManager.clear(); // force a real read from MySQL

        LoginThrottle loaded = repository
                .findByScopeAndKeyHash(ThrottleScope.LOGIN_EMAIL, HASH_A)
                .orElseThrow();

        assertThat(loaded.getFailureCount()).isEqualTo(3);
        assertThat(loaded.getWindowStart()).isEqualTo(now);
        assertThat(loaded.getLockedUntil()).isEqualTo(now.plusSeconds(900));
        assertThat(loaded.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void sameScopeAndKeyCannotBeInsertedTwice() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.saveAndFlush(new LoginThrottle(ThrottleScope.LOGIN_IP, HASH_A, now));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new LoginThrottle(ThrottleScope.LOGIN_IP, HASH_A, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}