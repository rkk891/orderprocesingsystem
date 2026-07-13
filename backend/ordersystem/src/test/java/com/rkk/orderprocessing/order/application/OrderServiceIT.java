package com.rkk.orderprocessing.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rkk.orderprocessing.testsupport.PostgresTestConfiguration;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Proves aggregate creation rollback through the real service transaction and PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class OrderServiceIT {

    private static final String TEST_CONSTRAINT = "ck_test_order_items_rollback";

    @Autowired
    private OrderService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failedChildInsertRollsBackTheParentAndEveryItem() {
        dropTestConstraint();
        jdbcTemplate.update("delete from orders");
        jdbcTemplate.execute("""
                alter table order_items
                add constraint ck_test_order_items_rollback
                check (product_id <> 'ROLLBACK-FAIL')
                """);

        try {
            assertThatThrownBy(() -> service.create(new CreateOrderCommand(List.of(
                    new CreateOrderCommand.Item("ROLLBACK-OK", 1),
                    new CreateOrderCommand.Item("ROLLBACK-FAIL", 1)))))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(exception -> {
                        Throwable rootCause = rootCauseOf(exception);
                        assertThat(rootCause).isInstanceOf(SQLException.class);
                        assertThat(((SQLException) rootCause).getSQLState()).isEqualTo("23514");
                    });

            TransactionTemplate verification = new TransactionTemplate(transactionManager);
            verification.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verification.setReadOnly(true);
            List<Long> committedCounts = verification.execute(status -> List.of(
                    jdbcTemplate.queryForObject("select count(*) from orders", Long.class),
                    jdbcTemplate.queryForObject("select count(*) from order_items", Long.class)));

            assertThat(committedCounts).containsExactly(0L, 0L);
        } finally {
            dropTestConstraint();
            jdbcTemplate.update("delete from orders");
        }
    }

    private void dropTestConstraint() {
        jdbcTemplate.execute("alter table order_items drop constraint if exists " + TEST_CONSTRAINT);
    }

    private static Throwable rootCauseOf(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
