package com.servicedesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ServicedeskApplicationTests {

    // Loading the context runs Flyway on servicedesk_test and then Hibernate validates
    // every entity against that migrated schema.
    @Test
    void contextLoads() {
    }
}