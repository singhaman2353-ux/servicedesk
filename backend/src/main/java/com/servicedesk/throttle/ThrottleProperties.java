package com.servicedesk.throttle;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.throttle")
public record ThrottleProperties(
        @DefaultValue("10") int loginMaxFailuresPerEmail,
        @DefaultValue("30") int loginMaxFailuresPerIp,
        @DefaultValue("15") long loginWindowMinutes,
        @DefaultValue("15") long loginLockMinutes,
        @DefaultValue("20") int registerMaxAttemptsPerIp,
        @DefaultValue("60") long registerWindowMinutes,
        @DefaultValue("60") long registerLockMinutes) {

    public ThrottleProperties {
        if (loginMaxFailuresPerEmail < 1 || loginMaxFailuresPerIp < 1 || registerMaxAttemptsPerIp < 1
                || loginWindowMinutes < 1 || loginLockMinutes < 1
                || registerWindowMinutes < 1 || registerLockMinutes < 1) {
            throw new IllegalArgumentException("app.security.throttle.* values must all be at least 1");
        }
    }

    public record Policy(int maxAttempts, Duration window, Duration lock) {
    }

    public Policy policyFor(ThrottleScope scope) {
        return switch (scope) {
            case LOGIN_EMAIL -> new Policy(loginMaxFailuresPerEmail,
                    Duration.ofMinutes(loginWindowMinutes), Duration.ofMinutes(loginLockMinutes));
            case LOGIN_IP -> new Policy(loginMaxFailuresPerIp,
                    Duration.ofMinutes(loginWindowMinutes), Duration.ofMinutes(loginLockMinutes));
            case REGISTER_IP -> new Policy(registerMaxAttemptsPerIp,
                    Duration.ofMinutes(registerWindowMinutes), Duration.ofMinutes(registerLockMinutes));
        };
    }
}