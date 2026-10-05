package com.servicedesk.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, long expirationMinutes) {

    static final int MIN_SECRET_BYTES = 32;
    static final long MAX_EXPIRATION_MINUTES = 24 * 60;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.jwt.issuer must not be blank");
        }

        if (expirationMinutes <= 0 || expirationMinutes > MAX_EXPIRATION_MINUTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.expiration-minutes must be between 1 and "
                            + MAX_EXPIRATION_MINUTES);
        }
    }

    public Duration expiration() {
        return Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=****, issuer=" + issuer
                + ", expirationMinutes=" + expirationMinutes + "]";
    }
}