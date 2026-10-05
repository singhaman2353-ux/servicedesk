package com.servicedesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

/** Proves the real application context wires encoder, decoder and properties together. */
@SpringBootTest
@ActiveProfiles("test")
class JwtWiringIntegrationTest {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void tokenIssuedByTheServiceIsAcceptedByTheApplicationDecoder() {
        JwtService.IssuedToken issued = jwtService.issueToken(7L);

        Jwt jwt = jwtDecoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());
    }
}