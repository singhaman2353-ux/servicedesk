package com.servicedesk.user;

import com.servicedesk.exception.BadRequestException;
import com.servicedesk.exception.ConflictException;
import com.servicedesk.team.Team;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** BCrypt only uses the first 72 bytes of a password. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final String DUPLICATE_MESSAGE =
            "Registration could not be completed. If you already have an account, please log in.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates a user with a BCrypt-hashed password. This is the single place where users
     * are created; callers decide the role (self-registration passes REQUESTER, admins pass
     * whatever they are authorized to create).
     */
    @Transactional
    public User createUser(String name, String email, String rawPassword, Role role, Team team) {
        String normalizedEmail = normalizeEmail(email);
        validatePasswordBytes(rawPassword);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException(DUPLICATE_MESSAGE);
        }

        User user = new User(
                name.strip(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                role,
                team,
                Instant.now(clock));

        try {
            // saveAndFlush so the UNIQUE(email) constraint is checked right now. This closes
            // the race where two requests pass existsByEmail at the same time.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(DUPLICATE_MESSAGE);
        }
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePasswordBytes(String rawPassword) {
        if (rawPassword == null || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must be at most " + MAX_PASSWORD_BYTES
                    + " bytes when encoded as UTF-8");
        }
    }
}