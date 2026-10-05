package com.servicedesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String VALID_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

    @Test
    void acceptsValidValues() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "servicedesk", 60);

        assertThat(properties.issuer()).isEqualTo("servicedesk");
        assertThat(properties.expiration()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void acceptsSecretOfExactly32Bytes() {
        JwtProperties properties = new JwtProperties("a".repeat(32), "servicedesk", 60);

        assertThat(properties.secret()).hasSize(32);
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, "servicedesk", 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("", "servicedesk", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSecretShorterThan32BytesWithoutEchoingIt() {
        String weak = "weak-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> new JwtProperties(weak, "servicedesk", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(weak);
    }

    @Test
    void rejectsBlankIssuer() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, " ", 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, null, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveOrExcessiveLifetime() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "servicedesk", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "servicedesk", -5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "servicedesk", 1441))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverContainsTheSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "servicedesk", 60);

        assertThat(properties.toString()).doesNotContain(VALID_SECRET).contains("****");
    }
}