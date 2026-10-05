package com.servicedesk.auth;

import com.servicedesk.audit.AuditAction;
import com.servicedesk.audit.AuditResourceType;
import com.servicedesk.audit.AuditService;
import com.servicedesk.exception.InvalidCredentialsException;
import com.servicedesk.exception.TooManyRequestsException;
import com.servicedesk.security.JwtProperties;
import com.servicedesk.security.JwtService;
import com.servicedesk.throttle.LoginThrottleService;
import com.servicedesk.throttle.ThrottleScope;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserRepository;
import com.servicedesk.user.UserResponse;
import com.servicedesk.user.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String UNKNOWN_IP = "unknown";
    private static final String TIMING_PASSWORD = "timing-equalizer-not-a-real-password";

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final LoginThrottleService throttleService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    /** Compared against when the email is unknown, so response time does not reveal valid accounts. */
    private final String dummyHash;

    public AuthService(UserService userService,
                       UserRepository userRepository,
                       AuditService auditService,
                       LoginThrottleService throttleService,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.throttleService = throttleService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(TIMING_PASSWORD);
    }

    /** Self-registration always yields a REQUESTER with no team. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.createUser(
                request.name(), request.email(), request.password(), Role.REQUESTER, null);
        auditService.record(user, AuditAction.USER_REGISTERED, AuditResourceType.USER,
                user.getId(), "Self-registration");
        return UserResponse.from(user);
    }

    /**
     * Deliberately NOT one big transaction: failure counters and audit rows are written in their
     * own transactions so they persist even though the request ends in an error.
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        String email = UserService.normalizeEmail(request.email());
        String ip = (clientIp == null || clientIp.isBlank()) ? UNKNOWN_IP : clientIp;

        Optional<Duration> lock = longestLock(email, ip);
        if (lock.isPresent()) {
            audit(null, AuditAction.LOGIN_THROTTLED, AuditResourceType.AUTH, null,
                    "Login attempt rejected: too many recent failures", ip);
            throw new TooManyRequestsException(toRetryAfterSeconds(lock.get()));
        }

        User user = userRepository.findWithTeamByEmail(email).orElse(null);
        // Always run one BCrypt comparison, whether or not the account exists.
        boolean passwordOk = passwordMatches(request.password(), user == null ? dummyHash : user.getPasswordHash());

        if (user == null) {
            throw failLogin(null, email, ip, "Unknown account");
        }
        if (!passwordOk) {
            throw failLogin(user, email, ip, "Wrong password");
        }
        if (!user.isActive()) {
            throw failLogin(user, email, ip, "Inactive account");
        }

        try {
            throttleService.reset(ThrottleScope.LOGIN_EMAIL, email);
        } catch (RuntimeException ex) {
            log.error("Failed to reset login throttle", ex);
        }
        JwtService.IssuedToken token = jwtService.issueToken(user.getId());
        audit(user, AuditAction.LOGIN_SUCCESS, AuditResourceType.USER, user.getId(), "Login successful", ip);

        return new LoginResponse(token.value(), "Bearer", token.expiresAt(),
                jwtProperties.expiration().toSeconds(), UserResponse.from(user));
    }

    private Optional<Duration> longestLock(String email, String ip) {
        Optional<Duration> byEmail = throttleService.remainingLock(ThrottleScope.LOGIN_EMAIL, email);
        Optional<Duration> byIp = throttleService.remainingLock(ThrottleScope.LOGIN_IP, ip);
        return Stream.of(byEmail, byIp).flatMap(Optional::stream).max(Comparator.naturalOrder());
    }

    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            passwordEncoder.matches(TIMING_PASSWORD, hash); // keep timing similar, always a mismatch
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private InvalidCredentialsException failLogin(User user, String email, String ip, String reason) {
        for (var target : new Object[][] {{ThrottleScope.LOGIN_EMAIL, email}, {ThrottleScope.LOGIN_IP, ip}}) {
            try {
                throttleService.recordFailure((ThrottleScope) target[0], (String) target[1]);
            } catch (RuntimeException ex) {
                log.error("Failed to record login failure for scope {}", target[0], ex);
            }
        }
        // The attempted email is NOT stored for unknown accounts; reasons never contain secrets.
        audit(user, AuditAction.LOGIN_FAILURE,
                user == null ? AuditResourceType.AUTH : AuditResourceType.USER,
                user == null ? null : user.getId(), reason, ip);
        return new InvalidCredentialsException();
    }

    private void audit(User actor, AuditAction action, AuditResourceType type,
                       Object resourceId, String details, String ip) {
        try {
            auditService.recordIndependently(actor, action, type, resourceId, details, ip);
        } catch (RuntimeException ex) {
            log.error("Failed to write audit entry {}", action, ex);
        }
    }

    private static long toRetryAfterSeconds(Duration remaining) {
        return Math.max(1L, (remaining.toMillis() + 999) / 1000);
    }
}