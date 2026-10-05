package com.servicedesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.servicedesk.audit.AuditAction;
import com.servicedesk.audit.AuditLog;
import com.servicedesk.audit.AuditLogRepository;
import com.servicedesk.audit.AuditResourceType;
import com.servicedesk.team.Team;
import com.servicedesk.team.TeamRepository;
import com.servicedesk.throttle.LoginThrottleRepository;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserRepository;
import com.servicedesk.user.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AuthLoginIntegrationTest {

    private static final String EMAIL = "login.user@example.com";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final String WRONG_PASSWORD = "Wr0ngPassword99";

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private LoginThrottleRepository throttleRepository;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userService.createUser("Login User", EMAIL, PASSWORD, Role.REQUESTER, null);
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

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult login(String email, String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private List<AuditLog> audit(AuditAction action) {
        return auditLogRepository.findAll().stream().filter(a -> a.getAction() == action).toList();
    }

    @Test
    void loginReturnsATokenAndTheUserWithoutSecrets() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  Login.User@Example.com ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.role").value("REQUESTER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        Jwt jwt = jwtDecoder.decode(token);
        User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(stored.getId()));
        // role, email and name must NOT be in the token
        assertThat(jwt.getClaims()).doesNotContainKeys("role", "roles", "email", "name", "scope");
    }

    @Test
    void agentLoginIncludesTheTeam() throws Exception {
        Team team = teamRepository.save(new Team("Network Support"));
        userService.createUser("Agent One", "agent.one@example.com", PASSWORD, Role.AGENT, team);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("agent.one@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("AGENT"))
                .andExpect(jsonPath("$.user.teamName").value("Network Support"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        String wrongPassword = login(EMAIL, WRONG_PASSWORD, 401).getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD, 401).getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.error")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(unknownEmail, "$.error")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.message"));
        assertThat(JsonPath.<Integer>read(wrongPassword, "$.status"))
                .isEqualTo(JsonPath.<Integer>read(unknownEmail, "$.status"));
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        jdbcTemplate.update("UPDATE users SET active = false WHERE email = ?", EMAIL);

        String body = login(EMAIL, PASSWORD, 401).getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.error")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void passwordOver72BytesIsAnOrdinaryFailureNotAnError() throws Exception {
        String tooManyBytes = "a1" + "\u00e9".repeat(36);

        String body = login(EMAIL, tooManyBytes, 401).getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.error")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void failedLoginsAreAuditedWithoutSecrets() throws Exception {
        login(EMAIL, WRONG_PASSWORD, 401);
        login("ghost@example.com", PASSWORD, 401);

        List<AuditLog> failures = audit(AuditAction.LOGIN_FAILURE);
        assertThat(failures).hasSize(2);

        AuditLog known = failures.stream().filter(a -> a.getResourceType() == AuditResourceType.USER)
                .findFirst().orElseThrow();
        assertThat(known.getResourceId()).isEqualTo(
                String.valueOf(userRepository.findByEmail(EMAIL).orElseThrow().getId()));
        assertThat(known.getIpAddress()).isEqualTo("127.0.0.1");

        AuditLog unknown = failures.stream().filter(a -> a.getResourceType() == AuditResourceType.AUTH)
                .findFirst().orElseThrow();
        assertThat(unknown.getResourceId()).isNull();
        assertThat(unknown.getActor()).isNull();

        for (AuditLog entry : failures) {
            assertThat(entry.getDetails())
                    .doesNotContain("ghost@example.com")
                    .doesNotContain(PASSWORD)
                    .doesNotContain(WRONG_PASSWORD);
        }
    }

    @Test
    void successfulLoginIsAudited() throws Exception {
        login(EMAIL, PASSWORD, 200);

        List<AuditLog> successes = audit(AuditAction.LOGIN_SUCCESS);
        assertThat(successes).hasSize(1);
        assertThat(successes.get(0).getResourceId()).isEqualTo(
                String.valueOf(userRepository.findByEmail(EMAIL).orElseThrow().getId()));
    }

    @Test
    void invalidBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }
}