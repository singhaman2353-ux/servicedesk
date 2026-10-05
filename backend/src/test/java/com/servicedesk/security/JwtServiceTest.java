package com.servicedesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Pure unit tests: real encoder/decoder, no Spring context, no database. */
class JwtServiceTest {

    // Random per run, so no secret-looking constant is committed. 73 bytes, long enough for HS512 too.
    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    private static final String OTHER_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

    private final JwtConfig jwtConfig = new JwtConfig();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private JwtService serviceFor(String secret, Instant at) {
        JwtProperties properties = new JwtProperties(secret, "servicedesk", 60);
        return new JwtService(jwtConfig.jwtEncoder(properties), properties, Clock.fixed(at, ZoneOffset.UTC));
    }

    private JwtDecoder decoderFor(String secret) {
        return jwtConfig.jwtDecoder(new JwtProperties(secret, "servicedesk", 60));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void issuedTokenIsAcceptedAndCarriesTheExpectedClaims() {
        JwtService.IssuedToken issued = serviceFor(SECRET, now).issueToken(42L);

        Jwt jwt = decoderFor(SECRET).decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("servicedesk");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(60)));
        assertThat(issued.expiresAt()).isEqualTo(jwt.getExpiresAt());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void tokenCarriesNoRoleEmailOrOtherPersonalClaims() {
        String token = serviceFor(SECRET, now).issueToken(42L).value();

        Jwt jwt = decoderFor(SECRET).decode(token);

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti");
    }

    @Test
    void everyTokenGetsADifferentId() {
        JwtService service = serviceFor(SECRET, now);

        Jwt first = decoderFor(SECRET).decode(service.issueToken(1L).value());
        Jwt second = decoderFor(SECRET).decode(service.issueToken(1L).value());

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void expiredTokenIsRejected() {
        // issued two hours ago with a 60-minute lifetime: expired an hour ago (well beyond clock skew)
        String token = serviceFor(SECRET, now.minus(Duration.ofHours(2))).issueToken(42L).value();

        assertThatThrownBy(() -> decoderFor(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithTamperedPayloadIsRejected() {
        String token = serviceFor(SECRET, now).issueToken(42L).value();
        String[] parts = token.split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"1\",\"iss\":\"servicedesk\",\"exp\":"
                + (now.getEpochSecond() + 3600) + "}");
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> decoderFor(SECRET).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        String token = serviceFor(OTHER_SECRET, now).issueToken(42L).value();

        assertThatThrownBy(() -> decoderFor(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenWithAlgNoneIsRejected() {
        String unsigned = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + base64Url("{\"sub\":\"42\",\"iss\":\"servicedesk\",\"exp\":" + (now.getEpochSecond() + 3600) + "}")
                + ".";

        assertThatThrownBy(() -> decoderFor(SECRET).decode(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherHmacAlgorithmIsRejectedEvenWithTheRightKey() {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SECRET.getBytes(StandardCharsets.UTF_8)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("servicedesk")
                .subject("42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        String hs256 = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String hs512 = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS512).build(), claims)).getTokenValue();

        // control: same key + HS256 is accepted, so the HS512 rejection is about the algorithm
        assertThat(decoderFor(SECRET).decode(hs256).getSubject()).isEqualTo("42");
        assertThatThrownBy(() -> decoderFor(SECRET).decode(hs512)).isInstanceOf(JwtException.class);
    }
}