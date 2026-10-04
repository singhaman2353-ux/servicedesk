package com.servicedesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.servicedesk.exception.BadRequestException;
import com.servicedesk.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final String RAW_PASSWORD = "Str0ngPassw0rd";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, clock);
    }

    @Test
    void createsUserWithHashedPasswordAndNormalizedFields() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser("  Rahul Sharma ", "  Rahul@Example.COM ", RAW_PASSWORD, Role.REQUESTER, null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Rahul Sharma");
        assertThat(saved.getEmail()).isEqualTo("rahul@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.REQUESTER);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        // the password is hashed, never stored as given
        assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                userService.createUser("Rahul", "RAHUL@example.com", RAW_PASSWORD, Role.REQUESTER, null))
                .isInstanceOf(ConflictException.class);

        verify(userRepository).existsByEmail("rahul@example.com");
        verifyNoMoreRepositoryWrites();
    }

    @Test
    void translatesDatabaseUniqueViolationIntoConflict() {
        // Simulates the race: both requests pass existsByEmail, the DB constraint stops the second one.
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() ->
                userService.createUser("Rahul", "rahul@example.com", RAW_PASSWORD, Role.REQUESTER, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsPasswordLongerThan72BytesBeforeTouchingTheDatabase() {
        // 2 ASCII chars + 36 two-byte characters = 38 characters but 74 bytes
        String tooManyBytes = "a1" + "\u00e9".repeat(36);

        assertThatThrownBy(() ->
                userService.createUser("Rahul", "rahul@example.com", tooManyBytes, Role.REQUESTER, null))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository);
    }

    private void verifyNoMoreRepositoryWrites() {
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).saveAndFlush(any(User.class));
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }
}