package com.rkk.orderprocessing;

import static org.assertj.core.api.Assertions.assertThat;

import com.rkk.orderprocessing.order.job.OrderScheduler;
import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Proves that the opt-in demo profile loads stable recruiter fixtures through Flyway. */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
@Import(PostgresTestConfiguration.class)
class DemoDataIT {

    private static final List<String> DEMO_ORDER_IDS = List.of(
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            "33333333-3333-4333-8333-333333333333",
            "44444444-4444-4444-8444-444444444444",
            "55555555-5555-4555-8555-555555555555");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void demoProfileSeedsOneOrderInEveryLifecycleState() {
        jdbcTemplate.update("""
                update orders
                set status = 'DELIVERED', updated_at = CURRENT_TIMESTAMP
                where id = cast(? as uuid)
                """, DEMO_ORDER_IDS.getFirst());

        // A second migration cycle restores the fixed demo state without duplicating rows.
        flyway.migrate();

        List<String> statuses = jdbcTemplate.queryForList("""
                select status
                from orders
                where id in (
                    cast(? as uuid), cast(? as uuid), cast(? as uuid),
                    cast(? as uuid), cast(? as uuid)
                )
                order by status
                """, String.class, DEMO_ORDER_IDS.toArray());

        Integer itemCount = jdbcTemplate.queryForObject("""
                select count(*)
                from order_items
                where order_id in (
                    cast(? as uuid), cast(? as uuid), cast(? as uuid),
                    cast(? as uuid), cast(? as uuid)
                )
                """, Integer.class, DEMO_ORDER_IDS.toArray());

        assertThat(statuses).containsExactly(
                "CANCELLED", "DELIVERED", "PENDING", "PROCESSING", "SHIPPED");
        assertThat(itemCount).isEqualTo(6);
        assertThat(applicationContext.getBeansOfType(OrderScheduler.class)).isEmpty();
    }
}
