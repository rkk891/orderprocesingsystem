package com.rkk.orderprocessing;

import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Verifies that Flyway migration and Hibernate validation start against real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class OrderProcessingApplicationIT {

    @Test
    void contextLoadsAgainstPostgres() {
    }
}
