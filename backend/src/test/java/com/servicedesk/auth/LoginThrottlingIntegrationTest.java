package com.servicedesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicedesk.audit.AuditAction;
import com.servicedesk.audit.AuditLogRepository;
import com.servicedesk.throttle.LoginThrottle;
import com.servicedesk.throttle.LoginThrottleRepository;
import com.servicedesk.throttle.ThrottleScope;
import com.servicedesk.team.TeamRepository;
import com.servicedesk.user.Role;
import com.servicedesk.user.UserRepository;
import com.servicedesk.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "app.security.throttle.login-max-failures-per-email=3",
        "app.security.throttle.login-max-failures-per-ip=5"})
@ActiveProfiles("test")
class LoginThrottlingIntegrationTest {

    private static final String EMAIL = "throttle.user@example.com";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final String WRONG = "Wr0ngPassword99";

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private LoginThrottleRepository throttleRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userService.createUser("Throttle User", EMAIL, PASSWORD, Role.REQUESTER, null);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        auditLogRepository.deleteAll();
        throttleRepository.deleteAll();
        userRepository.deleteAll();
        teamRepository.deleteAll();
    }

    private MvcResult login(String email, String password, String ip, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    @Test
    void emailIsLockedAfterTooManyFailuresEvenWithTheCorrectPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(EMAIL, WRONG, "10.0.0.1", 401);
        }

        MvcResult locked = login(EMAIL, PASSWORD, "10.0.0.1", 429);

        String retryAfter = locked.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 900L);
        assertThat(JsonPath.<String>read(locked.getResponse().getContentAsString(), "$.error"))
                .isEqualTo("TOO_MANY_REQUESTS");
        assertThat(auditLogRepository.findAll())
                .anyMatch(entry -> entry.getAction() == AuditAction.LOGIN_THROTTLED);
        // the throttle table holds hashes only, never the email
        assertThat(throttleRepository.findAll())
                .allSatisfy(row -> assertThat(row.getKeyHash()).hasSize(64).doesNotContain("example"));
    }

    @Test
    void unknownEmailsAreThrottledExactlyLikeKnownOnes() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("ghost@example.com", WRONG, "10.0.0.2", 401);
        }

        login("ghost@example.com", WRONG, "10.0.0.2", 429);
    }

    @Test
    void attemptsDuringALockDoNotExtendIt() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(EMAIL, WRONG, "10.0.0.3", 401);
        }
        LoginThrottle before = emailRow();
        Instant lockedUntil = before.getLockedUntil();
        assertThat(lockedUntil).isNotNull();

        login(EMAIL, WRONG, "10.0.0.3", 429);
        login(EMAIL, PASSWORD, "10.0.0.3", 429);

        LoginThrottle after = emailRow();
        assertThat(after.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(after.getFailureCount()).isEqualTo(3);
    }

    @Test
    void aSuccessfulLoginResetsTheEmailCounter() throws Exception {
        login(EMAIL, WRONG, "10.0.0.4", 401);
        login(EMAIL, WRONG, "10.0.0.4", 401);
        login(EMAIL, PASSWORD, "10.0.0.4", 200);

        login(EMAIL, WRONG, "10.0.0.4", 401);
        login(EMAIL, WRONG, "10.0.0.4", 401);

        // 2 failures since the reset, below the limit of 3, so a correct login still works
        login(EMAIL, PASSWORD, "10.0.0.4", 200);
    }

    @Test
    void anIpIsLockedAcrossDifferentEmailsWithoutAffectingOtherIps() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("scan" + i + "@example.com", WRONG, "10.9.8.7", 401);
        }

        login("scan-new@example.com", WRONG, "10.9.8.7", 429);
        login(EMAIL, PASSWORD, "10.9.8.8", 200);
    }

    private LoginThrottle emailRow() {
        return throttleRepository.findAll().stream()
                .filter(row -> row.getScope() == ThrottleScope.LOGIN_EMAIL)
                .findFirst().orElseThrow();
    }
}