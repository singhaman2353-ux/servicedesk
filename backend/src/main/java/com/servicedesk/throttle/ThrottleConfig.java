package com.servicedesk.throttle;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ThrottleProperties.class)
public class ThrottleConfig {
}