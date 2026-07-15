package com.rkk.orderprocessing;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Verifies that Flyway migration and Hibernate validation start against real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class OrderProcessingApplicationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAgainstPostgres() {
        Integer demoOrderCount = jdbcTemplate.queryForObject("""
                select count(*)
                from orders
                where id = cast('11111111-1111-4111-8111-111111111111' as uuid)
                """, Integer.class);

        assertThat(demoOrderCount).isZero();
    }
}
