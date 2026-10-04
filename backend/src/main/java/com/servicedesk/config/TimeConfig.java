package com.servicedesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One shared UTC clock. Services read time from this bean instead of calling
 * Instant.now() directly, so tests can freeze or advance time (essential for SLA tests).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}