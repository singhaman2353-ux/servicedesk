package com.servicedesk.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.servicedesk.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginThrottleServiceTest {

    private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");
    private static final String EMAIL = "a@example.com";

    private final MutableClock clock = new MutableClock(START);
    // email limit 3, ip limit 5, login window/lock 15 min, register 20 / 60 / 60
    private final ThrottleProperties properties = new ThrottleProperties(3, 5, 15, 15, 20, 60, 60);

    @Mock
    private LoginThrottleRepository repository;

    private LoginThrottleService service;
    private LoginThrottle row;

    @BeforeEach
    void setUp() {
        service = new LoginThrottleService(repository, properties, clock);
        row = new LoginThrottle(ThrottleScope.LOGIN_EMAIL, LoginThrottleService.hash(EMAIL), START);
        lenient().when(repository.findForUpdate(eq(ThrottleScope.LOGIN_EMAIL), anyString()))
                .thenReturn(Optional.of(row));
        lenient().when(repository.findByScopeAndKeyHash(eq(ThrottleScope.LOGIN_EMAIL), anyString()))
                .thenReturn(Optional.of(row));
    }

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(ThrottleScope.LOGIN_EMAIL, EMAIL);
        }
    }

    @Test
    void staysUnlockedBelowTheLimit() {
        fail(2);

        assertThat(row.getFailureCount()).isEqualTo(2);
        assertThat(service.remainingLock(ThrottleScope.LOGIN_EMAIL, EMAIL)).isEmpty();
    }

    @Test
    void locksWhenTheLimitIsReached() {
        fail(3);

        assertThat(service.remainingLock(ThrottleScope.LOGIN_EMAIL, EMAIL))
                .contains(Duration.ofMinutes(15));
    }

    @Test
    void attemptsDuringALockDoNotExtendIt() {
        fail(3);
        Instant lockedUntil = row.getLockedUntil();

        clock.advance(Duration.ofMinutes(5));
        fail(2);

        assertThat(row.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(row.getFailureCount()).isEqualTo(3);
    }

    @Test
    void lockExpiresAndTheCounterRestarts() {
        fail(3);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(service.remainingLock(ThrottleScope.LOGIN_EMAIL, EMAIL)).isEmpty();

        fail(1);
        assertThat(row.getFailureCount()).isEqualTo(1);
        assertThat(row.getLockedUntil()).isNull();
    }

    @Test
    void anExpiredWindowResetsTheCount() {
        fail(2);

        clock.advance(Duration.ofMinutes(16));
        fail(1);

        assertThat(row.getFailureCount()).isEqualTo(1);
        assertThat(row.getLockedUntil()).isNull();
    }

    @Test
    void storesOnlyAHashOfTheKey() {
        fail(1);

        String expectedHash = LoginThrottleService.hash(EMAIL);
        assertThat(expectedHash).hasSize(64).doesNotContain("example");
        verify(repository).insertIfAbsent(eq("LOGIN_EMAIL"), eq(expectedHash), any(Instant.class));
        // SHA-256("abc"), a published test vector
        assertThat(LoginThrottleService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void resetDeletesTheRow() {
        service.reset(ThrottleScope.LOGIN_EMAIL, EMAIL);

        verify(repository).deleteByScopeAndKeyHash(ThrottleScope.LOGIN_EMAIL, LoginThrottleService.hash(EMAIL));
    }

    @Test
    void registrationHasItsOwnPolicy() {
        ThrottleProperties.Policy policy = properties.policyFor(ThrottleScope.REGISTER_IP);

        assertThat(policy.maxAttempts()).isEqualTo(20);
        assertThat(policy.window()).isEqualTo(Duration.ofMinutes(60));
        assertThat(properties.policyFor(ThrottleScope.LOGIN_IP).maxAttempts()).isEqualTo(5);
    }
}